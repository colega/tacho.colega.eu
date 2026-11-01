package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.Apdu
import eu.colega.tacho.core.card.ApduResponse
import eu.colega.tacho.core.card.ApduTransceiver
import eu.colega.tacho.core.card.ByteReader
import eu.colega.tacho.core.card.CardStructureVersion

/**
 * Downloads every elementary file of a driver card application over an
 * [ApduTransceiver] and returns a [DriverCardDump].
 *
 * Download strategy (per TCS_148..TCS_155): the generation is known from the
 * selected AID, so EF Application_Identification is read with its exact size
 * first; its counters give the exact size of every further EF. READ BINARY
 * never asks beyond an EF's size (TCS_42 returns 6700/6Cxx for that), which
 * is what a fixed 255-byte chunking gets wrong on these cards.
 */
class CardDumpReader(
    private val transceiver: ApduTransceiver,
    private val chunkSize: Int = 255,
) {
    fun readDriverCard(): DriverCardDump {
        val generation = selectApplication()
        val isG1 = generation == CardStructureVersion.GENERATION_1
        val files = mutableMapOf<Int, ByteArray>()

        fun read(fid: Int, expectedSize: Int): ByteArray? = readFile(fid, expectedSize)

        // Application_Identification: 10 bytes in G1, 15 in G2 version 1 and
        // 17 in G2 version 2 - read the common part, extend for version 2.
        val appIdSize = if (isG1) Layout.APP_ID_G1 else 15
        var appIdBytes = read(Layout.EF_APPLICATION_IDENTIFICATION, appIdSize)
            ?: throw missing(Layout.EF_APPLICATION_IDENTIFICATION)
        // Byte 2 of cardStructureVersion: 0x00 = version 1, 0x01 = version 2.
        val versionByte = if (isG1) 0 else (appIdBytes[2].toInt() and 0xFF)
        val isV2 = !isG1 && versionByte >= 1
        if (isV2) {
            // The amended AppId adds noOfCardVehicleUnitRecords (2 bytes);
            // the EF is still selected, so read the extension in place.
            appIdBytes = appIdBytes + readRange(Layout.EF_APPLICATION_IDENTIFICATION, appIdSize, 2)
        }
        val appId = DriverCardParser.parseApplicationId(generation, appIdBytes)
        files[Layout.EF_APPLICATION_IDENTIFICATION] = appIdBytes

        // Certificates: fixed size in G1, DER-encoded variable size in G2.
        if (isG1) {
            files[Layout.EF_CARD_CERTIFICATE] = read(Layout.EF_CARD_CERTIFICATE, Layout.CERT_G1)
                ?: throw missing(Layout.EF_CARD_CERTIFICATE)
            files[Layout.EF_CA_CERTIFICATE] = read(Layout.EF_CA_CERTIFICATE, Layout.CERT_G1)
                ?: throw missing(Layout.EF_CA_CERTIFICATE)
        } else {
            for (fid in listOf(
                Layout.EF_CARD_MA_CERTIFICATE,
                Layout.EF_CARD_SIGN_CERTIFICATE,
                Layout.EF_CA_CERTIFICATE,
                Layout.EF_LINK_CERTIFICATE,
            )) {
                // A zero-filled certificate is not issued on this card;
                // it is left out of the dump.
                (readCertificate(fid) ?: continue).also { files[fid] = it }
            }
        }

        files[Layout.EF_IDENTIFICATION] = read(Layout.EF_IDENTIFICATION, Layout.IDENTIFICATION)
            ?: throw missing(Layout.EF_IDENTIFICATION)
        files[Layout.EF_CARD_DOWNLOAD] = read(Layout.EF_CARD_DOWNLOAD, Layout.CARD_DOWNLOAD)
            ?: throw missing(Layout.EF_CARD_DOWNLOAD)
        files[Layout.EF_DRIVING_LICENCE_INFO] = read(Layout.EF_DRIVING_LICENCE_INFO, Layout.DRIVING_LICENCE_INFO)
            ?: throw missing(Layout.EF_DRIVING_LICENCE_INFO)

        if (isG1) {
            files[Layout.EF_EVENTS_DATA] = read(Layout.EF_EVENTS_DATA, 6 * appId.noOfEventsPerType * Layout.EVENT_RECORD)
                ?: throw missing(Layout.EF_EVENTS_DATA)
            files[Layout.EF_FAULTS_DATA] = read(Layout.EF_FAULTS_DATA, 2 * appId.noOfFaultsPerType * Layout.FAULT_RECORD)
                ?: throw missing(Layout.EF_FAULTS_DATA)
            files[Layout.EF_DRIVER_ACTIVITY_DATA] =
                read(Layout.EF_DRIVER_ACTIVITY_DATA, 4 + appId.activityStructureLength)
                    ?: throw missing(Layout.EF_DRIVER_ACTIVITY_DATA)
            files[Layout.EF_VEHICLES_USED] =
                read(Layout.EF_VEHICLES_USED, 2 + appId.noOfCardVehicleRecords * Layout.VEHICLE_RECORD_G1)
                    ?: throw missing(Layout.EF_VEHICLES_USED)
            files[Layout.EF_PLACES] =
                read(Layout.EF_PLACES, 1 + appId.noOfCardPlaceRecords * Layout.PLACE_RECORD_G1)
                    ?: throw missing(Layout.EF_PLACES)
            files[Layout.EF_CURRENT_USAGE] = read(Layout.EF_CURRENT_USAGE, Layout.CURRENT_USAGE)
                ?: throw missing(Layout.EF_CURRENT_USAGE)
            files[Layout.EF_CONTROL_ACTIVITY_DATA] =
                read(Layout.EF_CONTROL_ACTIVITY_DATA, Layout.CONTROL_ACTIVITY_DATA)
                    ?: throw missing(Layout.EF_CONTROL_ACTIVITY_DATA)
            files[Layout.EF_SPECIFIC_CONDITIONS] =
                read(Layout.EF_SPECIFIC_CONDITIONS, Layout.SPECIFIC_CONDITIONS_G1_SIZE)
                    ?: throw missing(Layout.EF_SPECIFIC_CONDITIONS)
        } else {
            files[Layout.EF_EVENTS_DATA] =
                read(Layout.EF_EVENTS_DATA, 11 * appId.noOfEventsPerType * Layout.EVENT_RECORD)
                    ?: throw missing(Layout.EF_EVENTS_DATA)
            files[Layout.EF_FAULTS_DATA] =
                read(Layout.EF_FAULTS_DATA, 2 * appId.noOfFaultsPerType * Layout.FAULT_RECORD)
                    ?: throw missing(Layout.EF_FAULTS_DATA)
            files[Layout.EF_DRIVER_ACTIVITY_DATA] =
                read(Layout.EF_DRIVER_ACTIVITY_DATA, 4 + appId.activityStructureLength)
                    ?: throw missing(Layout.EF_DRIVER_ACTIVITY_DATA)
            files[Layout.EF_VEHICLES_USED] =
                read(Layout.EF_VEHICLES_USED, 2 + appId.noOfCardVehicleRecords * Layout.VEHICLE_RECORD)
                    ?: throw missing(Layout.EF_VEHICLES_USED)
            files[Layout.EF_PLACES] =
                read(Layout.EF_PLACES, 2 + appId.noOfCardPlaceRecords * Layout.PLACE_RECORD)
                    ?: throw missing(Layout.EF_PLACES)
            files[Layout.EF_CURRENT_USAGE] = read(Layout.EF_CURRENT_USAGE, Layout.CURRENT_USAGE)
                ?: throw missing(Layout.EF_CURRENT_USAGE)
            files[Layout.EF_CONTROL_ACTIVITY_DATA] =
                read(Layout.EF_CONTROL_ACTIVITY_DATA, Layout.CONTROL_ACTIVITY_DATA)
                    ?: throw missing(Layout.EF_CONTROL_ACTIVITY_DATA)
            files[Layout.EF_SPECIFIC_CONDITIONS] =
                read(Layout.EF_SPECIFIC_CONDITIONS, 2 + appId.noOfSpecificConditionRecords * Layout.SPECIFIC_CONDITION_RECORD)
                    ?: throw missing(Layout.EF_SPECIFIC_CONDITIONS)
            files[Layout.EF_VEHICLE_UNITS_USED] =
                read(Layout.EF_VEHICLE_UNITS_USED, 2 + appId.noOfCardVehicleUnitRecords * Layout.VEHICLE_UNIT_RECORD)
                    ?: throw missing(Layout.EF_VEHICLE_UNITS_USED)
            files[Layout.EF_GNSS_PLACES] =
                read(Layout.EF_GNSS_PLACES, 2 + appId.noOfGnssAdRecords * gnssRecordSize(appId))
                    ?: throw missing(Layout.EF_GNSS_PLACES)
            if (isV2) {
                val v2Bytes = read(Layout.EF_APPLICATION_IDENTIFICATION_V2, Layout.APP_ID_G2_V2)
                    ?: throw missing(Layout.EF_APPLICATION_IDENTIFICATION_V2)
                files[Layout.EF_APPLICATION_IDENTIFICATION_V2] = v2Bytes
                val v2 = G2Card.applicationIdentificationV2(ByteReader(v2Bytes))
                files[Layout.EF_PLACES_AUTHENTICATION] =
                    read(Layout.EF_PLACES_AUTHENTICATION, 2 + appId.noOfCardPlaceRecords * Layout.PLACE_AUTH_RECORD)
                        ?: throw missing(Layout.EF_PLACES_AUTHENTICATION)
                files[Layout.EF_GNSS_PLACES_AUTHENTICATION] =
                    read(Layout.EF_GNSS_PLACES_AUTHENTICATION, 2 + appId.noOfGnssAdRecords * Layout.GNSS_AUTH_RECORD)
                        ?: throw missing(Layout.EF_GNSS_PLACES_AUTHENTICATION)
                files[Layout.EF_BORDER_CROSSINGS] =
                    read(Layout.EF_BORDER_CROSSINGS, 2 + v2.noOfBorderCrossingRecords * Layout.BORDER_CROSSING_RECORD)
                        ?: throw missing(Layout.EF_BORDER_CROSSINGS)
                files[Layout.EF_LOAD_UNLOAD_OPERATIONS] =
                    read(Layout.EF_LOAD_UNLOAD_OPERATIONS, 2 + v2.noOfLoadUnloadRecords * Layout.LOAD_UNLOAD_RECORD)
                        ?: throw missing(Layout.EF_LOAD_UNLOAD_OPERATIONS)
                files[Layout.EF_LOAD_TYPE_ENTRIES] =
                    read(Layout.EF_LOAD_TYPE_ENTRIES, 2 + v2.noOfLoadTypeEntryRecords * Layout.LOAD_TYPE_RECORD)
                        ?: throw missing(Layout.EF_LOAD_TYPE_ENTRIES)
            }
            // EF VU_Configuration (0540) is readable only with secure
            // messaging (SC5) and is not part of a plain download.
        }
        return DriverCardDump(files)
    }

    /** GNSS record size: 18 bytes in the amended layout, 15 in version 1. */
    private fun gnssRecordSize(appId: ApplicationIdentification): Int {
        val count = appId.noOfGnssAdRecords ?: return Layout.GNSS_AD_RECORD_G2
        return if (appId.structureVersionByte >= 1) Layout.GNSS_AD_RECORD_G2 else Layout.GNSS_AD_RECORD_G1
    }

    private fun missing(fid: Int): IllegalStateException =
        IllegalStateException("EF %04X is missing from the card".format(fid))

    /** Select an EF; null when the file does not exist (6A82). */
    private fun selectEfid(fid: Int) {
        val select = ApduResponse.from(transceiver.transceive(Apdu.selectFile(fid)))
        if (select.statusWord == Apdu.SW_FILE_NOT_FOUND) throw AbsentFileException(fid)
        if (!select.isOk) {
            throw IllegalStateException("select of EF %04X failed (SW %04X)".format(fid, select.statusWord))
        }
    }

    /** Read [length] bytes at [offset] from the selected EF, honouring 6Cxx. */
    private fun readRange(fid: Int, offset: Int, length: Int): ByteArray {
        var le = length.coerceIn(1, chunkSize)
        var response = ApduResponse.from(transceiver.transceive(Apdu.readBinary(offset, le)))
        if (response.statusWord and 0xFF00 == 0x6C00) {
            // The card tells us the exact remaining length; re-issue with it.
            le = response.statusWord and 0xFF
            response = ApduResponse.from(transceiver.transceive(Apdu.readBinary(offset, le)))
        }
        if (!response.isOk) {
            throw IllegalStateException(
                "read of EF %04X at offset %d failed (SW %04X)".format(fid, offset, response.statusWord)
            )
        }
        return response.data
    }

    private fun readFile(fid: Int, expectedSize: Int): ByteArray? {
        return try {
            selectEfid(fid)
            readExact(fid, expectedSize)
        } catch (absent: AbsentFileException) {
            null
        }
    }

    /** Read an EF of a known size in chunk-size pieces. */
    private fun readExact(fid: Int, size: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(size)
        var offset = 0
        while (offset < size) {
            val chunk = readRange(fid, offset, minOf(chunkSize, size - offset))
            out.write(chunk)
            offset += chunk.size
        }
        if (out.size() != size) {
            throw IllegalStateException("EF %04X delivered %d bytes, expected %d".format(fid, out.size(), size))
        }
        return out.toByteArray()
    }

    /**
     * Certificates are variable-size TLV structures (the outer wrapper is
     * the ISO 7816-4 certificate template 7F21; older structures use a
     * plain 0x30 SEQUENCE). Read the header, derive the total size and read
     * the rest in chunks. A zero-filled EF means the certificate is not
     * issued on this card (the real card does not disclose sizes any other
     * way: over-reads answer 6700 without a length), so return null.
     */
    private fun readCertificate(fid: Int): ByteArray? {
        selectEfid(fid)
        val head = readRange(fid, 0, 5)
        if (head.all { it.toInt() == 0 }) return null
        val (lengthPosition, tagLength) = when (head[0].toInt() and 0xFF) {
            0x7F -> {
                require(head[1].toInt() and 0xFF == 0x21) {
                    "EF %04X does not start with a certificate template".format(fid)
                }
                2 to 2
            }
            0x30 -> 1 to 1
            else -> throw IllegalStateException("EF %04X does not start with a certificate".format(fid))
        }
        val firstLengthByte = head[lengthPosition].toInt() and 0xFF
        val (headerSize, contentSize) = when {
            firstLengthByte < 0x80 -> tagLength + 1 to firstLengthByte
            firstLengthByte == 0x81 -> tagLength + 2 to (head[lengthPosition + 1].toInt() and 0xFF)
            firstLengthByte == 0x82 ->
                tagLength + 3 to ((head[lengthPosition + 1].toInt() and 0xFF) shl 8 or (head[lengthPosition + 2].toInt() and 0xFF))
            else -> throw IllegalStateException("EF %04X uses an unsupported certificate length form".format(fid))
        }
        val total = headerSize + contentSize
        val out = java.io.ByteArrayOutputStream(total)
        out.write(head, 0, minOf(head.size, total))
        var offset = minOf(head.size, total)
        while (offset < total) {
            val chunk = readRange(fid, offset, minOf(chunkSize, total - offset))
            out.write(chunk)
            offset += chunk.size
        }
        if (out.size() != total) {
            throw IllegalStateException("EF %04X delivered %d bytes, expected %d".format(fid, out.size(), total))
        }
        return out.toByteArray()
    }

    /** A file the download knows should exist but the card does not expose. */
    class AbsentFileException(val fid: Int) : IllegalStateException("EF %04X not found on the card".format(fid))

    /** Select the application by AID and report its generation. */
    private fun selectApplication(): CardStructureVersion {
        val g2 = ApduResponse.from(transceiver.transceive(Apdu.selectApplication(Apdu.AID_G2)))
        if (g2.isOk) return CardStructureVersion.GENERATION_2
        val g1 = ApduResponse.from(transceiver.transceive(Apdu.selectApplication(Apdu.AID_G1)))
        if (g1.isOk) return CardStructureVersion.GENERATION_1
        throw IllegalStateException("no tachograph application found on card")
    }

    companion object {
        /**
         * EFs of the amended generation 2 driver card, minus VU_Configuration
         * (0540): that file is readable only with secure messaging (SC5).
         */
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
        )

        /** Generation 1 driver card EF list (no 0523/0524/0525+ on G1). */
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
        )
    }
}
