package eu.colega.tacho.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import eu.colega.tacho.core.tachograph.DriverCard

/** Card overview: holder identity, validity and current session. */
@Composable
fun CardScreen(
    viewModel: CardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Driver card", style = MaterialTheme.typography.headlineSmall)

        if (state.isReading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Reading card…")
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
                    Text("No card read yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hold the card against the back of the phone until the read completes.",
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            HolderCard(card)
            ValidityCard(card)
            SessionCard(card)
        }
    }
}

@Composable
private fun HolderCard(card: DriverCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.driverName, style = MaterialTheme.typography.titleLarge)
            Text("Card number ${card.identification.cardNumber}", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(card.generation.name.lowercase().replace('_', ' ')) })
                if (card.isV2) AssistChip(onClick = {}, label = { Text("version 2") })
            }
        }
    }
}

@Composable
private fun ValidityCard(card: DriverCard) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Validity", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Issued ${card.identification.cardIssueDate}")
                Text("Expires ${card.identification.cardExpiryDate}")
            }
        }
    }
}

@Composable
private fun SessionCard(card: DriverCard) {
    val usage = card.currentUsage ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Current session", style = MaterialTheme.typography.titleMedium)
            Text("Opened ${usage.sessionOpenTime}")
            usage.vehicle.number.takeIf { it.isNotBlank() }?.let {
                Text("Vehicle ${usage.vehicle.nation.alpha} $it")
            }
        }
    }
}
