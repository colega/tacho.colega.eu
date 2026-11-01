package eu.colega.tacho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.colega.tacho.app.R
import eu.colega.tacho.core.analysis.ActivityAnalysis
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.colega.tacho.core.tachograph.Activity
import eu.colega.tacho.core.tachograph.ActivityDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Daily activity timeline built from the card's activity change records.
 *  Tapping a day expands its segment-by-segment detail. */
@Composable
fun TimelineScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val analysis = state.analysis
    if (analysis == null || analysis.dailySummaries.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.timeline_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.timeline_empty),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    // Raw activity days by absolute day number, for the expanded detail.
    val daysByDate = remember(state.card) {
        state.card?.activity?.associateBy { activityDayDate(it).toEpochDay() } ?: emptyMap()
    }
    var expandedDays by remember { mutableStateOf(setOf<Long>()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                stringResource(R.string.timeline_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        items(analysis.dailySummaries.asReversed(), key = { it.date.toEpochDay() }) { summary ->
            val key = summary.date.toEpochDay()
            DayRow(
                summary = summary,
                expanded = key in expandedDays,
                onToggle = {
                    expandedDays = if (key in expandedDays) expandedDays - key else expandedDays + key
                },
            )
            if (key in expandedDays) {
                DayDetail(
                    summary = summary,
                    day = daysByDate[key],
                    violations = analysis.violations.filter { it.date == summary.date },
                )
            }
            HorizontalDivider()
        }
    }
}

private fun activityDayDate(day: ActivityDay): LocalDate =
    Instant.ofEpochSecond(day.date.seconds).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun DayRow(
    summary: ActivityAnalysis.DailySummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(summary.date.displayDate(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.timeline_driving_hours, hoursLabel(summary.drivingMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    summary.drivingMinutes > ActivityAnalysis.EXTENDED_DAILY_DRIVING_LIMIT_MIN ->
                        MaterialTheme.colorScheme.error
                    summary.drivingMinutes > ActivityAnalysis.DAILY_DRIVING_LIMIT_MIN ->
                        MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        ActivityBar(summary)
        Text(
            stringResource(
                R.string.timeline_summary,
                summary.workMinutes / 60,
                summary.breakMinutes,
                summary.restMinutes / 60,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Text(
                stringResource(R.string.timeline_tap_to_close),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The expanded detail of one day: every recorded segment and any rule
 *  violations detected on it. */
@Composable
private fun DayDetail(
    summary: ActivityAnalysis.DailySummary,
    day: ActivityDay?,
    violations: List<ActivityAnalysis.Violation>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (day == null) {
            Text(
                stringResource(R.string.timeline_no_records),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        val changes = day.activityChanges
        changes.forEachIndexed { index, change ->
            val end = changes.getOrNull(index + 1)?.minutesOfDay ?: 1440
            val span = end - change.minutesOfDay
            if (span <= 0) return@forEachIndexed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(segmentColor(change.activity, span), CircleShape),
                    )
                    Text(
                        minuteOfDayLabel(change.minutesOfDay) + " - " + minuteOfDayLabel(end),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(segmentLabel(change.activity, span)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    hoursLabel(span),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        violations.forEach { violation ->
            val excess = if (violation.excessMinutes > 0) {
                " (+" + stringResource(R.string.timeline_excess_minutes, violation.excessMinutes) + ")"
            } else {
                ""
            }
            Text(
                stringResource(violationLabelRes(violation.type)) + excess,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Rest spans of 45 minutes or more are rest; shorter ones are breaks. */
private fun segmentLabel(activity: Activity, spanMinutes: Int): Int = when (activity) {
    Activity.DRIVING -> R.string.timeline_activity_driving
    Activity.WORK -> R.string.timeline_activity_work
    Activity.AVAILABILITY -> R.string.timeline_activity_availability
    Activity.BREAK_OR_REST -> if (spanMinutes >= 45) {
        R.string.timeline_activity_rest
    } else {
        R.string.timeline_activity_break
    }
    Activity.UNKNOWN -> R.string.timeline_activity_unknown
}

private fun segmentColor(activity: Activity, spanMinutes: Int): Color = when (activity) {
    Activity.DRIVING -> DrivingColor
    Activity.WORK -> WorkColor
    Activity.AVAILABILITY -> AvailabilityColor
    Activity.BREAK_OR_REST -> if (spanMinutes >= 45) RestColor else BreakColor
    Activity.UNKNOWN -> Color(0xFF9E9E9E)
}

/** One horizontal 24 h bar split into activity segments, proportional to minutes. */
@Composable
private fun ActivityBar(summary: ActivityAnalysis.DailySummary) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        if (summary.drivingMinutes > 0) {
            Box(Modifier.weight(summary.drivingMinutes.toFloat()).height(12.dp).background(DrivingColor))
        }
        if (summary.workMinutes > 0) {
            Box(Modifier.weight(summary.workMinutes.toFloat()).height(12.dp).background(WorkColor))
        }
        if (summary.availabilityMinutes > 0) {
            Box(Modifier.weight(summary.availabilityMinutes.toFloat()).height(12.dp).background(AvailabilityColor))
        }
        if (summary.breakMinutes > 0) {
            Box(Modifier.weight(summary.breakMinutes.toFloat()).height(12.dp).background(BreakColor))
        }
        if (summary.restMinutes > 0) {
            Box(Modifier.weight(summary.restMinutes.toFloat()).height(12.dp).background(RestColor))
        }
    }
}

private val DrivingColor = Color(0xFF2E7D32)
private val WorkColor = Color(0xFFEFB000)
private val AvailabilityColor = Color(0xFF0288D1)
private val BreakColor = Color(0xFF7E57C2)
private val RestColor = Color(0xFF546E7A)
