package eu.colega.tacho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.colega.tacho.app.R
import eu.colega.tacho.core.analysis.ActivityAnalysis
import eu.colega.tacho.core.analysis.ActivityAnalysis.Violation

/** Driving-time insights: weekly chart, totals and detected violations. */
@Composable
fun InsightsScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val analysis = state.analysis
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineSmall)
        if (analysis == null) {
            Text(stringResource(R.string.insights_empty), style = MaterialTheme.typography.bodyMedium)
            return
        }
        WeeklyChart(analysis)
        Violations(analysis.violations)
    }
}

/**
 * Weekly driving hours as simple bars over the weekly limit; the newest week
 * on the right. Pure Compose: no chart library, matches the app's styling.
 */
@Composable
private fun WeeklyChart(analysis: ActivityAnalysis.Analysis) {
    val weeks = analysis.weeklySummaries
    if (weeks.isEmpty()) return
    val maxMinutes = maxOf(ActivityAnalysis.WEEKLY_DRIVING_LIMIT_MIN, weeks.maxOf { it.drivingMinutes })
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.insights_weekly_driving),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEach { week ->
                    val fraction = week.drivingMinutes.toFloat() / maxMinutes
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f - fraction))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .weight(fraction.coerceAtLeast(0.02f))
                                .background(
                                    if (week.drivingMinutes > ActivityAnalysis.WEEKLY_DRIVING_LIMIT_MIN) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                ),
                        ) {}
                        Text(
                            stringResource(R.string.insights_week_driving, week.drivingMinutes / 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.insights_week_of, week.weekStart.displayDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.insights_daily_driving),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weeks.last().days.forEach { day ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${day.date.dayOfMonth}: ${day.drivingMinutes / 60} h") },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            labelColor = if (day.drivingMinutes > ActivityAnalysis.DAILY_DRIVING_LIMIT_MIN) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun Violations(violations: List<Violation>) {
    Text(
        if (violations.isEmpty()) {
            stringResource(R.string.insights_no_violations)
        } else {
            stringResource(R.string.insights_violations_title)
        },
        style = MaterialTheme.typography.titleMedium,
    )
    if (violations.isEmpty()) return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(violations, key = { it.type.name + it.date.toString() + it.description }) { violation ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        violation.localizedTitle(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        violation.date.displayDate(),
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (violation.excessMinutes > 0) {
                        Text(
                            stringResource(R.string.insights_excess_minutes, violation.excessMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

/** Maps an analysis violation type to its localized string resource. */
@Composable
private fun Violation.localizedTitle() = stringResource(
    when (type) {
        Violation.Type.DAILY_DRIVING -> R.string.violation_daily_driving
        Violation.Type.WEEKLY_DRIVING -> R.string.violation_weekly_driving
        Violation.Type.TWO_WEEK_DRIVING -> R.string.violation_fortnight
        Violation.Type.BREAK -> R.string.violation_break
    },
)
