package eu.colega.tacho.core.tachograph

/**
 * A complete dump of a driver card application: the concatenated bytes of every
 * elementary file, keyed by file identifier. Produced by the NFC reader (APDU
 * responses per EF) or loaded from a card download file.
 */
class DriverCardDump(
    private val files: Map<Int, ByteArray>,
) {
    fun file(fid: Int): ByteArray? = files[fid]

    fun has(fid: Int): Boolean = files.containsKey(fid)

    /** File identifiers present in this dump, sorted. */
    val fileIds: List<Int> get() = files.keys.sorted()

    val isEmpty: Boolean get() = files.isEmpty()

    companion object {
        fun of(vararg pairs: Pair<Int, ByteArray>): DriverCardDump = DriverCardDump(pairs.toMap())
    }
}
