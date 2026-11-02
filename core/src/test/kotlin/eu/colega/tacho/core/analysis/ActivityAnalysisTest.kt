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
    fun `a nine and a half hour day uses the twice a week extension`() {
        // Regulation 561/2006 Art. 6(1): 9 h may be extended to 10 h twice
        // per fixed week, so a single 9 h 30 day is legal.
        val monday = LocalDate.of(2025, 1, 20)
        val d = day(
            epochSeconds(monday),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 5 h
            change(600, Activity.DRIVING), // 4 h 30 more -> 9 h 30 total
            change(870, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(listOf(d))
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING })
    }

    @Test
    fun `a day over ten hours of driving is always a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        val d = day(
            epochSeconds(monday),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 10 h 30 of driving
            change(930, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(listOf(d))
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING }
        assertEquals(monday, violation.date)
        assertEquals(30, violation.excessMinutes)
    }

    @Test
    fun `a third day over nine hours in one week is a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        fun extendedDay(date: LocalDate): ActivityDay = day(
            epochSeconds(date),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING),
            change(870, Activity.BREAK_OR_REST), // 9 h 30
        )
        val analysis = ActivityAnalysis.analyze(
            listOf(
                extendedDay(monday),
                extendedDay(monday.plusDays(1)),
                extendedDay(monday.plusDays(2)),
            )
        )
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING }
        assertEquals(monday.plusDays(2), violation.date)
        assertEquals(30, violation.excessMinutes)
    }

    @Test
    fun `two extended days in one week stay legal`() {
        val monday = LocalDate.of(2025, 1, 20)
        fun extendedDay(date: LocalDate, drivingEnd: Int): ActivityDay = day(
            epochSeconds(date),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING),
            change(drivingEnd, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(
            listOf(
                extendedDay(monday, 870), // 9 h 30
                extendedDay(monday.plusDays(1), 900), // 10 h
            )
        )
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING })
    }

    @Test
    fun `extensions do not carry across weeks`() {
        val monday = LocalDate.of(2025, 1, 20)
        fun extendedDay(date: LocalDate): ActivityDay = day(
            epochSeconds(date),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING),
            change(870, Activity.BREAK_OR_REST), // 9 h 30
        )
        val analysis = ActivityAnalysis.analyze(
            listOf(
                extendedDay(monday),
                extendedDay(monday.plusDays(1)),
                extendedDay(monday.plusDays(7)),
            )
        )
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_DRIVING })
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
        // The split must fit inside one 4h30 window: the driving between
        // the two parts keeps counting towards the window.
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 2 h
            change(420, Activity.BREAK_OR_REST), // 15 min (first split part)
            change(435, Activity.DRIVING), // 2 h 15: window reaches 4 h 15
            change(555, Activity.BREAK_OR_REST), // 30 min (second part)
            change(585, Activity.DRIVING), // 4 h after the reset
            change(825, Activity.BREAK_OR_REST),
            change(945, Activity.DRIVING), // 2 h
            change(1065, Activity.BREAK_OR_REST),
        )
        assertEquals(null, ActivityAnalysis.checkBreaks(d.activityChanges, LocalDate.of(2025, 1, 20)))
    }

    @Test
    fun `two thirty minute parts work as a split break`() {
        val d = day(
            epochSeconds(LocalDate.of(2025, 1, 20)),
            change(0, Activity.BREAK_OR_REST),
            change(300, Activity.DRIVING), // 2 h
            change(420, Activity.BREAK_OR_REST), // 30 min (first part, >= 15)
            change(450, Activity.DRIVING), // 2 h: window reaches 4 h
            change(570, Activity.BREAK_OR_REST), // 30 min (second part)
            change(600, Activity.DRIVING), // 4 h after the reset
            change(840, Activity.BREAK_OR_REST),
            change(960, Activity.DRIVING), // 2 h
            change(1080, Activity.BREAK_OR_REST),
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

    // ---- daily rest -------------------------------------------------------

    @Test
    fun `a long rest spanning midnight counts once`() {
        // Driving 06:00-22:00 on Monday, then rest to 09:00 Tuesday: the rest
        // is 2 h on Monday plus 9 h on Tuesday = 11 h in total.
        val monday = LocalDate.of(2025, 1, 20)
        val days = listOf(
            day(
                epochSeconds(monday),
                change(0, Activity.UNKNOWN),
                change(360, Activity.DRIVING),
                change(1320, Activity.BREAK_OR_REST),
            ),
            day(
                epochSeconds(monday.plusDays(1)),
                change(540, Activity.WORK),
                change(600, Activity.BREAK_OR_REST),
            ),
        )
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_REST })
    }

    @Test
    fun `a duty stretch over fifteen hours without a rest start is a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = listOf(
            day(
                epochSeconds(monday),
                change(0, Activity.BREAK_OR_REST),
                change(600, Activity.DRIVING), // 10 h driving
                change(1200, Activity.WORK), // 4 h work
                change(1440 - 120, Activity.BREAK_OR_REST), // 2 h rest
            ),
            day(
                epochSeconds(monday.plusDays(1)),
                change(0, Activity.WORK), // 24 h work from Monday 22:00
                change(1440, Activity.BREAK_OR_REST),
            ),
            day(
                epochSeconds(monday.plusDays(2)),
                change(0, Activity.BREAK_OR_REST), // rest from Tuesday 24:00
            ),
        )
        val analysis = ActivityAnalysis.analyze(days)
        val violation = analysis.violations.single { it.type == ActivityAnalysis.Violation.Type.DAILY_REST }
        // The next rest had to start by Monday 10:00 + 15 h.
        assertEquals(monday.plusDays(1), violation.date)
        assertEquals(1380, violation.excessMinutes)
    }

    @Test
    fun `a fifteen hour duty stretch stays legal`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = listOf(
            day(
                epochSeconds(monday),
                change(0, Activity.BREAK_OR_REST),
                change(600, Activity.DRIVING), // 10 h driving
                change(1200, Activity.WORK), // 4 h work
            ),
            day(
                epochSeconds(monday.plusDays(1)),
                change(0, Activity.WORK), // 1 h work: duty reaches exactly 15 h
                change(60, Activity.BREAK_OR_REST),
            ),
        )
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_REST })
    }

    @Test
    fun `a weekly rest must start within six days of the previous one`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = mutableListOf<ActivityDay>()
        days += day(epochSeconds(monday), change(0, Activity.BREAK_OR_REST)) // 24 h reduced weekly rest
        for (offset in 1..7) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.DRIVING),
                change(600, Activity.BREAK_OR_REST), // 14 h rest nights
            )
        }
        days += day(epochSeconds(monday.plusDays(8)), change(0, Activity.BREAK_OR_REST))
        val analysis = ActivityAnalysis.analyze(days)
        val violation = analysis.violations.single {
            it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                it.description == "Next weekly rest started more than six days after the previous one"
        }
        assertEquals(monday.plusDays(7), violation.date)
    }

    @Test
    fun `a fortnight with fewer than two weekly rests is a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = (0 until 14).map { offset ->
            day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.DRIVING),
                change(600, Activity.BREAK_OR_REST), // 14 h rest nights, never a weekly rest
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        val violation = analysis.violations.single {
            it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                it.description == "Fewer than two weekly rests in two consecutive weeks"
        }
        assertEquals(monday.plusDays(14), violation.date)
    }

    @Test
    fun `a continuous month off does not create fortnightly rest warnings`() {
        val monday = LocalDate.of(2026, 4, 20)
        val days = (0 until 28).map { offset ->
            day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.BREAK_OR_REST),
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST })
    }

    @Test
    fun `fortnightly rest checks still apply to work without driving`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = (0 until 14).map { offset ->
            day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.WORK),
                change(600, Activity.BREAK_OR_REST), // 14 h rest each day
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(
            analysis.violations.any {
                it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                    it.description == "Fewer than two weekly rests in two consecutive weeks"
            }
        )
    }

    @Test
    fun `a 90 hour rest after duty satisfies two weekly rests`() {
        val monday = LocalDate.of(2026, 3, 23)
        val days = (0 until 14).map { offset ->
            val date = monday.plusDays(offset.toLong())
            when (offset) {
                in 0..2 -> day(
                    epochSeconds(date),
                    change(0, Activity.DRIVING),
                    change(240, Activity.BREAK_OR_REST), // 45-minute break
                    change(285, Activity.DRIVING),
                    change(480, Activity.BREAK_OR_REST),
                )
                in 3..5 -> day(epochSeconds(date), change(0, Activity.BREAK_OR_REST))
                6 -> day(
                    epochSeconds(date),
                    change(0, Activity.BREAK_OR_REST),
                    change(120, Activity.WORK),
                    change(600, Activity.BREAK_OR_REST),
                )
                else -> day(
                    epochSeconds(date),
                    change(0, Activity.WORK),
                    change(600, Activity.BREAK_OR_REST),
                )
            }
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST })
    }

    @Test
    fun `an 89 hour rest cannot replace two weekly rests`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = (0 until 14).map { offset ->
            val date = monday.plusDays(offset.toLong())
            when {
                offset in 2..4 -> day(epochSeconds(date), change(0, Activity.BREAK_OR_REST))
                offset == 5 -> day(
                    epochSeconds(date),
                    change(0, Activity.BREAK_OR_REST),
                    change(180, Activity.WORK),
                    change(600, Activity.BREAK_OR_REST),
                )
                else -> day(
                    epochSeconds(date),
                    change(0, Activity.WORK),
                    change(600, Activity.BREAK_OR_REST),
                )
            }
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(
            analysis.violations.any {
                it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                    it.description == "Fewer than two weekly rests in two consecutive weeks"
            }
        )
    }

    @Test
    fun `a reduced weekly rest compensated in time stays legal`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = mutableListOf<ActivityDay>()
        days += day(epochSeconds(monday), change(0, Activity.BREAK_OR_REST)) // 24 h reduced, owes 21 h
        for (offset in 1..5) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.DRIVING),
                change(600, Activity.BREAK_OR_REST),
            )
        }
        // 48 h regular weekly rest: compensates the reduction (21 h + 9 h attached).
        days += day(epochSeconds(monday.plusDays(6)), change(0, Activity.BREAK_OR_REST))
        days += day(epochSeconds(monday.plusDays(7)), change(0, Activity.BREAK_OR_REST))
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(
            analysis.violations.none {
                it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                    it.description == "Reduced weekly rest not compensated within three weeks"
            }
        )
    }

    @Test
    fun `an uncompensated reduced weekly rest after the deadline is a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = mutableListOf<ActivityDay>()
        days += day(epochSeconds(monday), change(0, Activity.BREAK_OR_REST)) // owes 21 h
        // Duty days without any qualifying compensation rest; the deadline
        // (end of the fourth week) is inside the data window.
        for (offset in 1 until 28) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.DRIVING),
                change(600, Activity.BREAK_OR_REST),
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertEquals(
            1,
            analysis.violations.count {
                it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                    it.description == "Reduced weekly rest not compensated within three weeks"
            },
        )
    }

    @Test
    fun `a compensation rest must reach the reduction plus nine attached hours`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = mutableListOf<ActivityDay>()
        days += day(epochSeconds(monday), change(0, Activity.BREAK_OR_REST)) // owes 21 h
        for (offset in 1 until 28) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.DRIVING),
                change(600, Activity.BREAK_OR_REST),
            )
        }
        // An isolated 24 h rest run: 24 - 9 = 15 h of attachable rest, less
        // than the 21 h owed, so it does not settle the obligation.
        days += day(
            epochSeconds(monday.plusDays(10)),
            change(0, Activity.BREAK_OR_REST),
        )
        val analysis = ActivityAnalysis.analyze(days)
        assertEquals(
            1,
            analysis.violations.count {
                it.type == ActivityAnalysis.Violation.Type.WEEKLY_REST &&
                    it.description == "Reduced weekly rest not compensated within three weeks"
            },
        )
    }

    @Test
    fun `a fourth reduced rest between weekly rests is a violation`() {
        val monday = LocalDate.of(2025, 1, 20)
        // Six days, each: 8 h rest, 10 h driving, 4 h work, 2 h rest. The
        // rest runs merge across midnight to 10 h each: a reduced rest.
        // The fourth and fifth exceed the three-times allowance.
        val days = (0 until 6).map { offset ->
            day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.BREAK_OR_REST),
                change(480, Activity.DRIVING),
                change(1080, Activity.WORK),
                change(1320, Activity.BREAK_OR_REST),
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        val violations = analysis.violations.filter { it.type == ActivityAnalysis.Violation.Type.DAILY_REST }
        assertEquals(2, violations.size)
        assertEquals(monday.plusDays(3), violations[0].date)
        assertEquals(monday.plusDays(4), violations[1].date)
    }

    @Test
    fun `a weekly rest resets the reduced rest allowance`() {
        val monday = LocalDate.of(2025, 1, 20)
        val days = mutableListOf<ActivityDay>()
        // Three reduced rests, a 48 h weekly rest, then three more.
        for (offset in 0 until 3) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.BREAK_OR_REST),
                change(600, Activity.DRIVING),
                change(1200, Activity.BREAK_OR_REST),
            )
        }
        days += day(
            epochSeconds(monday.plusDays(3)),
            change(0, Activity.BREAK_OR_REST), // 48 h weekly rest
        )
        days += day(
            epochSeconds(monday.plusDays(4)),
            change(0, Activity.BREAK_OR_REST),
        )
        for (offset in 5 until 8) {
            days += day(
                epochSeconds(monday.plusDays(offset.toLong())),
                change(0, Activity.BREAK_OR_REST),
                change(600, Activity.DRIVING),
                change(1200, Activity.BREAK_OR_REST),
            )
        }
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_REST })
    }

    @Test
    fun `days without card records count as rest`() {
        // Monday driven, then two days off card, then work again: the gap is
        // long enough to be a daily rest.
        val monday = LocalDate.of(2025, 1, 20)
        val days = listOf(
            day(
                epochSeconds(monday),
                change(0, Activity.BREAK_OR_REST),
                change(600, Activity.DRIVING),
                change(1200, Activity.BREAK_OR_REST),
            ),
            day(
                epochSeconds(monday.plusDays(3)),
                change(0, Activity.WORK),
                change(600, Activity.DRIVING),
                change(1320, Activity.BREAK_OR_REST),
            ),
        )
        val analysis = ActivityAnalysis.analyze(days)
        assertTrue(analysis.violations.none { it.type == ActivityAnalysis.Violation.Type.DAILY_REST })
    }
}
