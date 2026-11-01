package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.Apdu
import eu.colega.tacho.core.card.ApduResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Drives the download against a scripted in-memory card: SELECT by AID,
 * SELECT file, exact-size READ BINARY with 6Cxx handling, and the
 * AppId-driven size computation for both generations.
 */
class CardDumpReaderTest {

    /** In-memory card speaking enough of ISO 7816 for the download flow. */
    private class ScriptedCard : (ByteArray) -> ByteArray {
        val files = mutableMapOf<Int, ByteArray>()
        val selectedFids = mutableListOf<Int>()
        var rejectAid = false
        var secureFile: Int? = null

        override fun invoke(command: ByteArray): ByteArray {
            return when (command[1].toInt() and 0xFF) {
                Apdu.INS_SELECT -> handleSelect(command)
                Apdu.INS_READ_BINARY -> handleRead(command)
                else -> error("unexpected INS ${command[1]}")
            }
        }

        private fun handleSelect(command: ByteArray): ByteArray {
            val p1 = command[2].toInt() and 0xFF
            return if (p1 == 0x04) {
                selectedFids.clear()
                selectedFile = null
                if (rejectAid || !aidMatchesGeneration(command)) status(Apdu.SW_FILE_NOT_FOUND) else ok()
            } else {
                val fid = ((command[5].toInt() and 0xFF) shl 8) or (command[6].toInt() and 0xFF)
                selectedFids += fid
                if (files.containsKey(fid)) {
                    selectedFile = fid
                    ok()
                } else {
                    selectedFile = null
                    status(Apdu.SW_FILE_NOT_FOUND)
                }
            }
        }

        private fun handleRead(command: ByteArray): ByteArray {
            val fid = selectedFile ?: return status(Apdu.SW_NO_EF_SELECTED)
            val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
            val requested = command[4].toInt() and 0xFF
            if (secureFile == fid) return status(Apdu.SW_SECURITY_NOT_SATISFIED)
            val data = files.getValue(fid)
            if (offset >= data.size) {
                println("READ %04X off=%d le=%d -> 6B00 (size=%d)".format(fid, offset, requested, data.size))
                return status(Apdu.SW_EOF_REACHED)
            }
            val end = minOf(offset + requested, data.size)
            return data.copyOfRange(offset, end) + status(Apdu.SW_OK)
        }

        private var selectedFile: Int? = null

        /** A real card answers only the AID matching its own generation. */
        private fun aidMatchesGeneration(command: ByteArray): Boolean {
            val lc = command[4].toInt() and 0xFF
            val aid = command.copyOfRange(5, 5 + lc)
            val appId = files[Layout.EF_APPLICATION_IDENTIFICATION] ?: return false
            val isG2 = aid.contentEquals(Apdu.AID_G2)
            val isG1 = aid.contentEquals(Apdu.AID_G1)
            return if (appId.size > 1 && (appId[1].toInt() and 0xFF) == 0x01) isG2 else isG1
        }


        private fun ok() = status(Apdu.SW_OK)
        private fun status(sw: Int) = byteArrayOf(((sw shr 8) and 0xFF).toByte(), (sw and 0xFF).toByte())
    }

    private fun sampleFile(size: Int, seed: Int) = ByteArray(size) { ((it + seed) and 0xFF).toByte() }

    /**
     * Certificate of an exact total size wrapped in the ISO 7816-4
     * certificate template tag 7F21, as real cards use. Sizes up to 255
     * bytes of content use the 81 length form, larger ones the 82 form.
     */
    private fun derCert(size: Int, seed: Int): ByteArray {
        val content = if (size - 4 < 256) size - 4 else size - 5
        val out = ByteArray(size)
        out[0] = 0x7F.toByte()
        out[1] = 0x21.toByte()
        if (content < 256) {
            out[2] = 0x81.toByte()
            out[3] = content.toByte()
        } else {
            out[2] = 0x82.toByte()
            out[3] = ((content shr 8) and 0xFF).toByte()
            out[4] = (content and 0xFF).toByte()
        }
        sampleFile(content, seed).copyInto(out, size - content)
        return out
    }

    /**
     * Amended G2v2 driver card AppId (17 bytes): driver card, generation 2,
     * version 2, n1=12, n2=24, activity 13776, n3=200, n4=112, gnss=336,
     * specific=112, vu=200.
     */
    private fun g2v2AppId() = byteArrayOf(
        0x00, 0x01, 0x01, 0x0C, 0x18,
        0x35, 0xD0.toByte(), // activityStructureLength 13776
        0x00, 0xC8.toByte(), // noOfCardVehicleRecords 200
        0x00, 0x70, // noOfCardPlaceRecords 112
        0x01, 0x50, // noOfGnssAdRecords 336
        0x00, 0x70, // noOfSpecificConditionRecords 112
        0x00, 0xC8.toByte(), // noOfCardVehicleUnitRecords 200
    )

    /** G2 version 1 AppId (15 bytes): same but without the VU counter. */
    private fun g2v1AppId() = byteArrayOf(
        0x00, 0x01, 0x00, 0x06, 0x0C,
        0x15, 0xA8.toByte(), // activityStructureLength 5544
        0x00, 0x54, // noOfCardVehicleRecords 84
        0x00, 0x54, // noOfCardPlaceRecords 84
        0x00, 0xFC.toByte(), // noOfGnssAdRecords 252
        0x00, 0x38, // noOfSpecificConditionRecords 56
    )

    /** G1 AppId (10 bytes): 1-byte place count, no GNSS/VU counters. */
    private fun g1AppId() = byteArrayOf(
        0x00, 0x00, 0x00, 0x06, 0x0C,
        0x15, 0xA8.toByte(), // activityStructureLength 5544
        0x00, 0x54, // noOfCardVehicleRecords 84
        0x54, // noOfCardPlaceRecords 84 (single byte in G1)
    )

    private fun populateG2v2(card: ScriptedCard) {
        card.files[Layout.EF_APPLICATION_IDENTIFICATION] = g2v2AppId()
        card.files[Layout.EF_CARD_MA_CERTIFICATE] = derCert(205, 1)
        card.files[Layout.EF_CARD_SIGN_CERTIFICATE] = derCert(229, 2)
        card.files[Layout.EF_CA_CERTIFICATE] = derCert(271, 3)
        card.files[Layout.EF_LINK_CERTIFICATE] = derCert(341, 4)
        card.files[Layout.EF_IDENTIFICATION] = sampleFile(143, 5)
        card.files[Layout.EF_CARD_DOWNLOAD] = sampleFile(4, 6)
        card.files[Layout.EF_DRIVING_LICENCE_INFO] = sampleFile(53, 7)
        card.files[Layout.EF_EVENTS_DATA] = sampleFile(3168, 8)
        card.files[Layout.EF_FAULTS_DATA] = sampleFile(1152, 9)
        card.files[Layout.EF_DRIVER_ACTIVITY_DATA] = sampleFile(13780, 10)
        card.files[Layout.EF_VEHICLES_USED] = sampleFile(9602, 11)
        card.files[Layout.EF_PLACES] = sampleFile(2354, 12)
        card.files[Layout.EF_CURRENT_USAGE] = sampleFile(19, 13)
        card.files[Layout.EF_CONTROL_ACTIVITY_DATA] = sampleFile(46, 14)
        card.files[Layout.EF_SPECIFIC_CONDITIONS] = sampleFile(562, 15)
        card.files[Layout.EF_VEHICLE_UNITS_USED] = sampleFile(2002, 16)
        card.files[Layout.EF_GNSS_PLACES] = sampleFile(6050, 17)
        card.files[Layout.EF_APPLICATION_IDENTIFICATION_V2] = byteArrayOf(
            0x00, 0x08, // lengthOfFollowingData
            0x00, 0x3C, // noOfBorderCrossingRecords 60
            0x00, 0x1E, // noOfLoadUnloadRecords 30
            0x00, 0x1E, // noOfLoadTypeEntryRecords 30
            0x0C, 0x00, // vuConfigurationLengthRange 3072
        )
        card.files[Layout.EF_PLACES_AUTHENTICATION] = sampleFile(562, 18)
        card.files[Layout.EF_GNSS_PLACES_AUTHENTICATION] = sampleFile(1682, 19)
        card.files[Layout.EF_BORDER_CROSSINGS] = sampleFile(2 + 60 * 17, 20)
        card.files[Layout.EF_LOAD_UNLOAD_OPERATIONS] = sampleFile(2 + 30 * 20, 21)
        card.files[Layout.EF_LOAD_TYPE_ENTRIES] = sampleFile(2 + 30 * 5, 22)
        // EF VU_Configuration (0540) is deliberately absent: the download
        // never reads it (secure messaging only).
    }

    @Test
    fun `downloads every file of a version 2 card at exact sizes`() {
        val card = ScriptedCard()
        populateG2v2(card)
        val dump = CardDumpReader(card).readDriverCard()

        val expected = listOf(
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
        assertEquals(expected.sorted(), dump.fileIds)
        assertEquals(expected.size, dump.fileIds.size)
        for (fid in expected) {
            assertEquals(
                "size of EF %04X".format(fid),
                card.files[fid]!!.size,
                dump.file(fid)!!.size,
            )
        }
        // Every read was in-bounds: no file was ever requested past its end.
        assertNull(dump.file(Layout.EF_VU_CONFIGURATION))
    }

    @Test
    fun `downloads a version 1 card without touching the version 2 files`() {
        val card = ScriptedCard().apply {
            files[Layout.EF_APPLICATION_IDENTIFICATION] = g2v1AppId()
            files[Layout.EF_CARD_MA_CERTIFICATE] = derCert(204, 1)
            files[Layout.EF_CARD_SIGN_CERTIFICATE] = derCert(229, 2)
            files[Layout.EF_CA_CERTIFICATE] = derCert(271, 3)
            files[Layout.EF_LINK_CERTIFICATE] = derCert(341, 4)
            files[Layout.EF_IDENTIFICATION] = sampleFile(143, 5)
            files[Layout.EF_CARD_DOWNLOAD] = sampleFile(4, 6)
            files[Layout.EF_DRIVING_LICENCE_INFO] = sampleFile(53, 7)
            files[Layout.EF_EVENTS_DATA] = sampleFile(11 * 6 * 24, 8)
            files[Layout.EF_FAULTS_DATA] = sampleFile(2 * 12 * 24, 9)
            files[Layout.EF_DRIVER_ACTIVITY_DATA] = sampleFile(4 + 5544, 10)
            files[Layout.EF_VEHICLES_USED] = sampleFile(2 + 84 * 48, 11)
            files[Layout.EF_PLACES] = sampleFile(2 + 84 * 21, 12)
            files[Layout.EF_CURRENT_USAGE] = sampleFile(19, 13)
            files[Layout.EF_CONTROL_ACTIVITY_DATA] = sampleFile(46, 14)
            files[Layout.EF_SPECIFIC_CONDITIONS] = sampleFile(2 + 56 * 5, 15)
            files[Layout.EF_VEHICLE_UNITS_USED] = sampleFile(2 + 84 * 10, 16)
            files[Layout.EF_GNSS_PLACES] = sampleFile(2 + 252 * 15, 17)
        }
        val dump = CardDumpReader(card).readDriverCard()

        assertEquals(2 + 84 * 48, dump.file(Layout.EF_VEHICLES_USED)!!.size)
        assertEquals(2 + 252 * 15, dump.file(Layout.EF_GNSS_PLACES)!!.size)
        // The V2-only files were never even selected.
        val selected = card.selectedFids
        assertTrue(selected.none { it in 0x0525..0x0540 })
    }

    @Test
    fun `downloads a generation 1 card with G1 record sizes`() {
        val card = ScriptedCard().apply {
            files[Layout.EF_APPLICATION_IDENTIFICATION] = g1AppId()
            files[Layout.EF_CARD_CERTIFICATE] = sampleFile(194, 1)
            files[Layout.EF_CA_CERTIFICATE] = sampleFile(194, 2)
            files[Layout.EF_IDENTIFICATION] = sampleFile(143, 3)
            files[Layout.EF_CARD_DOWNLOAD] = sampleFile(4, 4)
            files[Layout.EF_DRIVING_LICENCE_INFO] = sampleFile(53, 5)
            files[Layout.EF_EVENTS_DATA] = sampleFile(6 * 6 * 24, 6)
            files[Layout.EF_FAULTS_DATA] = sampleFile(2 * 12 * 24, 7)
            files[Layout.EF_DRIVER_ACTIVITY_DATA] = sampleFile(4 + 5544, 8)
            files[Layout.EF_VEHICLES_USED] = sampleFile(2 + 84 * 31, 9)
            files[Layout.EF_PLACES] = sampleFile(1 + 84 * 10, 10)
            files[Layout.EF_CURRENT_USAGE] = sampleFile(19, 11)
            files[Layout.EF_CONTROL_ACTIVITY_DATA] = sampleFile(46, 12)
            files[Layout.EF_SPECIFIC_CONDITIONS] = sampleFile(280, 13)
        }
        val dump = CardDumpReader(card).readDriverCard()

        assertEquals(2 + 84 * 31, dump.file(Layout.EF_VEHICLES_USED)!!.size)
        assertEquals(1 + 84 * 10, dump.file(Layout.EF_PLACES)!!.size)
        assertEquals(6 * 6 * 24, dump.file(Layout.EF_EVENTS_DATA)!!.size)
        assertEquals(280, dump.file(Layout.EF_SPECIFIC_CONDITIONS)!!.size)
        // G1 has no V2 files, no vehicle units and no GNSS places.
        assertTrue(card.selectedFids.none { it == Layout.EF_VEHICLE_UNITS_USED || it == Layout.EF_GNSS_PLACES })
        assertTrue(card.selectedFids.none { it in 0x0525..0x0540 })
    }

    @Test
    fun `a security failure on a core file aborts with the status word`() {
        val card = ScriptedCard().apply {
            files[Layout.EF_APPLICATION_IDENTIFICATION] = g2v2AppId()
            files[Layout.EF_CARD_MA_CERTIFICATE] = derCert(204, 1)
            files[Layout.EF_CARD_SIGN_CERTIFICATE] = derCert(229, 2)
            files[Layout.EF_CA_CERTIFICATE] = derCert(271, 3)
            files[Layout.EF_LINK_CERTIFICATE] = derCert(341, 4)
            files[Layout.EF_IDENTIFICATION] = sampleFile(143, 5)
            secureFile = Layout.EF_IDENTIFICATION
        }
        val exception = runCatching { CardDumpReader(card).readDriverCard() }.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
        assertTrue(exception!!.message!!.contains("0520"))
        assertTrue(exception.message!!.contains("6982"))
    }

    @Test
    fun `a zero-filled link certificate is skipped`() {
        val card = ScriptedCard()
        populateG2v2(card)
        card.files[Layout.EF_LINK_CERTIFICATE] = ByteArray(205) // not issued on this card
        val dump = CardDumpReader(card).readDriverCard()
        assertEquals(false, dump.has(Layout.EF_LINK_CERTIFICATE))
    }

    @Test
    fun `reports every elementary file as the download reaches it`() {
        val card = ScriptedCard()
        populateG2v2(card)
        val seen = mutableListOf<Int>()
        CardDumpReader(card, onFileStart = { fid -> seen += fid }).readDriverCard()
        assertEquals(Layout.EF_APPLICATION_IDENTIFICATION, seen.first())
        assertTrue(seen.contains(Layout.EF_IDENTIFICATION))
        assertTrue(seen.contains(Layout.EF_EVENTS_DATA))
        assertEquals(Layout.EF_LOAD_TYPE_ENTRIES, seen.last())
        // The secure-messaging-only file is never part of the download.
        assertTrue(seen.none { it == Layout.EF_VU_CONFIGURATION })
    }

    @Test
    fun `no tachograph application on the card fails loudly`() {
        val card = ScriptedCard().apply { rejectAid = true }
        val exception = runCatching { CardDumpReader(card).readDriverCard() }.exceptionOrNull()
        assertTrue(exception is IllegalStateException)
    }

    @Test
    fun `certificates of different DER sizes are read completely`() {
        val card = ScriptedCard()
        populateG2v2(card)
        val dump = CardDumpReader(card).readDriverCard()
        // The four G2 certificates were built with different lengths and
        // both long-form length encodings.
        assertEquals(205, dump.file(Layout.EF_CARD_MA_CERTIFICATE)!!.size)
        assertEquals(229, dump.file(Layout.EF_CARD_SIGN_CERTIFICATE)!!.size)
        assertEquals(271, dump.file(Layout.EF_CA_CERTIFICATE)!!.size)
        assertEquals(341, dump.file(Layout.EF_LINK_CERTIFICATE)!!.size)
        assertEquals(0x7F, dump.file(Layout.EF_CARD_MA_CERTIFICATE)!![0].toInt())
    }
}
