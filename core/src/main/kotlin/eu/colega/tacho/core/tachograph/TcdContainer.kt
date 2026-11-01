package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.TruncatedDataException
import java.io.ByteArrayOutputStream

/**
 * The portable `.tcd` (tachograph card dump) container: for each elementary
 * file in canonical order, its FID (2 bytes, big-endian) + payload length
 * (4 bytes, big-endian) + payload. Used for export/import, the persisted
 * last dump and local test data.
 */
object TcdContainer {

    fun toBytes(dump: DriverCardDump): ByteArray {
        val out = ByteArrayOutputStream()
        for (fid in driverCardDumpFileIds()) {
            val payload = dump.file(fid) ?: continue
            out.write((fid ushr 8) and 0xFF)
            out.write(fid and 0xFF)
            out.write((payload.size ushr 24) and 0xFF)
            out.write((payload.size ushr 16) and 0xFF)
            out.write((payload.size ushr 8) and 0xFF)
            out.write(payload.size and 0xFF)
            out.write(payload)
        }
        return out.toByteArray()
    }

    fun fromBytes(bytes: ByteArray): DriverCardDump {
        val files = mutableMapOf<Int, ByteArray>()
        var offset = 0
        while (offset + 6 <= bytes.size) {
            val fid = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            val length = readU32(bytes, offset + 2)
            val start = offset + 6
            val end = start + length
            if (end > bytes.size) throw TruncatedDataException("dump file truncated at EF %04X".format(fid))
            files[fid] = bytes.copyOfRange(start, end)
            offset = end
        }
        if (files.isEmpty()) throw TruncatedDataException("dump file contains no elementary files")
        return DriverCardDump(files)
    }

    private fun readU32(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
}
