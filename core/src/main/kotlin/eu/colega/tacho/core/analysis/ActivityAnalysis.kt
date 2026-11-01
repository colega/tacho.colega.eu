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
    const val MAX_REDUCED_RESTS_BETWEEN_WEEKLY = 3
    const val REST_WINDOW_MIN = 24L * 60
    const val WEEKLY_REST_MIN = 45L * 60

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

        /** Days using the twice-a-week extension: every day over 9 h counts. */
        val extendedDrivingDays: Int
            get() = days.count { it.drivingMinutes > DAILY_DRIVING_LIMIT_MIN }
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
            DAILY_REST,
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
        val violations = checkViolations(summaries, weekly, byDate) + checkRests(days)
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
        // Daily driving: 9 h may be extended to 10 h at most twice per fixed
        // week (Regulation 561/2006, Art. 6(1)). A day over 10 h is always a
        // violation; a day between 9 h and 10 h only becomes one when the
        // week has more than two such days.
        for (day in summaries) {
            if (day.drivingMinutes > EXTENDED_DAILY_DRIVING_LIMIT_MIN) {
                violations += Violation(
                    Violation.Type.DAILY_DRIVING,
                    day.date,
                    "Daily driving exceeded",
                    day.drivingMinutes - EXTENDED_DAILY_DRIVING_LIMIT_MIN,
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
                // The third day over 9 h in this week is the violation: it
                // could not be licensed by the twice-a-week extension.
                val third = week.days.sortedBy { it.date }
                    .filter { it.drivingMinutes > DAILY_DRIVING_LIMIT_MIN }[MAX_EXTENDED_DAYS_PER_WEEK]
                violations += Violation(
                    Violation.Type.DAILY_DRIVING,
                    third.date,
                    "Daily driving extended more than twice in a week",
                    minOf(third.drivingMinutes, EXTENDED_DAILY_DRIVING_LIMIT_MIN) - DAILY_DRIVING_LIMIT_MIN,
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

    /**
     * Daily rest rules (Regulation 561/2006, Art. 8): a daily rest of 11 h,
     * reducible to 9 h at most three times between two weekly rests.
     *
     * Card activity covers whole days, so rest runs are merged across
     * midnight on an absolute minute timeline. Days without card records
     * count as rest when the gap is at least a daily rest long: a driver
     * who removes the card for that long was not working, and treating the
     * gap as work would raise false violations. The 24 h window is checked
     * on rest starts, the reading that avoids false positives on drivers
     * who start their next rest within a day of finishing the previous one.
     */
    fun checkRests(days: List<ActivityDay>): List<Violation> {
        if (days.isEmpty()) return emptyList()
        val spans = mutableListOf<Span>()
        for (day in days.sortedBy { epochDay(it.date.seconds).toEpochDay() }) {
            val date = epochDay(day.date.seconds)
            val base = date.toEpochDay() * 1440
            for (i in day.activityChanges.indices) {
                val change = day.activityChanges[i]
                val nextStart = day.activityChanges.getOrNull(i + 1)?.minutesOfDay ?: 1440
                if (nextStart <= change.minutesOfDay) continue
                spans += Span(base + change.minutesOfDay, base + nextStart, change.activity)
            }
        }
        if (spans.isEmpty()) return emptyList()
        spans.sortBy { it.startMinute }
        // Merge overlapping or adjacent spans of the same activity; the gaps
        // between recorded days become unknown spans.
        val merged = mutableListOf<Span>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && span.startMinute <= last.endMinute && last.activity == span.activity) {
                // Touching or overlapping spans of one activity are one run;
                // this is what merges a rest that crosses midnight.
                merged[merged.size - 1] = last.copy(endMinute = maxOf(last.endMinute, span.endMinute))
            } else if (last != null && span.startMinute > last.endMinute) {
                merged += Span(last.endMinute, span.startMinute, Activity.UNKNOWN)
                merged += span
            } else {
                // Overlapping spans of a different activity start where the
                // previous one ends.
                val clamped = if (last != null && span.startMinute < last.endMinute) {
                    span.copy(startMinute = last.endMinute)
                } else {
                    span
                }
                if (clamped.endMinute > clamped.startMinute) merged += clamped
            }
        }
        val violations = mutableListOf<Violation>()
        // Qualifying daily rests: rest (or unknown gaps) of at least 9 h.
        val dailyRests = merged.filter { span ->
            (span.activity == Activity.BREAK_OR_REST || span.activity == Activity.UNKNOWN) &&
                span.endMinute - span.startMinute >= REDUCED_DAILY_REST_MIN
        }
        // No work stretch without a qualifying rest start within 24 h of the
        // previous rest's end.
        for (i in 0 until dailyRests.size - 1) {
            val gap = dailyRests[i + 1].startMinute - dailyRests[i].endMinute
            if (gap > REST_WINDOW_MIN) {
                violations += Violation(
                    Violation.Type.DAILY_REST,
                    dateOf(dailyRests[i + 1].startMinute),
                    "No daily rest within 24 hours",
                    (gap - REST_WINDOW_MIN).toInt(),
                )
            }
        }
        // Weekly rests (>= 45 h) reset the reduced-rest allowance; a fourth
        // reduced rest (9..11 h) between two weekly rests is a violation.
        val weeklyRests = merged.filter { span ->
            (span.activity == Activity.BREAK_OR_REST || span.activity == Activity.UNKNOWN) &&
                span.endMinute - span.startMinute >= WEEKLY_REST_MIN
        }
        var reducedSinceWeekly = 0
        var weeklyIndex = 0
        for (rest in dailyRests) {
            val length = rest.endMinute - rest.startMinute
            while (weeklyIndex < weeklyRests.size && weeklyRests[weeklyIndex].endMinute <= rest.startMinute) {
                weeklyIndex++
            }
            val lastWeeklyEnd = weeklyRests.getOrNull(weeklyIndex - 1)?.endMinute ?: Long.MIN_VALUE
            if (rest.startMinute >= lastWeeklyEnd) {
                if (length < DAILY_REST_LIMIT_MIN) {
                    reducedSinceWeekly++
                    if (reducedSinceWeekly > MAX_REDUCED_RESTS_BETWEEN_WEEKLY) {
                        violations += Violation(
                            Violation.Type.DAILY_REST,
                            dateOf(rest.startMinute),
                            "More than three reduced daily rests between weekly rests",
                            0,
                        )
                    }
                }
            } else {
                reducedSinceWeekly = 0
            }
        }
        return violations
    }

    /** A contiguous activity span on the absolute-minute timeline. */
    private data class Span(val startMinute: Long, val endMinute: Long, val activity: Activity)

    private fun dateOf(absMinute: Long): LocalDate =
        Instant.ofEpochSecond(absMinute * 60).atZone(ZoneOffset.UTC).toLocalDate()

    private fun epochDay(seconds: Long): LocalDate =
        Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate()
}
