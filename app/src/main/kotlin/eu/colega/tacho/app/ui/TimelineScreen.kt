package eu.colega.tacho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.colega.tacho.app.R
import eu.colega.tacho.core.analysis.ActivityAnalysis

/** Daily activity timeline built from the card's activity change records. */
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
            DayRow(summary)
            HorizontalDivider()
        }
    }
}

@Composable
private fun DayRow(summary: ActivityAnalysis.DailySummary) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(summary.date.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.timeline_driving_hours, summary.drivingMinutes / 60),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.drivingMinutes > ActivityAnalysis.DAILY_DRIVING_LIMIT_MIN) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
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
    }
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
