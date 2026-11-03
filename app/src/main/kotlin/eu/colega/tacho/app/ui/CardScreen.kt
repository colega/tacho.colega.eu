package eu.colega.tacho.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.colega.tacho.app.R
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.tachograph.DriverCard
import eu.colega.tacho.core.tachograph.Layout
import eu.colega.tacho.core.tachograph.HolderName
import eu.colega.tacho.core.tachograph.efName
import java.time.LocalDate

/** Card overview: holder identity, validity, last recorded vehicle use and card file actions. */
@Composable
fun CardScreen(
    viewModel: CardViewModel = hiltViewModel(),
    attachedReader: kotlinx.coroutines.flow.StateFlow<android.hardware.usb.UsbDevice?>? = null,
    onReadViaReader: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.card_title), style = MaterialTheme.typography.headlineSmall)

        state.error?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (state.isReading) {
            ReadingProgress(state.readingFile)
        }

        val card = state.card
        if (card == null) {
            EmptyLanding(
                attachedReader = attachedReader,
                onReadViaReader = onReadViaReader,
                canRead = !state.isReading,
                onOpenFile = importPicker { viewModel.importDump(context, it) },
            )
        } else {
            HolderCard(card)
            ValidityCard(card)
            SessionCard(card)
            Button(
                onClick = { viewModel.requestExport(context) },
                enabled = !state.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(stringResource(R.string.card_export_dump), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = importPicker { viewModel.importDump(context, it) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.card_import_dump), Modifier.padding(start = 8.dp))
            }
        }
        TextButton(
            onClick = { uriHandler.openUri(privacyPolicyUrl) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(stringResource(R.string.privacy_policy))
        }
    }
}

/** Shared SAF picker launcher for importing a previously exported card file. */
@Composable
private fun importPicker(onImport: (android.net.Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImport(uri)
    }
    return { picker.launch(arrayOf("*/*")) }
}

/**
 * Landing state before any card has been read: explains what the app does
 * and offers the two ways in, with clear full-width actions.
 */
@Composable
private fun EmptyLanding(
    attachedReader: kotlinx.coroutines.flow.StateFlow<android.hardware.usb.UsbDevice?>?,
    onReadViaReader: (() -> Unit)?,
    canRead: Boolean,
    onOpenFile: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                Icons.Filled.CreditCard,
                contentDescription = null,
                modifier = Modifier.padding(18.dp).size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            stringResource(R.string.card_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.card_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(4.dp))
        val reader = attachedReader?.collectAsState()?.value
        if (attachedReader != null && onReadViaReader != null && reader != null && canRead) {
            Button(onClick = onReadViaReader, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.card_read_reader), Modifier.padding(start = 8.dp))
            }
        } else {
            Text(
                stringResource(R.string.card_empty_no_reader),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.card_import_dump), Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Progress while the card downloads: a plain-language step for each
 * elementary file. Tapping toggles the technical detail (EF name, FID).
 */
@Composable
private fun ReadingProgress(readingFile: Int?) {
    var showTechnical by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Filled.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(readingStepLabel(readingFile)),
                    Modifier.padding(start = 12.dp).weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showTechnical = !showTechnical }) {
                    Text(
                        stringResource(
                            if (showTechnical) R.string.reading_hide_details else R.string.reading_show_details
                        ),
                    )
                }
            }
            if (showTechnical) {
                Text(
                    stringResource(
                        R.string.reading_technical_detail,
                        efName(readingFile ?: 0),
                        readingFile ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Plain-language label for the elementary file being read (null = connecting). */
private fun readingStepLabel(fid: Int?): Int = when (fid) {
    null -> R.string.reading_connecting
    Layout.EF_APPLICATION_IDENTIFICATION -> R.string.reading_application
    0xC100, 0xC101, 0xC108, 0xC109 -> R.string.reading_certificates
    Layout.EF_IDENTIFICATION -> R.string.reading_identification
    Layout.EF_CARD_DOWNLOAD -> R.string.reading_last_download
    Layout.EF_DRIVING_LICENCE_INFO -> R.string.reading_licence
    Layout.EF_EVENTS_DATA -> R.string.reading_events
    Layout.EF_FAULTS_DATA -> R.string.reading_faults
    Layout.EF_DRIVER_ACTIVITY_DATA -> R.string.reading_activity
    Layout.EF_VEHICLES_USED -> R.string.reading_vehicles
    Layout.EF_PLACES -> R.string.reading_places
    Layout.EF_CURRENT_USAGE -> R.string.reading_session
    Layout.EF_CONTROL_ACTIVITY_DATA -> R.string.reading_control
    Layout.EF_SPECIFIC_CONDITIONS -> R.string.reading_specific
    Layout.EF_VEHICLE_UNITS_USED -> R.string.reading_vehicle_units
    Layout.EF_GNSS_PLACES -> R.string.reading_gnss
    in 0x0525..0x052A -> R.string.reading_authentication
    else -> R.string.reading_generic
}

@Composable
private fun HolderCard(card: DriverCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.CreditCard,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistBadge(
                        text = when (card.generation) {
                            CardStructureVersion.GENERATION_1 -> stringResource(R.string.card_generation_g1)
                            else -> stringResource(R.string.card_generation_g2)
                        },
                    )
                    if (card.isV2) {
                        AssistBadge(text = stringResource(R.string.card_generation_v2))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        card.holder.name.initials(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        card.driverName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        stringResource(R.string.card_number, card.identification.cardNumber),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ValidityCard(card: DriverCard) {
    val expiry = card.identification.cardExpiryDate.takeIf {
        it.year in 1900..9999 && it.month in 1..12 && it.day in 1..31
    }?.let { runCatching { LocalDate.of(it.year, it.month, it.day) }.getOrNull() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.card_validity_title),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                expiry?.let { date ->
                    val today = LocalDate.now()
                    val (labelRes, container, content) = when {
                        date < today -> Triple(
                            R.string.card_status_expired,
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                        )
                        date < today.plusDays(30) -> Triple(
                            R.string.card_status_expiring,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        else -> Triple(
                            R.string.card_status_valid,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Surface(shape = RoundedCornerShape(50), color = container) {
                        Text(
                            stringResource(labelRes),
                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = content,
                        )
                    }
                }
            }
            Text(stringResource(R.string.card_issued, card.identification.cardIssueDate.displayDate()))
            Text(stringResource(R.string.card_expires, card.identification.cardExpiryDate.displayDate()))
            if (card.lastCardDownload.seconds > 0) {
                Text(
                    stringResource(R.string.card_last_download, card.lastCardDownload.displayDateTime()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(card: DriverCard) {
    val usage = card.currentUsage ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.card_session_title),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (usage.sessionOpenTime.seconds > 0) {
                Text(stringResource(R.string.card_session_opened, usage.sessionOpenTime.displayDateTime()))
                usage.vehicle.number.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.card_session_vehicle, usage.vehicle.nation.alpha, it))
                }
            } else {
                Text(
                    stringResource(R.string.card_session_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** First letters of the given names and the surname, e.g. "OM". */
private fun HolderName.initials(): String =
    (firstNames.firstOrNull()?.uppercaseChar()?.toString() ?: "") +
        (surname.firstOrNull()?.uppercaseChar()?.toString() ?: "")
