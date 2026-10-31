package eu.colega.tacho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.colega.tacho.app.R
import eu.colega.tacho.core.analysis.ActivityAnalysis
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month calendar with one colour-coded cell per day: driving intensity
 * (green under the daily limit, amber on extended days, red over the limit,
 * blue for rest days). Days without recorded activity are grey.
 */
@Composable
fun CalendarScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val analysis = state.analysis
    var month by remember { mutableStateOf(YearMonth.now()) }
    val summariesByDate = remember(analysis) {
        analysis?.dailySummaries?.associateBy { it.date } ?: emptyMap()
    }
    val monthDays = remember(month) {
        val first = month.atDay(1)
        // ISO weeks start on Monday; pad before the 1st so columns align.
        val lead = first.dayOfWeek.value - 1
        List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineSmall)
        if (analysis == null) {
            Text(stringResource(R.string.calendar_empty), style = MaterialTheme.typography.bodyMedium)
            return
        }

        MonthHeader(month, onPrevious = { month = month.minusMonths(1) }, onNext = { month = month.plusMonths(1) })

        Row(Modifier.fillMaxWidth()) {
            for (index in 0..6) {
                Text(
                    stringResource(weekdayResource(index)),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        monthDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                    } else {
                        val summary = summariesByDate[date]
                        val description = dayDescription(date, summary)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center,
                        ) {
                            DayCell(
                                date = date,
                                summary = summary,
                                inCurrentMonth = date.month == month.month,
                                isToday = date == LocalDate.now(),
                                onClick = { month = YearMonth.from(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous_month))
        }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: ActivityAnalysis.DailySummary?,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        summary == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (inCurrentMonth) 0.5f else 0.2f)
        summary.drivingMinutes > ActivityAnalysis.EXTENDED_DAILY_DRIVING_LIMIT_MIN -> OverLimitColor
        summary.drivingMinutes > ActivityAnalysis.DAILY_DRIVING_LIMIT_MIN -> ExtendedColor
        summary.drivingMinutes > 0 -> DrivingColor
        else -> RestColor
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .background(background.copy(alpha = if (inCurrentMonth) 1f else 0.35f), CircleShape)
            .then(
                if (isToday) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            color = if (summary != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun dayDescription(date: LocalDate, summary: ActivityAnalysis.DailySummary?): String {
    val hours = summary?.drivingMinutes?.div(60) ?: 0
    return stringResource(R.string.calendar_day_a11y, date.toString(), "$hours h")
}

private val DrivingColor = Color(0xFF2E7D32)
private val ExtendedColor = Color(0xFFEFB000)
private val OverLimitColor = Color(0xFFC62828)
private val RestColor = Color(0xFF0288D1)

/** ISO weekday order (Mon..Sun) mapped to the calendar string resources. */
private fun weekdayResource(index: Int): Int = when (index) {
    0 -> R.string.calendar_weekday_monday
    1 -> R.string.calendar_weekday_tuesday
    2 -> R.string.calendar_weekday_wednesday
    3 -> R.string.calendar_weekday_thursday
    4 -> R.string.calendar_weekday_friday
    5 -> R.string.calendar_weekday_saturday
    else -> R.string.calendar_weekday_sunday
}
