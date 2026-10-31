package eu.colega.tacho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.colega.tacho.core.analysis.ActivityAnalysis

/** Driving-time insights: totals, limits and detected violations. */
@Composable
fun InsightsScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val analysis = state.analysis
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Insights", style = MaterialTheme.typography.headlineSmall)
        if (analysis == null) {
            Text("Read a card to analyse driving times.", style = MaterialTheme.typography.bodyMedium)
            return
        }
        WeeklyTotals(analysis)
        Violations(analysis.violations)
    }
}

@Composable
private fun WeeklyTotals(analysis: ActivityAnalysis.Analysis) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        analysis.weeklySummaries.takeLast(2).forEach { week ->
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Week of ${week.weekStart}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${week.drivingMinutes / 60} h driving",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (week.drivingMinutes > ActivityAnalysis.WEEKLY_DRIVING_LIMIT_MIN) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        "${week.days.size} recorded days",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Violations(violations: List<ActivityAnalysis.Violation>) {
    Text(
        if (violations.isEmpty()) "No rule violations detected" else "Violations",
        style = MaterialTheme.typography.titleMedium,
    )
    if (violations.isEmpty()) return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(violations, key = { it.type.name + it.date.toString() + it.description }) { violation ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        violation.type.name.lowercase().replace('_', ' '),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        violation.description + " · " + violation.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (violation.excessMinutes > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("+${violation.excessMinutes} min") },
                        )
                    }
                }
            }
        }
    }
}
