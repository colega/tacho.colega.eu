package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.Apdu
import eu.colega.tacho.core.card.ApduResponse
import eu.colega.tacho.core.card.ApduTransceiver
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.card.ByteReader

/**
 * Reads every elementary file of a driver card application over an
 * [ApduTransceiver] and returns a [DriverCardDump].
 *
 * The generation is detected from the SELECT response of the two known
 * application identifiers; each EF is then read with READ BINARY in 255-byte
 * chunks until the card reports end of file.
 */
class CardDumpReader(
    private val transceiver: ApduTransceiver,
    private val chunkSize: Int = 255,
) {
    fun readDriverCard(): DriverCardDump {
        val generation = selectApplication()
        val efIds = if (generation == CardStructureVersion.GENERATION_1) G1_EF_IDS else G2_EF_IDS
        val files = mutableMapOf<Int, ByteArray>()
        for (fid in efIds) {
            val data = readFile(fid)
            if (data != null && data.isNotEmpty()) files[fid] = data
        }
        return DriverCardDump(files)
    }

    /** Select the application by AID and report its generation. */
    private fun selectApplication(): CardStructureVersion {
        val response = ApduResponse.from(transceiver.transceive(Apdu.selectApplication(Apdu.AID_G2)))
        if (response.isOk) return CardStructureVersion.GENERATION_2
        val g1 = ApduResponse.from(transceiver.transceive(Apdu.selectApplication(Apdu.AID_G1)))
        if (g1.isOk) return CardStructureVersion.GENERATION_1
        throw IllegalStateException("no tachograph application found on card")
    }

    /** Read one EF fully; returns null if the file does not exist on this card. */
    private fun readFile(fid: Int): ByteArray? {
        val select = ApduResponse.from(transceiver.transceive(Apdu.selectFile(fid)))
        if (select.statusWord == Apdu.SW_FILE_NOT_FOUND) return null
        if (!select.isOk) return null
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        while (true) {
            val response = ApduResponse.from(transceiver.transceive(Apdu.readBinary(offset, chunkSize)))
            if (response.statusWord == Apdu.SW_EOF_REACHED && offset == 0) return null
            when {
                response.isOk -> {
                    out.write(response.data)
                    if (response.data.size < chunkSize) break
                    offset += response.data.size
                }
                response.statusWord == Apdu.SW_EOF_REACHED -> break // file fully read
                else -> break // tolerate '62xx' warnings etc.; keep what we have
            }
        }
        return out.toByteArray()
    }

    companion object {
        /** Generation 2 (amended) driver card EF list. */
        val G2_EF_IDS = listOf(
            Layout.EF_APPLICATION_IDENTIFICATION,
            Layout.EF_CARD_MA_CERTIFICATE,
            Layout.EF_CARD_SIGN_CERTIFICATE,
            Layout.EF_CA_CERTIFICATE,
            Layout.EF_LINK_CERTIFICATE,
            Layout.EF_IDENTIFICATION,
            Layout.EF_CARD_DOWNLOAD,
            Layout.EF_DRIVING_LICENCE_INFO,
            Layout.EF_EVENTS_DATA,
            Layout.EF_FAULTS_DATA,
            Layout.EF_DRIVER_ACTIVITY_DATA,
            Layout.EF_VEHICLES_USED,
            Layout.EF_PLACES,
            Layout.EF_CURRENT_USAGE,
            Layout.EF_CONTROL_ACTIVITY_DATA,
            Layout.EF_SPECIFIC_CONDITIONS,
            Layout.EF_VEHICLE_UNITS_USED,
            Layout.EF_GNSS_PLACES,
            Layout.EF_APPLICATION_IDENTIFICATION_V2,
            Layout.EF_PLACES_AUTHENTICATION,
            Layout.EF_GNSS_PLACES_AUTHENTICATION,
            Layout.EF_BORDER_CROSSINGS,
            Layout.EF_LOAD_UNLOAD_OPERATIONS,
            Layout.EF_LOAD_TYPE_ENTRIES,
            Layout.EF_VU_CONFIGURATION,
        )

        /** Generation 1 driver card EF list. */
        val G1_EF_IDS = listOf(
            Layout.EF_APPLICATION_IDENTIFICATION,
            Layout.EF_CARD_CERTIFICATE,
            Layout.EF_CA_CERTIFICATE,
            Layout.EF_IDENTIFICATION,
            Layout.EF_CARD_DOWNLOAD,
            Layout.EF_DRIVING_LICENCE_INFO,
            Layout.EF_EVENTS_DATA,
            Layout.EF_FAULTS_DATA,
            Layout.EF_DRIVER_ACTIVITY_DATA,
            Layout.EF_VEHICLES_USED,
            Layout.EF_PLACES,
            Layout.EF_CURRENT_USAGE,
            Layout.EF_CONTROL_ACTIVITY_DATA,
            Layout.EF_SPECIFIC_CONDITIONS,
            Layout.EF_GNSS_PLACES,
        )
    }
}
