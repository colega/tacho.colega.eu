package eu.colega.tacho.app.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.colega.tacho.core.analysis.ActivityAnalysis
import eu.colega.tacho.core.tachograph.DriverCard
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.DriverCardParser
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI state of the driver card reader. */
data class CardUiState(
    val isReading: Boolean = false,
    val card: DriverCard? = null,
    val analysis: ActivityAnalysis.Analysis? = null,
    val error: String? = null,
)

@HiltViewModel
class CardViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    /** Parse a completed dump and update the UI state. */
    fun onDumpRead(dump: DriverCardDump) {
        _state.value = try {
            val card = DriverCardParser.parse(dump)
            CardUiState(card = card, analysis = ActivityAnalysis.analyze(card.activity))
        } catch (e: Exception) {
            CardUiState(error = e.message ?: "failed to parse card data")
        }
    }

    fun onReadError(message: String) {
        _state.value = CardUiState(error = message)
    }

    fun setReading(isReading: Boolean) {
        _state.update { it.copy(isReading = isReading, error = null) }
    }

    private inline fun MutableStateFlow<CardUiState>.update(transform: (CardUiState) -> CardUiState) {
        value = transform(value)
    }
}
