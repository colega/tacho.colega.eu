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
    const val WEEKLY_REST_REGULAR_MIN = 45L * 60
    const val WEEKLY_REST_REDUCED_MIN = 24L * 60
    const val WEEKLY_REST_START_WINDOW_MIN = 6L * 24 * 60
    // Art. 8(2): within the 24 h after the previous rest's end the new rest
    // must have at least 9 h, so the next rest must start within 15 h.
    const val MAX_DUTY_BETWEEN_RESTS_MIN = 24L * 60 - REDUCED_DAILY_REST_MIN
    const val WEEKS_TO_COMPENSATE = 3

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
            WEEKLY_REST,
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
                    span >= SPLIT_BREAK_SECOND_MIN && splitFirstTaken -> {
                        // Split completed (15 then 30): only the second part
                        // restarts the 4h30 window; the driving done before
                        // the first part still counts.
                        drivingSinceBreak = 0
                        splitFirstTaken = false
                    }
                    span >= SPLIT_BREAK_FIRST_MIN && !splitFirstTaken -> {
                        // First split part taken: the split has begun, but
                        // the 4h30 window is NOT renewed by it.
                        splitFirstTaken = true
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
     * Daily and weekly rest rules (Regulation 561/2006, Art. 8), on an
     * absolute minute timeline so rest runs merge across midnight.
     *
     * Rest runs are maximal contiguous stretches of rest or of time
     * without card records. A gap without records counts as rest: a
     * driver who removes the card for that long was not working, and
     * treating the gap as work would raise false violations.
     *
     * Implemented rules:
     * - Art. 8(2): within the 24 h after the end of a qualifying rest
     *   (>= 9 h) the next qualifying rest must start, with at least 9 h
     *   of it inside the window - so it must start within 15 h.
     * - Art. 8(4): at most three reduced daily rests (9-11 h) between
     *   two weekly rests.
     * - Art. 8(6): in any two consecutive weeks at least two weekly
     *   rests, of which at most one reduced (>= 24 h); a weekly rest
     *   must start no later than six 24-hour periods after the end of
     *   the previous one. Two consecutive reduced weekly rests are only
     *   legal for international drivers when the four-week derogation
     *   holds (at least four weekly rests, two regular, in any four
     *   consecutive weeks); the check applies that condition.
     * - Art. 8(6b)/(7): any weekly rest reduction is compensated by an
     *   equivalent rest taken en bloc before the end of the third week
     *   following the week in question, attached to another rest of at
     *   least nine hours (so the compensating run must reach
     *   reduction + 9 h).
     *
     * Not detectable from a single card: multi-manning (Art. 8(5)) and
     * rests taken in a vehicle (Art. 8(8)).
     */
    fun checkRests(days: List<ActivityDay>): List<Violation> {
        if (days.isEmpty()) return emptyList()
        val merged = mergeTimeline(days)
        if (merged.isEmpty()) return emptyList()

        // Contiguous rest runs: rest or unknown spans merge into one run.
        val runs = mutableListOf<Span>()
        for (span in merged) {
            if (span.activity != Activity.BREAK_OR_REST && span.activity != Activity.UNKNOWN) continue
            val last = runs.lastOrNull()
            if (last != null && span.startMinute <= last.endMinute) {
                runs[runs.size - 1] = last.copy(endMinute = maxOf(last.endMinute, span.endMinute))
            } else {
                runs += span
            }
        }

        val violations = mutableListOf<Violation>()
        val dataEnd = merged.last().endMinute

        // Qualifying daily rests: runs of at least 9 h.
        val dailyRests = runs.filter { it.endMinute - it.startMinute >= REDUCED_DAILY_REST_MIN }
        for (i in 0 until dailyRests.size - 1) {
            val gap = dailyRests[i + 1].startMinute - dailyRests[i].endMinute
            if (gap > MAX_DUTY_BETWEEN_RESTS_MIN) {
                violations += Violation(
                    Violation.Type.DAILY_REST,
                    dateOf(dailyRests[i].endMinute + MAX_DUTY_BETWEEN_RESTS_MIN),
                    "No daily rest started within 15 hours of the previous rest",
                    (gap - MAX_DUTY_BETWEEN_RESTS_MIN).toInt(),
                )
            }
        }

        // Weekly rests: runs of at least 24 h.
        val weeklyRests = runs.filter { it.endMinute - it.startMinute >= WEEKLY_REST_REDUCED_MIN }
        for (i in 0 until weeklyRests.size - 1) {
            val gap = weeklyRests[i + 1].startMinute - weeklyRests[i].endMinute
            if (gap > WEEKLY_REST_START_WINDOW_MIN) {
                violations += Violation(
                    Violation.Type.WEEKLY_REST,
                    dateOf(weeklyRests[i].endMinute + WEEKLY_REST_START_WINDOW_MIN),
                    "Next weekly rest started more than six days after the previous one",
                    (gap - WEEKLY_REST_START_WINDOW_MIN).toInt(),
                )
            }
        }

        // Art. 8(4): at most three reduced daily rests (9-11 h) between
        // two weekly rests; any weekly rest (>= 24 h) resets the count.
        var reducedSinceWeekly = 0
        for (run in runs) {
            val length = run.endMinute - run.startMinute
            when {
                length >= WEEKLY_REST_REDUCED_MIN -> reducedSinceWeekly = 0
                length >= REDUCED_DAILY_REST_MIN && length < DAILY_REST_LIMIT_MIN -> {
                    reducedSinceWeekly++
                    if (reducedSinceWeekly > MAX_REDUCED_RESTS_BETWEEN_WEEKLY) {
                        violations += Violation(
                            Violation.Type.DAILY_REST,
                            dateOf(run.startMinute),
                            "More than three reduced daily rests between weekly rests",
                            0,
                        )
                    }
                }
            }
        }

        // Fortnight rule over consecutive ISO week pairs fully covered by
        // the data (weeks at the edge cannot be judged).
        val firstDataWeek = isoWeekStart(dateOf(merged.first().startMinute))
        val lastDataWeek = isoWeekStart(dateOf(dataEnd - 1))
        var weekStart = firstDataWeek
        while (weekStart < lastDataWeek) {
            val pairStart = weekStart
            weekStart = weekStart.plusDays(7) // advance first: the guard
            // below must not skip the increment.
            val nextWeekStart = pairStart.plusDays(7)
            val fortnightStart = pairStart.toEpochDay() * 1440
            val fortnightEnd = nextWeekStart.plusDays(7).toEpochDay() * 1440
            if (fortnightStart < firstDataWeek.toEpochDay() * 1440 || fortnightEnd > dataEnd) continue
            // A fortnight with no duty is already accounted for as rest.
            val hasDuty = merged.any { span ->
                span.startMinute < fortnightEnd &&
                    span.endMinute > fortnightStart &&
                    (span.activity == Activity.DRIVING ||
                        span.activity == Activity.WORK ||
                        span.activity == Activity.AVAILABILITY)
            }
            val overlapping = weeklyRests.filter {
                it.startMinute < fortnightEnd && it.endMinute > fortnightStart
            }
            // One continuous rest can be split into two regular weekly rests.
            val longRestCoversTwo = overlapping.size == 1 &&
                minOf(overlapping[0].endMinute, fortnightEnd) -
                    maxOf(overlapping[0].startMinute, fortnightStart) >=
                    2 * WEEKLY_REST_REGULAR_MIN
            if (hasDuty && (overlapping.size >= 2 || longRestCoversTwo)) {
                val regular = overlapping.count { it.endMinute - it.startMinute >= WEEKLY_REST_REGULAR_MIN }
                if (regular == 0) {
                    // Two consecutive reduced weekly rests: legal only under
                    // the international four-week derogation.
                    val windowStart = weekStart.minusDays(7).toEpochDay() * 1440
                    val inWindow = weeklyRests.filter {
                        it.startMinute < fortnightEnd + 7L * 1440 && it.endMinute > windowStart
                    }
                    val windowRegular = inWindow.count { it.endMinute - it.startMinute >= WEEKLY_REST_REGULAR_MIN }
                    if (inWindow.size < 4 || windowRegular < 2) {
                        violations += Violation(
                            Violation.Type.WEEKLY_REST,
                            dateOf(fortnightEnd),
                            "Two reduced weekly rests without the regular rest the derogation requires",
                            0,
                        )
                    }
                }
            } else if (hasDuty) {
                violations += Violation(
                    Violation.Type.WEEKLY_REST,
                    dateOf(fortnightEnd),
                    "Fewer than two weekly rests in two consecutive weeks",
                    0,
                )
            }
            weekStart = weekStart.plusDays(7)
        }

        // Compensation obligations for reduced weekly rests.
        data class Obligation(val owedMinutes: Long, val deadlineMinute: Long)
        val obligations = mutableListOf<Obligation>()
        for (run in runs) {
            val length = run.endMinute - run.startMinute
            // A compensation rest must reach the reduction plus an attached
            // nine-hour rest (Art. 8(7)).
            obligations.removeAll { it.owedMinutes <= length - REDUCED_DAILY_REST_MIN }
            if (length in WEEKLY_REST_REDUCED_MIN until WEEKLY_REST_REGULAR_MIN) {
                val deadline = (isoWeekStart(dateOf(run.startMinute)).plusDays(7L * (WEEKS_TO_COMPENSATE + 1)).toEpochDay()) * 1440
                obligations += Obligation(WEEKLY_REST_REGULAR_MIN - length, deadline)
            }
        }
        for (obligation in obligations) {
            if (obligation.deadlineMinute <= dataEnd) {
                violations += Violation(
                    Violation.Type.WEEKLY_REST,
                    dateOf(obligation.deadlineMinute),
                    "Reduced weekly rest not compensated within three weeks",
                    obligation.owedMinutes.toInt(),
                )
            }
        }
        return violations
    }

    /** Collapse recorded days into adjacent, non-overlapping spans. */
    private fun mergeTimeline(days: List<ActivityDay>): List<Span> {
        val spans = mutableListOf<Span>()
        for (day in days.sortedBy { epochDay(it.date.seconds).toEpochDay() }) {
            val base = epochDay(day.date.seconds).toEpochDay() * 1440
            for (i in day.activityChanges.indices) {
                val change = day.activityChanges[i]
                val nextStart = day.activityChanges.getOrNull(i + 1)?.minutesOfDay ?: 1440
                if (nextStart <= change.minutesOfDay) continue
                spans += Span(base + change.minutesOfDay, base + nextStart, change.activity)
            }
        }
        if (spans.isEmpty()) return spans
        spans.sortBy { it.startMinute }
        val merged = mutableListOf<Span>()
        for (span in spans) {
            val last = merged.lastOrNull()
            if (last != null && span.startMinute <= last.endMinute && last.activity == span.activity) {
                merged[merged.size - 1] = last.copy(endMinute = maxOf(last.endMinute, span.endMinute))
            } else if (last != null && span.startMinute > last.endMinute) {
                merged += Span(last.endMinute, span.startMinute, Activity.UNKNOWN)
                merged += span
            } else {
                val clamped = if (last != null && span.startMinute < last.endMinute) {
                    span.copy(startMinute = last.endMinute)
                } else {
                    span
                }
                if (clamped.endMinute > clamped.startMinute) merged += clamped
            }
        }
        return merged
    }

    private fun isoWeekStart(date: LocalDate): LocalDate = date.with(WeekFields.ISO.dayOfWeek(), 1)

    /** A contiguous activity span on the absolute-minute timeline. */    /** A contiguous activity span on the absolute-minute timeline. */
    private data class Span(val startMinute: Long, val endMinute: Long, val activity: Activity)

    private fun dateOf(absMinute: Long): LocalDate =
        Instant.ofEpochSecond(absMinute * 60).atZone(ZoneOffset.UTC).toLocalDate()

    private fun epochDay(seconds: Long): LocalDate =
        Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC).toLocalDate()
}
