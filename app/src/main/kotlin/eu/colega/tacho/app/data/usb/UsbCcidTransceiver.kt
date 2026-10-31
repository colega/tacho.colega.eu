package eu.colega.tacho.app.data.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import eu.colega.tacho.core.card.ApduTransceiver

/**
 * Talks the CCID protocol over a connected USB smart card reader and exposes
 * the ISO 7816 command layer on top of it.
 *
 * The reader runs the T=0/T=1 protocol itself: an [ApduTransceiver.transceive]
 * maps to one CCID XfrBlock, whose payload is the card response including the
 * two status words.
 */
class UsbCcidTransceiver(
    private val connection: UsbDeviceConnection,
    private val interfaceClaim: UsbInterface,
    private val bulkOut: UsbEndpoint,
    private val bulkIn: UsbEndpoint,
) : ApduTransceiver {

    private var sequence = 0

    /** Power up the card and return its ATR. */
    fun powerOn(): ByteArray {
        val response = exchange(CcidMessages.powerOn(SLOT, nextSequence()), "ICC power on")
        return response.payload
    }

    /** Power the card down; brings it back to a clean state between sessions. */
    fun powerOff() {
        exchange(CcidMessages.powerOff(SLOT, nextSequence()), "ICC power off")
    }

    override fun transceive(command: ByteArray): ByteArray {
        val response = exchange(CcidMessages.xfrBlock(command, SLOT, nextSequence()), "APDU exchange")
        return response.payload
    }

    fun close() {
        connection.releaseInterface(interfaceClaim)
        connection.close()
    }

    private fun nextSequence(): Int = sequence++ and 0xFF

    private fun exchange(frame: ByteArray, what: String): CcidResponse {
        val sent = connection.bulkTransfer(bulkOut, frame, frame.size, TIMEOUT_MS)
        require(sent == frame.size) { "$what: bulk write sent $sent of ${frame.size} bytes" }

        // Responses may span several URBs; accumulate until the frame parses.
        val buffer = ByteArray(MAX_RESPONSE_BYTES)
        var received = 0
        while (true) {
            val read = connection.bulkTransfer(bulkIn, buffer, received, buffer.size - received, TIMEOUT_MS)
            if (read <= 0) throw IllegalStateException("$what: no response from reader")
            received += read
            if (received >= CcidMessages.HEADER_SIZE) {
                val declared = le32(buffer, 1) + CcidMessages.HEADER_SIZE
                if (received >= declared) {
                    val response = CcidMessages.parse(buffer.copyOf(received))
                    if (!response.statusOk) {
                        throw IllegalStateException("$what: reader reported status 0x%02X (error 0x%02X)".format(response.status, response.error))
                    }
                    return response
                }
            }
        }
    }

    private fun le32(frame: ByteArray, offset: Int): Int =
        (frame[offset].toInt() and 0xFF) or
            ((frame[offset + 1].toInt() and 0xFF) shl 8) or
            ((frame[offset + 2].toInt() and 0xFF) shl 16) or
            ((frame[offset + 3].toInt() and 0xFF) shl 24)

    companion object {
        private const val TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_BYTES = 4096
        private const val SLOT = 0

        /**
         * Readers announce themselves as class 0x0B (ChipCard/SmartCard); some
         * use vendor-specific classes, so fall back to any interface with a
         * bulk-IN and bulk-OUT endpoint pair.
         */
        fun isSmartCardReader(device: UsbDevice): Boolean =
            (0 until device.interfaceCount).any { i ->
                val iface = device.getInterface(i)
                iface.interfaceClass == UsbConstants.USB_CLASS_CSCID ||
                    (iface.endpointCount >= 2 && hasBulkPair(iface))
            }

        private fun hasBulkPair(iface: UsbInterface): Boolean {
            var bulkIn = false
            var bulkOut = false
            for (i in 0 until iface.endpointCount) {
                val endpoint = iface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) bulkIn = true else bulkOut = true
                }
            }
            return bulkIn && bulkOut
        }

        fun firstReader(usbManager: UsbManager): UsbDevice? =
            usbManager.deviceList.values.firstOrNull { isSmartCardReader(it) }

        fun open(usbManager: UsbManager, device: UsbDevice): UsbCcidTransceiver {
            val connection = requireNotNull(usbManager.openDevice(device)) { "could not open reader" }
            val iface = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .first { it.interfaceClass == UsbConstants.USB_CLASS_CSCID || hasBulkPair(it) }
            require(connection.claimInterface(iface, true)) { "could not claim reader interface" }
            val endpoints = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            val bulkOut = endpoints.first {
                it.direction == UsbConstants.USB_DIR_OUT && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            val bulkIn = endpoints.first {
                it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_BULK
            }
            return UsbCcidTransceiver(connection, iface, bulkOut, bulkIn)
        }
    }
}
