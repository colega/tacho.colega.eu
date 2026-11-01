package eu.colega.tacho.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import eu.colega.tacho.core.card.TruncatedDataException
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.TcdContainer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * High-level driver card source: from a USB reader or a dump file. Also
 * serialises dumps to a portable `.tcd` (tachograph card dump) container.
 */
class CardRepository {

    /** Parse a `.tcd` container (FID + length + payload per elementary file). */
    fun fromConcatenatedDump(bytes: ByteArray): DriverCardDump = TcdContainer.fromBytes(bytes)

    /** Serialise a dump to the `.tcd` container format. */
    fun toConcatenatedDump(dump: DriverCardDump): ByteArray = TcdContainer.toBytes(dump)

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

    /**
     * Persist the dump so the card survives app restarts; drivers should not
     * lose their data because the process was killed.
     */
    fun saveLastDump(context: Context, dump: DriverCardDump) {
        context.openFileOutput(LAST_DUMP_FILE, Context.MODE_PRIVATE).use {
            it.write(toConcatenatedDump(dump))
        }
    }

    /** Restore the last persisted dump, or null when none is stored or it is unreadable. */
    fun loadLastDump(context: Context): DriverCardDump? =
        context.filesDir.resolve(LAST_DUMP_FILE)
            .takeIf { it.exists() }
            ?.let { file -> runCatching { fromConcatenatedDump(file.readBytes()) }.getOrNull() }

    private companion object {
        const val LAST_DUMP_FILE = "last-card.tcd"
    }
}
