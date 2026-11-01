package eu.colega.tacho.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import eu.colega.tacho.core.tachograph.EventFaultRecord

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
        state.card?.let { card ->
            EventList(card.events, isFault = false)
            EventList(card.faults, isFault = true)
        }
    }
}

/** The card's recorded events or faults, newest first, empty slots skipped. */
@Composable
private fun EventList(records: List<EventFaultRecord>, isFault: Boolean) {
    val real = records
        .filter { it.begin.seconds > 0 && it.typeCode != 0x00 }
        .sortedByDescending { it.begin.seconds }
    Text(
        stringResource(if (isFault) R.string.insights_faults_title else R.string.insights_events_title),
        style = MaterialTheme.typography.titleMedium,
    )
    if (real.isEmpty()) {
        Text(
            stringResource(R.string.insights_none_recorded),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    real.forEach { record ->
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isFault) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    record.label(),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isFault) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                val period = if (record.isOpen) {
                    stringResource(R.string.event_ongoing_since, record.begin.displayDateTime())
                } else {
                    stringResource(
                        R.string.event_period,
                        record.begin.displayDateTime(),
                        record.end.displayDateTime(),
                    )
                }
                Text(
                    period,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFault) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.vehicle.isPresent) {
                    Text(
                        stringResource(R.string.card_session_vehicle, record.vehicle.nation.alpha, record.vehicle.number),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFault) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Localized label for an event or fault type; unmapped codes fall back to the hex form. */
@Composable
private fun EventFaultRecord.label(): String {
    val fallback = stringResource(
        if (typeCode >= 0x30) R.string.fault_generic else R.string.event_generic,
        "0x%02X".format(typeCode),
    )
    return when (typeCode) {
        in 0x10..0x2F -> stringResource(R.string.event_security_breach)
        0x00 -> stringResource(R.string.event_no_details)
        0x01 -> stringResource(R.string.event_non_valid_card)
        0x02 -> stringResource(R.string.event_card_conflict)
        0x03 -> stringResource(R.string.event_time_overlap)
        0x04 -> stringResource(R.string.event_driving_without_card)
        0x05 -> stringResource(R.string.event_card_insertion_while_driving)
        0x06 -> stringResource(R.string.event_last_session_not_closed)
        0x07 -> stringResource(R.string.event_overspeeding)
        0x08 -> stringResource(R.string.event_power_supply)
        0x09 -> stringResource(R.string.event_motion_data_error)
        0x0A -> stringResource(R.string.event_vehicle_motion_conflict)
        0x0B -> stringResource(R.string.event_time_conflict_gnss)
        0x30 -> stringResource(R.string.fault_no_details)
        0x31 -> stringResource(R.string.fault_vu_internal)
        0x32 -> stringResource(R.string.fault_printer)
        0x33 -> stringResource(R.string.fault_display)
        0x34 -> stringResource(R.string.fault_downloading)
        0x35 -> stringResource(R.string.fault_sensor)
        0x40 -> stringResource(R.string.fault_card)
        0x51 -> stringResource(R.string.fault_gnss_internal)
        0x52 -> stringResource(R.string.fault_gnss_external)
        0x53 -> stringResource(R.string.fault_gnss_communication)
        0x54 -> stringResource(R.string.fault_gnss_no_position)
        0x55 -> stringResource(R.string.fault_gnss_tamper)
        0x56 -> stringResource(R.string.fault_gnss_certificate_expired)
        0x61 -> stringResource(R.string.fault_rcm)
        0x62 -> stringResource(R.string.fault_rcm_communication)
        0x70 -> stringResource(R.string.fault_its)
        0x11 -> stringResource(R.string.fault_motion_sensor_auth)
        0x12 -> stringResource(R.string.fault_card_auth)
        0x13 -> stringResource(R.string.fault_unauthorised_change)
        0x14 -> stringResource(R.string.fault_data_input_integrity)
        0x15 -> stringResource(R.string.fault_stored_user_data_integrity)
        0x16 -> stringResource(R.string.fault_internal_transfer)
        0x17 -> stringResource(R.string.fault_case_opening)
        0x18 -> stringResource(R.string.fault_hardware_sabotage)
        else -> fallback
    }
}

/**
 * Weekly driving hours as simple bars over the weekly limit; the newest week
 * on the right. Pure Compose: no chart library, matches the app's styling.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            // One fixed-width column per week: the card can hold months of
            // activity, so the row scrolls horizontally instead of squeezing
            // every week into the screen width.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                weeks.forEach { week ->
                    val fraction = week.drivingMinutes.toFloat() / maxMinutes
                    Column(
                        Modifier.width(56.dp),
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
                            hoursLabel(week.drivingMinutes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            week.weekStart.displayDateShort(),
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
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weeks.last().days.forEach { day ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${day.date.dayOfMonth}: ${hoursLabel(day.drivingMinutes)}") },
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            labelColor = when {
                                day.drivingMinutes > ActivityAnalysis.EXTENDED_DAILY_DRIVING_LIMIT_MIN ->
                                    MaterialTheme.colorScheme.error
                                day.drivingMinutes > ActivityAnalysis.DAILY_DRIVING_LIMIT_MIN ->
                                    MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        violations.forEach { violation ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
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
private fun Violation.localizedTitle() = stringResource(violationLabelRes(type))

/** Compact hours label that keeps half hours: "9 h" or "9 h 30". */
internal fun hoursLabel(minutes: Int): String =
    if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60}"
