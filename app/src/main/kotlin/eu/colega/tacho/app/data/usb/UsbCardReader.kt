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
            transceiver.powerOn()
            return try {
                CardDumpReader(transceiver).readDriverCard()
            } catch (first: Exception) {
                // A failed session (for example an interrupted read) can leave
                // state on the card; a power cycle brings it back to a clean
                // state, so try the complete download exactly once more.
                transceiver.powerOff()
                transceiver.powerOn()
                CardDumpReader(transceiver).readDriverCard()
            }
        } finally {
            transceiver.close()
        }
    }
}
