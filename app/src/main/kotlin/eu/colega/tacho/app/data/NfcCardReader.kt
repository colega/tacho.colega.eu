package eu.colega.tacho.app.data

import android.nfc.tech.IsoDep
import eu.colega.tacho.core.card.ApduTransceiver
import eu.colega.tacho.core.card.TruncatedDataException
import eu.colega.tacho.core.tachograph.CardDumpReader
import eu.colega.tacho.core.tachograph.DriverCardDump

/**
 * Reads a tachograph card over NFC using the ISO 7816-4 [IsoDep] transport and
 * the APDU command set from the core module.
 */
class NfcCardReader(private val isoDep: IsoDep) {

    init {
        isoDep.timeout = 20_000 // cards can be slow on certificate reads
    }

    /** Perform a full card read; returns the parsed dump keyed by EF id. */
    fun readCard(): DriverCardDump {
        isoDep.connect()
        try {
            val transceiver = ApduTransceiver { command -> isoDep.transceive(command) }
            return CardDumpReader(transceiver).readDriverCard()
        } finally {
            runCatching { isoDep.close() }
        }
    }
}

/**
 * High-level driver card source: from an NFC reader or a downloaded dump file.
 */
class CardRepository {

    /** Parse a dump where every elementary file appears contiguously, 2-byte FID prefixed. */
    fun fromConcatenatedDump(bytes: ByteArray): DriverCardDump {
        val files = mutableMapOf<Int, ByteArray>()
        var offset = 0
        while (offset + 2 <= bytes.size) {
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

    private fun readU32(bytes: ByteArray, at: Int): Int {
        require(at + 4 <= bytes.size) { "truncated length field" }
        return ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }
}
