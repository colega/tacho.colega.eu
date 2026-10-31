package eu.colega.tacho.app

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.colega.tacho.app.data.NfcCardReader
import eu.colega.tacho.app.ui.CardViewModel
import eu.colega.tacho.app.ui.TachoNavGraph
import eu.colega.tacho.app.ui.theme.TachoColegaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cardViewModel: CardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TachoColegaTheme {
                TachoNavGraph(cardViewModel)
            }
        }
        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    /** A card was tapped: read it off the main-thread intent and parse in the background. */
    private fun handleNfcIntent(intent: Intent?) {
        val tag = IntentCompat.getParcelableExtra(intent!!, NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return
        val isoDep = IsoDep.get(tag) ?: return
        cardViewModel.setReading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                cardViewModel.onDumpRead(NfcCardReader(isoDep).readCard())
            } catch (e: Exception) {
                cardViewModel.onReadError(e.message ?: "card read failed")
            }
        }
    }
}
