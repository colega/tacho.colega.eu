package eu.colega.tacho.core.card

/**
 * Bounds-checked cursor over card bytes. Multi-byte integers are big-endian,
 * as mandated by the tachograph card specification.
 */
class ByteReader(private val data: ByteArray, private var pos: Int = 0, private val end: Int = data.size) {

    init {
        require(pos in 0..end && end <= data.size) { "invalid range [$pos, $end) for ${data.size} bytes" }
    }

    val remaining: Int get() = end - pos
    val atEnd: Boolean get() = remaining == 0

    /** Read a 16-bit big-endian value at an offset relative to the cursor, without moving it. */
    fun u16At(offset: Int): Int {
        require(offset >= 0 && pos + offset + 1 < end) { "offset $offset out of bounds" }
        return ((data[pos + offset].toInt() and 0xFF) shl 8) or (data[pos + offset + 1].toInt() and 0xFF)
    }

    fun skip(n: Int) {
        if (n < 0 || remaining < n) throw TruncatedDataException()
        pos += n
    }

    fun u8(): Int = if (remaining >= 1) data[pos++].toInt() and 0xFF else throw TruncatedDataException()

    fun u16(): Int = (u8() shl 8) or u8()

    fun u24(): Int = (u16() shl 8) or u8()

    fun u32(): Long = (u24().toLong() shl 8) or u8().toLong()

    fun bytes(n: Int): ByteArray {
        if (n < 0 || remaining < n) throw TruncatedDataException()
        val out = data.copyOfRange(pos, pos + n)
        pos += n
        return out
    }

    /** ASCII string padded with NUL or spaces; padding is trimmed. */
    fun ascii(n: Int): String {
        val raw = bytes(n)
        var end = raw.size
        while (end > 0 && (raw[end - 1] == 0x20.toByte() || raw[end - 1] == 0x00.toByte())) end--
        var start = 0
        while (start < end && (raw[start] == 0x20.toByte() || raw[start] == 0x00.toByte())) start++
        return String(raw, start, end - start, Charsets.US_ASCII)
    }
}

class TruncatedDataException : IllegalStateException {
    constructor() : super("unexpected end of card data")
    constructor(message: String) : super(message)
}
