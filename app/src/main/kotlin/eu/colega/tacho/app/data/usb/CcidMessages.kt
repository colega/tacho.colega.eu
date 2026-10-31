package eu.colega.tacho.app.data.usb

import java.io.ByteArrayOutputStream

/**
 * Message framing for the USB Chip/Smart Card Interface Device (CCID)
 * protocol, version 1.10. Messages travel on the bulk-OUT / bulk-IN
 * endpoints of the reader; each frame is a 10-byte header plus payload.
 *
 * Only the subset a card download needs is implemented: power on the ICC
 * and exchange APUs (the reader runs T=0/T=1 itself).
 */
object CcidMessages {

    const val PC_TO_RDR_ICC_POWER_ON = 0x62
    const val PC_TO_RDR_ICC_POWER_OFF = 0x63
    const val PC_TO_RDR_GET_SLOT_STATUS = 0x65
    const val PC_TO_RDR_XFR_BLOCK = 0x6F

    const val RDR_TO_PC_DATA_BLOCK = 0x80
    const val RDR_TO_PC_SLOT_STATUS = 0x81

    const val HEADER_SIZE = 10

    /** Protocols of the message-level protocol preferences: T=0. */
    const val PROTOCOL_T0 = 0x01

    /** An ICC is present and active, and the last command succeeded. */
    const val STATUS_OK = 0x00

    /** Build a PC_to_RDR frame: header + payload. */
    fun frame(messageType: Int, slot: Int, sequence: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArrayOutputStream(HEADER_SIZE + payload.size)
        out.write(messageType)
        writeLe32(out, payload.size)
        out.write(slot)
        out.write(sequence)
        out.write(0) // bRFU
        out.write(0)
        out.write(0)
        out.write(payload)
        return out.toByteArray()
    }

    fun powerOn(slot: Int, sequence: Int): ByteArray =
        frame(PC_TO_RDR_ICC_POWER_ON, slot, sequence, byteArrayOf(PROTOCOL_T0.toByte()))

    fun powerOff(slot: Int, sequence: Int): ByteArray =
        frame(PC_TO_RDR_ICC_POWER_OFF, slot, sequence)

    fun getSlotStatus(slot: Int, sequence: Int): ByteArray =
        frame(PC_TO_RDR_GET_SLOT_STATUS, slot, sequence)

    /** XfrBlock: exchange one APDU with the card in the reader. */
    fun xfrBlock(apdu: ByteArray, slot: Int, sequence: Int): ByteArray =
        frame(PC_TO_RDR_XFR_BLOCK, slot, sequence, apdu)

    /**
     * Parse an RDR_to_PC frame. Returns the payload (e.g. ATR or card
     * response) and the bStatus octet; [statusOk] reports whether the
     * command succeeded with an ICC present and active.
     */
    fun parse(frame: ByteArray): CcidResponse {
        require(frame.size >= HEADER_SIZE) { "CCID frame shorter than its 10-byte header" }
        val messageType = frame[0].toInt() and 0xFF
        val payloadLength = readLe32(frame, 1)
        if (frame.size < HEADER_SIZE + payloadLength) {
            throw IllegalStateException("truncated CCID frame: ${frame.size} bytes, expected ${HEADER_SIZE + payloadLength}")
        }
        val sequence = frame[6].toInt() and 0xFF
        val status = frame[7].toInt() and 0xFF
        val error = frame[8].toInt() and 0xFF
        val payload = frame.copyOfRange(HEADER_SIZE, HEADER_SIZE + payloadLength)
        return CcidResponse(messageType, sequence, status, error, payload)
    }

    private fun writeLe32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }

    private fun readLe32(frame: ByteArray, offset: Int): Int =
        (frame[offset].toInt() and 0xFF) or
            ((frame[offset + 1].toInt() and 0xFF) shl 8) or
            ((frame[offset + 2].toInt() and 0xFF) shl 16) or
            ((frame[offset + 3].toInt() and 0xFF) shl 24)
}

/** Parsed CCID response frame. */
data class CcidResponse(
    val messageType: Int,
    val sequence: Int,
    val status: Int,
    val error: Int,
    val payload: ByteArray,
) {
    val statusOk: Boolean get() = status == CcidMessages.STATUS_OK
}
