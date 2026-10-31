package eu.colega.tacho.core.analysis

import eu.colega.tacho.core.tachograph.Activity
import eu.colega.tacho.core.tachograph.ActivityChange
import eu.colega.tacho.core.tachograph.ActivityDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Verifies the daily summaries and the EU 561/2006 rule checks against
 * hand-built activity days.
 */
class ActivityAnalysisTest {

    // ---- builders ---------------------------------------------------------

    /** Build a change word: slot 0, single, card inserted, activity, minute of day. */
    private fun change(minute: Int, activity: Activity): ActivityChange =
        ActivityChange(minute, slot = 0, crew = false, cardInserted = true, activity = activity)

    private fun day(seconds: Long, vararg changes: ActivityChange): ActivityDay =
        ActivityDay(index = 0, date = eu.colega.tacho.core.card.TimeReal(seconds), dailyPresenceCounter = 1, dayDistance = 0, activityChanges = changes.toList())

    private fun epochSeconds(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()

    private fun ActivityDay.hoursOf(activity: Activity): Int {
        val summary = ActivityAnalysis.summarize(this)
        return when (activity) {
            Activity.DRIVING -> summary.drivingMinutes
            Activity.WORK -> summary.workMinutes
            Activity.BREAK_OR_REST -> summary.breakMinutes + summary.restMinutes
            Activity.AVAILABILITY -> summary.availabilityMinutes
            Activity.UNKNOWN -> 0
        } / 60
    }

    // ---- summarise --------------------------------------------------------

    @Test
    fun `splits a day into driving work and rest spans`() {
        // 6 h driving from 06:00, 45 min break at 12:00, 2 h work, rest from 14:30.
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(360, Activity.DRIVING),
            change(720, Activity.BREAK_OR_REST),
            change(765, Activity.WORK),
            change(885, Activity.BREAK_OR_REST),
        )
        assertEquals(6, d.hoursOf(Activity.DRIVING))
        assertEquals(2, d.hoursOf(Activity.WORK))
        // 00:00-06:00 (360), the 45 min at 12:00, and 14:45-24:00 (555) are rest.
        val summary = ActivityAnalysis.summarize(d)
        assertEquals(960, summary.restMinutes)
        assertEquals(0, summary.breakMinutes)
    }

    @Test
    fun `spans shorter than 45 minutes count as break not rest`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(600, Activity.BREAK_OR_REST), // 30 min break
            change(630, Activity.BREAK_OR_REST), // 810 min rest
        )
        val summary = ActivityAnalysis.summarize(d)
        assertEquals(30, summary.breakMinutes)
        assertEquals(1410, summary.restMinutes)
    }

    // ---- daily driving limit ---------------------------------------------

    @Test
    fun `flags days with more than nine hours of driving`() {
        val monday = LocalDate.of(2025, 1, 20)
        val d = day(
            epochSeconds(monday),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 5 h
            change(600, Activity.DRIVING), // 4 h 30 more -> 9 h 30 total
            change(870, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(listOf(d))
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING }
        assertEquals(monday, violation.date)
        assertEquals(30, violation.excessMinutes)
    }

    @Test
    fun `does not flag a nine hour day`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING),
            change(840, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(listOf(d))
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING })
    }

    // ---- weekly and two-week limits --------------------------------------

    @Test
    fun `flags a week over 56 hours of driving`() {
        val days = (0..5).map { offset ->
            val date = LocalDate.of(2025, 1, 20).plusDays(offset.toLong()) // Mon..Sat
            day(
                epochSeconds(date),
                change(0, Activity.BREAK_OR_REST),
                change(300, Activity.DRIVING), // 9 h
                change(840, Activity.BREAK_OR_REST),
            )
        } + listOf(
            // Sunday: 3 h -> week total 57 h
            day(
                epochSeconds(LocalDate.of(2025, 1, 26)),
                change(0, Activity.BREAK_OR_REST),
                change(300, Activity.DRIVING),
                change(480, Activity.BREAK_OR_REST),
            ),
        )
        val analysis = ActivityAnalysis.analyze(days)
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.WEEKLY_DRIVING }
        assertEquals(LocalDate.of(2025, 1, 20), violation.date) // ISO week start
        assertEquals(60, violation.excessMinutes)
    }

    @Test
    fun `flags two consecutive weeks over 90 hours`() {
        fun weekOf(monday: LocalDate, hoursPerDay: Int): List<ActivityDay> =
            (0..5).map { offset ->
                day(
                    epochSeconds(monday.plusDays(offset.toLong())),
                    change(0, Activity.BREAK_OR_REST),
                    change(300, Activity.DRIVING),
                    change(300 + hoursPerDay * 60, Activity.BREAK_OR_REST),
                )
            }

        val week1 = weekOf(LocalDate.of(2025, 1, 20), hoursPerDay = 8) // 48 h
        val week2 = weekOf(LocalDate.of(2025, 1, 27), hoursPerDay = 8) // 48 h -> 96 h total
        val analysis = ActivityAnalysis.analyze(week1 + week2)
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.TWO_WEEK_DRIVING }
        assertEquals(LocalDate.of(2025, 1, 27), violation.date)
        assertEquals(6 * 60, violation.excessMinutes)
    }

    // ---- break rule -------------------------------------------------------

    @Test
    fun `flags more than 4h30 continuous driving without a qualifying break`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 5 h continuous
            change(600, Activity.BREAK_OR_REST),
        )
        val violation = ActivityAnalysis.checkBreaks(d.activityChanges, LocalDate.of(2025, 1, 20))
        assertEquals(ActivityAnalysis.Violation.Type.BREAK, violation!!.type)
        assertEquals(30, violation.excessMinutes)
    }

    @Test
    fun `a full 45 minute break resets the driving accumulator`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 3 h 30
            change(510, Activity.BREAK_OR_REST), // 45 min break
            change(555, Activity.DRIVING), // 1 h 15
            change(630, Activity.BREAK_OR_REST),
        )
        assertEquals(null, ActivityAnalysis.checkBreaks(d.activityChanges, LocalDate.of(2025, 1, 20)))
    }

    @Test
    fun `a 15 plus 30 split break keeps a long driving day compliant`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 4 h
            change(540, Activity.BREAK_OR_REST), // 15 min (first split part)
            change(555, Activity.DRIVING), // 4 h
            change(795, Activity.BREAK_OR_REST), // 30 min (second part)
            change(825, Activity.DRIVING), // 2 h
            change(945, Activity.BREAK_OR_REST),
        )
        assertEquals(null, ActivityAnalysis.checkBreaks(d.activityChanges, LocalDate.of(2025, 1, 20)))
    }

    @Test
    fun `a 15 minute part alone does not license endless driving`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 4 h
            change(540, Activity.BREAK_OR_REST), // 15 min (first split part)
            change(555, Activity.DRIVING), // 5 h 15 without the 30 min part
            change(870, Activity.BREAK_OR_REST),
        )
        val violation = ActivityAnalysis.checkBreaks(d.activityChanges, LocalDate.of(2025, 1, 20))
        assertEquals(ActivityAnalysis.Violation.Type.BREAK, violation!!.type)
    }
}
