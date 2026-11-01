package eu.colega.tacho.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.colega.tacho.app.ui.CardViewModel
import eu.colega.tacho.app.ui.TachoNavGraph
import eu.colega.tacho.app.ui.theme.TachoColegaTheme
import eu.colega.tacho.app.data.usb.UsbCcidTransceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val cardViewModel: CardViewModel by viewModels()

    /** The attached CCID reader, or null when none is plugged in. */
    private val _attachedReader = MutableStateFlow<UsbDevice?>(null)
    val attachedReader: StateFlow<UsbDevice?> = _attachedReader.asStateFlow()

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                cardViewModel.readViaUsbReader(getSystemService(UsbManager::class.java), device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { cardViewModel.state.value.isRestoring }
        enableEdgeToEdge()
        setContent {
            TachoColegaTheme {
                TachoNavGraph(
                    cardViewModel,
                    attachedReader,
                    onReadViaReader = { attachedReader.value?.let(::requestReader) },
                )
            }
        }
        lifecycleScope.launch {
            cardViewModel.exportIntent.collect { shareIntent ->
                startActivity(Intent.createChooser(shareIntent, null))
            }
        }
        // The USB permission broadcast is sent by the system; NOT_EXPORTED still
        // receives it while blocking other apps from spoofing the grant.
        registerReceiver(
            usbPermissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            usbAttachReceiver,
            // The attach action has no Intent constant; the literal is the documented value.
        IntentFilter("android.hardware.usb.action.USB_DEVICE_ATTACHED"),
            Context.RECEIVER_NOT_EXPORTED,
        )
        refreshAttachedReader()
        handleUsbIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshAttachedReader()
    }

    private fun refreshAttachedReader() {
        val usbManager = getSystemService(UsbManager::class.java)
        _attachedReader.value = UsbCcidTransceiver.firstReader(usbManager)
    }

    private val usbAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshAttachedReader()
            intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)?.let { requestReader(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    /** A reader (with card) was attached: ask for permission, then download. */
    private fun handleUsbIntent(intent: Intent?) {
        val device = intent?.let {
            IntentCompat.getParcelableExtra(it, UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } ?: return
        requestReader(device)
    }

    /** Ask the user for reader access if needed, then download the card. */
    fun requestReader(device: UsbDevice) {
        val usbManager = getSystemService(UsbManager::class.java)
        if (usbManager.hasPermission(device)) {
            cardViewModel.readViaUsbReader(usbManager, device)
        } else {
            // Mutable: the system attaches EXTRA_DEVICE to the intent.
            val pending = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(packageName),
                PendingIntent.FLAG_MUTABLE,
            )
            usbManager.requestPermission(device, pending)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(usbPermissionReceiver)
        super.onDestroy()
    }

    private fun Intent.getParcelableExtraCompat(name: String): UsbDevice? =
        IntentCompat.getParcelableExtra(this, name, UsbDevice::class.java)

    private companion object {
        const val ACTION_USB_PERMISSION = "eu.colega.tacho.USB_PERMISSION"
    }
}
