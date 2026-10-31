package eu.colega.tacho.core.card

/**
 * APDU command construction and response parsing for tachograph cards,
 * per the restricted ISO/IEC 7816-4 usage in chapter 3.5 of the card
 * specification.
 */
object Apdu {

    const val INS_SELECT = 0xA4
    const val INS_READ_BINARY = 0xB0
    const val INS_GET_RESPONSE = 0xC0

    const val SW_OK = 0x9000
    const val SW_EOF_REACHED = 0x6B00 // offset beyond EF size
    const val SW_WRONG_LENGTH = 0x6700
    const val SW_FILE_NOT_FOUND = 0x6A82
    const val SW_SECURITY_NOT_SATISFIED = 0x6982
    const val SW_NO_EF_SELECTED = 0x6986
    const val SW_CORRUPTED_FILE = 0x6400

    // Generation 1 tachograph application AID: 'FF 54 41 43 48 4F'
    val AID_G1 = byteArrayOf(0xFF.toByte(), 0x54, 0x41, 0x43, 0x48, 0x4F)
    // Generation 2 tachograph application AID: 'FF 53 4D 52 44 54'
    val AID_G2 = byteArrayOf(0xFF.toByte(), 0x53, 0x4D, 0x52, 0x44, 0x54)

    /** SELECT by AID (P1=04 'by name', P2=0C no response expected). */
    fun selectApplication(aid: ByteArray): ByteArray {
        val out = ByteArray(5 + aid.size)
        out[0] = 0x00 // CLA
        out[1] = INS_SELECT.toByte()
        out[2] = 0x04 // P1: select by DF name
        out[3] = 0x0C // P2: no response data
        out[4] = aid.size.toByte() // Lc
        aid.copyInto(out, 5)
        return out
    }

    /** SELECT elementary file by its 2-byte file identifier (P1=02, P2=0C). */
    fun selectFile(fid: Int): ByteArray {
        require(fid in 0x0001..0xFFFF)
        return byteArrayOf(
            0x00,
            INS_SELECT.toByte(),
            0x02,
            0x0C,
            0x02,
            ((fid shr 8) and 0xFF).toByte(),
            (fid and 0xFF).toByte(),
        )
    }

    /**
     * READ BINARY from the currently selected EF. Offset uses P1-P2 with the
     * P1 bit 8 set to 0, Le capped at 255.
     */
    fun readBinary(offset: Int, length: Int): ByteArray {
        require(offset in 0..0x7FFF) { "offset $offset exceeds 15-bit addressing" }
        require(length in 1..255) { "length $length out of APDU range" }
        return byteArrayOf(
            0x00,
            INS_READ_BINARY.toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            (offset and 0xFF).toByte(),
            length.toByte(),
        )
    }

    /** GET RESPONSE (T=0): CLA 00, INS C0, Le = remaining length. */
    fun getResponse(length: Int): ByteArray =
        byteArrayOf(0x00, INS_GET_RESPONSE.toByte(), 0x00, 0x00, length.coerceIn(1, 255).toByte())
}

/** Status words returned by the card, with the data part of the response. */
data class ApduResponse(
    val data: ByteArray,
    val statusWord: Int,
) {
    val isOk: Boolean get() = statusWord == Apdu.SW_OK

    /** '61xx': xx more bytes are available via GET RESPONSE. */
    val moreAvailable: Int
        get() = if (statusWord and 0xFF00 == 0x6100) statusWord and 0xFF else 0

    companion object {
        fun from(raw: ByteArray): ApduResponse {
            require(raw.size >= 2) { "APDU response must carry status words" }
            val sw = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or
                (raw[raw.size - 1].toInt() and 0xFF)
            return ApduResponse(raw.copyOfRange(0, raw.size - 2), sw)
        }
    }
}

/**
 * Low-level transport to the card (ISO 7816-4 T=0/T=1). Implemented by the
 * Android NFC stack; tests use a scripted fake.
 */
fun interface ApduTransceiver {
    /** Send raw command bytes, receive raw response bytes (data + SW). */
    fun transceive(command: ByteArray): ByteArray
}
