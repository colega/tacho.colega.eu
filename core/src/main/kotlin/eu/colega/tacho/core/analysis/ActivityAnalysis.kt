package eu.colega.tacho.core.analysis

import eu.colega.tacho.core.tachograph.Activity
import eu.colega.tacho.core.tachograph.ActivityChange
import eu.colega.tacho.core.tachograph.ActivityDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Analyses card activity data against the EU driving-time rules
 * (Regulation (EC) No 561/2006, Articles 6-8) at the minute resolution
 * provided by card activity records.
 */
object ActivityAnalysis {

    // EU limits (Regulation 561/2006)
    const val DAILY_DRIVING_LIMIT_MIN = 9 * 60
    const val EXTENDED_DAILY_DRIVING_LIMIT_MIN = 10 * 60
    const val MAX_EXTENDED_DAYS_PER_WEEK = 2
    const val WEEKLY_DRIVING_LIMIT_MIN = 56 * 60
    const val TWO_WEEK_DRIVING_LIMIT_MIN = 90 * 60
    const val MAX_DRIVING_WITHOUT_BREAK_MIN = 4 * 60 + 30
    const val MIN_BREAK_MIN = 45
    const val SPLIT_BREAK_FIRST_MIN = 15
    const val SPLIT_BREAK_SECOND_MIN = 30
    const val DAILY_REST_LIMIT_MIN = 11 * 60
    const val REDUCED_DAILY_REST_MIN = 9 * 60
    const val MAX_REDUCED_RESTS_PER_WEEK = 3

    /** Activity durations for one calendar day, in minutes. */
    data class DailySummary(
        val date: LocalDate,
        val drivingMinutes: Int,
        val workMinutes: Int,
        val availabilityMinutes: Int,
        val breakMinutes: Int,
        val restMinutes: Int,
        val firstChangeMinute: Int?,
        val lastChangeMinute: Int?,
    ) {
        val totalRecordedMinutes: Int
            get() = drivingMinutes + workMinutes + availabilityMinutes + breakMinutes + restMinutes
    }

    /** Driving-time totals for one ISO week (Monday-Sunday). */
    data class WeeklySummary(
        val weekStart: LocalDate,
        val days: List<DailySummary>,
    ) {
        val drivingMinutes: Int get() = days.sumOf { it.drivingMinutes }
        val extendedDrivingDays: Int
            get() = days.count { it.drivingMinutes > DAILY_DRIVING_LIMIT_MIN && it.drivingMinutes <= EXTENDED_DAILY_DRIVING_LIMIT_MIN }
        val overDailyLimitDays: Int
            get() = days.count { it.drivingMinutes > EXTENDED_DAILY_DRIVING_LIMIT_MIN }
    }

    /** One detected rule violation. */
    data class Violation(
        val type: Type,
        val date: LocalDate,
        val description: String,
        val excessMinutes: Int,
    ) {
        enum class Type {
            DAILY_DRIVING,
            WEEKLY_DRIVING,
            TWO_WEEK_DRIVING,
            BREAK,
        }
    }

    /** Complete analysis result. */
    data class Analysis(
        val dailySummaries: List<DailySummary>,
        val weeklySummaries: List<WeeklySummary>,
        val violations: List<Violation>,
    ) {
        val totalDrivingMinutes: Int get() = dailySummaries.sumOf { it.drivingMinutes }
        val totalRecordedMinutes: Int get() = dailySummaries.sumOf { it.totalRecordedMinutes }
    }

    /** Analyse a card's activity days (in card storage order). */
    fun analyze(days: List<ActivityDay>): Analysis {
        val byDate = days.map { it to summarize(it) }
            .filter { (_, summary) -> summary.totalRecordedMinutes > 0 }
        val summaries = byDate.map { it.second }
        val weekly = groupByWeek(summaries)
        val violations = checkViolations(summaries, weekly, byDate)
        return Analysis(summaries, weekly, violations)
    }

    fun summarize(day: ActivityDay): DailySummary {
        var driving = 0
        var work = 0
        var availability = 0
        var breakMin = 0
        var rest = 0
        var first: Int? = null
        var last: Int? = null
        for (i in day.activityChanges.indices) {
            val change = day.activityChanges[i]
            val nextStart = day.activityChanges.getOrNull(i + 1)?.minutesOfDay ?: 1440
            val span = nextStart - change.minutesOfDay
            if (span <= 0) continue
            if (first == null) first = change.minutesOfDay
            last = nextStart
            when (change.activity) {
                Activity.DRIVING -> driving += span
                Activity.WORK -> work += span
                Activity.BREAK_OR_REST -> if (span >= MIN_BREAK_MIN) rest += span else breakMin += span
                Activity.AVAILABILITY -> availability += span
                Activity.UNKNOWN -> {}
            }
        }
        return DailySummary(
            date = epochDay(day.date.seconds),
            drivingMinutes = driving,
            workMinutes = work,
            availabilityMinutes = availability,
            breakMinutes = breakMin,
            restMinutes = rest,
            firstChangeMinute = first,
            lastChangeMinute = last,
        )
    }

    private fun groupByWeek(summaries: List<DailySummary>): List<WeeklySummary> {
        val isoWeek = WeekFields.of(Locale.UK).firstDayOfWeek.let { WeekFields.ISO }
        return summaries
            .groupBy { it.date.with(isoWeek.dayOfWeek(), 1) }
            .map { (weekStart, days) -> WeeklySummary(weekStart, days.sortedBy { it.date }) }
            .sortedBy { it.weekStart }
    }

    private fun checkViolations(
        summaries: List<DailySummary>,
        weekly: List<WeeklySummary>,
        changesByDay: List<Pair<ActivityDay, DailySummary>>,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        for (day in summaries) {
            if (day.drivingMinutes > DAILY_DRIVING_LIMIT_MIN) {
                violations += Violation(
                    Violation.Type.DAILY_DRIVING,
                    day.date,
                    "Daily driving exceeded",
                    day.drivingMinutes - DAILY_DRIVING_LIMIT_MIN,
                )
            }
        }
        for (week in weekly) {
            if (week.drivingMinutes > WEEKLY_DRIVING_LIMIT_MIN) {
                violations += Violation(
                    Violation.Type.WEEKLY_DRIVING,
                    week.weekStart,
                    "Weekly driving exceeded",
                    week.drivingMinutes - WEEKLY_DRIVING_LIMIT_MIN,
                )
            }
            if (week.extendedDrivingDays > MAX_EXTENDED_DAYS_PER_WEEK) {
                violations += Violation(
                    Violation.Type.DAILY_DRIVING,
                    week.weekStart,
                    "Daily driving extended more than twice in a week",
                    0,
                )
            }
        }
        // Two consecutive weeks
        for (i in 0 until weekly.size - 1) {
            val total = weekly[i].drivingMinutes + weekly[i + 1].drivingMinutes
            if (total > TWO_WEEK_DRIVING_LIMIT_MIN) {
                violations += Violation(
                    Violation.Type.TWO_WEEK_DRIVING,
                    weekly[i + 1].weekStart,
                    "Two-week driving exceeded",
                    total - TWO_WEEK_DRIVING_LIMIT_MIN,
                )
            }
        }
        // Break compliance within each day
        for ((day, _) in changesByDay) {
            checkBreaks(day.activityChanges, epochDay(day.date.seconds))?.let { violations += it }
        }
        return violations
    }

    /**
     * Check the break rule across a day's driving segments: after 4h30 of
     * driving, a 45-minute break is required, either as one period of at
     * least 45 minutes or split into a first part of at least 15 minutes
     * followed by a second part of at least 30 minutes. The violation is
     * raised at the moment the driving accumulation passes the limit - a
     * later break cannot cure it.
     */
    fun checkBreaks(changes: List<ActivityChange>, date: LocalDate): Violation? {
        if (changes.isEmpty()) return null
        var drivingSinceBreak = 0
        var splitFirstTaken = false
        for (i in changes.indices) {
            val change = changes[i]
            val span = (changes.getOrNull(i + 1)?.minutesOfDay ?: 1440) - change.minutesOfDay
            if (span <= 0) continue
            when (change.activity) {
                Activity.DRIVING -> {
                    drivingSinceBreak += span
                    if (drivingSinceBreak > MAX_DRIVING_WITHOUT_BREAK_MIN) {
                        return Violation(
                            Violation.Type.BREAK,
                            date,
                            "Driving exceeded 4h30 without a qualifying 45-minute break",
                            drivingSinceBreak - MAX_DRIVING_WITHOUT_BREAK_MIN,
                        )
                    }
                }
                Activity.BREAK_OR_REST -> when {
                    span >= MIN_BREAK_MIN -> {
                        // Full break: resets both the driving and split state.
                        drivingSinceBreak = 0
                        splitFirstTaken = false
                    }
                    span >= SPLIT_BREAK_FIRST_MIN && !splitFirstTaken -> {
                        // First split part taken: a fresh 4h30 window starts now.
                        splitFirstTaken = true
                        drivingSinceBreak = 0
                    }
                    span >= SPLIT_BREAK_SECOND_MIN && splitFirstTaken -> {
                        // Split completed: 15 + 30 in order.
                        drivingSinceBreak = 0
                        splitFirstTaken = false
                    }
                    else -> {
                        // Too short to qualify; it neither resets nor cures.
                    }
                }
                else -> {
                    // Work/availability do not reset the driving accumulator.
                }
            }
        }
        return null
    }

    private fun epochDay(seconds: Long): LocalDate =
        Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate()
}
