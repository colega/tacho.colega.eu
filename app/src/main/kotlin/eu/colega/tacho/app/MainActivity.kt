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
    private var nfcAdapter: NfcAdapter? = null

    override fun onResume() {
        super.onResume()
        // Reader mode delivers ISO-DEP tags while the app is open, without
        // competing with other apps' tag dispatch.
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        cardViewModel.onNfcStateChanged(nfcAdapter?.isEnabled)
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TachoColegaTheme {
                TachoNavGraph(cardViewModel)
            }
        }
        lifecycleScope.launch {
            cardViewModel.exportIntent.collect { shareIntent ->
                startActivity(Intent.createChooser(shareIntent, null))
            }
        }
        handleNfcIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    /** A card was tapped: read it in the background and parse the dump. */
    private fun handleTag(tag: Tag) {
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

    /** Fallback route: the system tag dispatch when the app was closed. */
    private fun handleNfcIntent(intent: Intent?) {
        val tag = intent?.let { IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java) }
            ?: return
        handleTag(tag)
    }
}
