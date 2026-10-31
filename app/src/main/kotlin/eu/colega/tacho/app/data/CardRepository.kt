package eu.colega.tacho.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import eu.colega.tacho.core.card.TruncatedDataException
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.Layout
import eu.colega.tacho.core.tachograph.driverCardDumpFileIds
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * High-level driver card source: from an NFC reader or a downloaded dump file.
 * Also serialises dumps to a portable `.tcd` (tachograph card dump) container.
 */
class CardRepository {

    /** Parse a dump where every elementary file appears contiguously, 2-byte FID prefixed. */
    fun fromConcatenatedDump(bytes: ByteArray): DriverCardDump {
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

    /**
     * Serialise a dump to the shareable container format: for each EF in
     * canonical order, FID (2 bytes, big-endian) + length (4 bytes) + payload.
     */
    fun toConcatenatedDump(dump: DriverCardDump): ByteArray {
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

    /**
     * Write the dump to the app's cache directory and return a share intent
     * (ACTION_SEND with a FileProvider URI) for saving to Drive, email, etc.
     */
    fun exportToShareIntent(context: Context, dump: DriverCardDump): Intent {
        val bytes = toConcatenatedDump(dump)
        val stamp = Instant.now().atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = java.io.File(context.cacheDir, "tacho-card-$stamp.tcd")
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Import a previously exported dump from a content [Uri]. */
    fun importFromUri(context: Context, uri: Uri): DriverCardDump {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw TruncatedDataException("could not open dump file")
        return fromConcatenatedDump(bytes)
    }

    private fun readU32(bytes: ByteArray, at: Int): Int {
        require(at + 4 <= bytes.size) { "truncated length field" }
        return ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }
}
