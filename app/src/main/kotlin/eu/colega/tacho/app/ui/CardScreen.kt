package eu.colega.tacho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.colega.tacho.app.R
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.tachograph.DriverCard

/** Card overview: holder identity, validity, current session and raw-data export. */
@Composable
fun CardScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.card_title), style = MaterialTheme.typography.headlineSmall)

        if (state.isReading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.card_reading))
            }
        }

        state.error?.let { error ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(error, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        val card = state.card
        if (card == null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.card_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.card_empty_body),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            HolderCard(card)
            ValidityCard(card)
            SessionCard(card)
            Button(
                onClick = { viewModel.requestExport(context) },
                enabled = !state.isExporting,
            ) {
                if (state.isExporting) {
                    CircularProgressIndicator(
                        Modifier.padding(end = 8.dp).size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.card_export_dump))
            }
        }
    }
}

@Composable
private fun HolderCard(card: DriverCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.driverName, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.card_number, card.identification.cardNumber),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            when (card.generation) {
                                CardStructureVersion.GENERATION_1 -> stringResource(R.string.card_generation_g1)
                                else -> stringResource(R.string.card_generation_g2)
                            }
                        )
                    },
                )
                if (card.isV2) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.card_generation_v2)) })
                }
            }
        }
    }
}

@Composable
private fun ValidityCard(card: DriverCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.card_validity_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.card_issued, card.identification.cardIssueDate.toString()))
                Text(stringResource(R.string.card_expires, card.identification.cardExpiryDate.toString()))
            }
        }
    }
}

@Composable
private fun SessionCard(card: DriverCard) {
    val usage = card.currentUsage ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.card_session_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.card_session_opened, usage.sessionOpenTime.toString()))
            usage.vehicle.number.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.card_session_vehicle, usage.vehicle.nation.alpha, it))
            }
        }
    }
}
