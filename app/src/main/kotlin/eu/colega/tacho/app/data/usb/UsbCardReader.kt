package eu.colega.tacho.app.data.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import eu.colega.tacho.core.tachograph.CardDumpReader
import eu.colega.tacho.core.tachograph.DriverCardDump

/**
 * Downloads the whole driver card through a USB CCID reader: power up the
 * card, then read every elementary file with the transport-agnostic
 * [CardDumpReader].
 */
class UsbCardReader(private val usbManager: UsbManager, private val device: UsbDevice) {

    fun readCard(): DriverCardDump {
        val transceiver = UsbCcidTransceiver.open(usbManager, device)
        try {
            val atr = transceiver.powerOn()
            require(atr.isNotEmpty()) { "reader returned no ATR: is the card inserted?" }
            return CardDumpReader(transceiver).readDriverCard()
        } finally {
            transceiver.close()
        }
    }
}
