package eu.colega.tacho.app.data

import android.nfc.tech.IsoDep
import eu.colega.tacho.core.card.ApduTransceiver
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
