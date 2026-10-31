package eu.colega.tacho.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.colega.tacho.app.data.CardRepository
import eu.colega.tacho.core.analysis.ActivityAnalysis
import eu.colega.tacho.core.tachograph.DriverCard
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.DriverCardParser
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** UI state of the driver card reader. */
data class CardUiState(
    val isReading: Boolean = false,
    val isExporting: Boolean = false,
    val card: DriverCard? = null,
    val analysis: ActivityAnalysis.Analysis? = null,
    val error: String? = null,
)

@HiltViewModel
class CardViewModel @Inject constructor(
    private val repository: CardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = _state.asStateFlow()

    private var currentDump: DriverCardDump? = null

    private val _exportIntent = MutableSharedFlow<android.content.Intent>(extraBufferCapacity = 1)
    val exportIntent: SharedFlow<android.content.Intent> = _exportIntent.asSharedFlow()

    /** Parse a completed dump and update the UI state. */
    fun onDumpRead(dump: DriverCardDump) {
        currentDump = dump
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

    /** Report that the current dump is being exported (UI shows progress). */
    fun setExporting(isExporting: Boolean) {
        _state.update { it.copy(isExporting = isExporting) }
    }

    /** Import a previously exported dump file chosen through the SAF picker. */
    fun importDump(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            setReading(true)
            try {
                onDumpRead(repository.importFromUri(context, uri))
            } catch (e: Exception) {
                onReadError(e.message ?: context.getString(eu.colega.tacho.app.R.string.error_generic))
            }
        }
    }

    /** Serialise the current dump and hand a share intent back to the UI. */
    fun requestExport(context: Context) {
        val dump = currentDump ?: return
        viewModelScope.launch(Dispatchers.IO) {
            setExporting(true)
            val intent = runCatching { repository.exportToShareIntent(context, dump) }
                .onFailure { onReadError(it.message ?: "export failed") }
            setExporting(false)
            intent.getOrNull()?.let { _exportIntent.emit(it) }
        }
    }

    private inline fun MutableStateFlow<CardUiState>.update(transform: (CardUiState) -> CardUiState) {
        value = transform(value)
    }
}
