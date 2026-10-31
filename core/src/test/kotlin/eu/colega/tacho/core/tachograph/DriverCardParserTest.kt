package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.card.EquipmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Builds synthetic card dumps following the amended (2021/1228) generation 2
 * layout and checks that the parser reproduces every field.
 */
class DriverCardParserTest {

    // ---- dump builders ----------------------------------------------------

    private fun g2ApplicationId(): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(0x00) // typeOfTachographCardId: driver card
        b.write(0x01); b.write(0x01) // cardStructureVersion {01 01} = G2 version 2
        b.write(12) // noOfEventsPerType
        b.write(24) // noOfFaultsPerType
        b.write(0x35); b.write(0xD0) // activityStructureLength 13776
        b.write(0x00); b.write(200) // noOfCardVehicleRecords
        b.write(0x00); b.write(112) // noOfCardPlaceRecords
        b.write(0x01); b.write(0x50) // noOfGNSSADRecords 336
        b.write(0x00); b.write(112) // noOfSpecificConditionRecords
        b.write(0x00); b.write(200) // noOfCardVehicleUnitRecords
        assertEquals(Layout.APP_ID_G2, b.size())
        return b.toByteArray()
    }

    private fun identification(): ByteArray {
        val b = ByteArrayOutputStream()
        // CardIdentification 65 bytes
        b.write(0x01) // cardIssuingMemberState: Austria
        val cardNumber = "D12345678901234".toByteArray(Charsets.US_ASCII)
        writePadded(b, cardNumber, 16)
        writePadded(b, "MEIER AUTHORITY".toByteArray(Charsets.US_ASCII), 36)
        writeDate(b, 25, 5, 10) // issue 2025-05-10
        writeDate(b, 25, 5, 12) // valid from 2025-05-12
        writeDate(b, 30, 5, 11) // expiry 2030-05-11
        assertEquals(65, b.size())
        // DriverCardHolderIdentification 78 bytes
        writePadded(b, "MEIER".toByteArray(Charsets.US_ASCII), 36)
        writePadded(b, "HANS PETER".toByteArray(Charsets.US_ASCII), 36)
        writeDate(b, 80, 1, 15)
        writePadded(b, "de".toByteArray(Charsets.US_ASCII), 2)
        assertEquals(143, b.size())
        return b.toByteArray()
    }

    private fun eventRecord(type: Int, begin: Long, end: Long, nation: Int, vrn: String): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(type)
        writeU32(b, begin)
        writeU32(b, end)
        b.write(nation)
        writePadded(b, vrn.toByteArray(Charsets.US_ASCII), 14)
        assertEquals(Layout.EVENT_RECORD, b.size())
        return b.toByteArray()
    }

    private fun activityEfs(): ByteArray {
        val b = ByteArrayOutputStream()
        // pointers: oldest/newest as record indices (0-based)
        b.write(0x00); b.write(0x00)
        b.write(0x00); b.write(0x01)
        // daily record: prevLength(0, oldest), recordLength, date, presence, distance, changes
        val changes = byteArrayOf(
            // 00:00 break/rest: s=0 driver, c=0 single, p=0 inserted, aa=00, t=0
            0x00, 0x00,
            // 08:00 driving (aa=11): word = 0x1800 or 480 = 0x19E0
            0x19, 0xE0.toByte(),
        )
        val recordLength = 12 + changes.size
        writeU16(b, 0) // previousRecordLength
        writeU16(b, recordLength)
        writeU32(b, 0x6795C000) // activityRecordDate (2025-01-26)
        writeU16(b, 1) // dailyPresenceCounter
        writeU16(b, 123) // dayDistance
        b.write(changes)
        return b.toByteArray()
    }

    private fun vehicleRecord(): ByteArray {
        val b = ByteArrayOutputStream()
        writeU24(b, 1000) // odometerBegin
        writeU24(b, 2500) // odometerEnd
        writeU32(b, 0x6795C000) // firstUse
        writeU32(b, 0x67A40000) // lastUse
        b.write(0x01) // nation: Austria
        writePadded(b, "B-AB 1234".toByteArray(Charsets.US_ASCII), 14)
        writeU16(b, 7) // vuDataBlockCounter
        writePadded(b, "WVWZZZ1KZAW123456".toByteArray(Charsets.US_ASCII), 17)
        assertEquals(Layout.VEHICLE_RECORD, b.size())
        return b.toByteArray()
    }

    // ---- tests ------------------------------------------------------------

    @Test
    fun `parses amended generation 2 application identification`() {
        val dump = DriverCardDump.of(
            Layout.EF_APPLICATION_IDENTIFICATION to g2ApplicationId(),
        )
        val card = DriverCardParser.parse(dump)
        assertEquals(CardStructureVersion.GENERATION_2, card.generation)
        assertEquals(EquipmentType.DRIVER, card.applicationId.typeOfTachographCardId)
        assertEquals(12, card.applicationId.noOfEventsPerType)
        assertEquals(24, card.applicationId.noOfFaultsPerType)
        assertEquals(13776, card.applicationId.activityStructureLength)
        assertEquals(200, card.applicationId.noOfCardVehicleRecords)
        assertEquals(112, card.applicationId.noOfCardPlaceRecords)
        assertEquals(336, card.applicationId.noOfGnssAdRecords)
        assertEquals(112, card.applicationId.noOfSpecificConditionRecords)
        assertEquals(200, card.applicationId.noOfCardVehicleUnitRecords)
    }

    @Test
    fun `parses identification and holder`() {
        val dump = DriverCardDump.of(
            Layout.EF_APPLICATION_IDENTIFICATION to g2ApplicationId(),
            Layout.EF_IDENTIFICATION to identification(),
        )
        val card = DriverCardParser.parse(dump)
        assertEquals("D12345678901234", card.identification.cardNumber)
        assertEquals("MEIER AUTHORITY", card.identification.cardIssuingAuthorityName)
        assertEquals("MEIER", card.holder.name.surname)
        assertEquals("HANS PETER", card.holder.name.firstNames)
        assertEquals("de", card.holder.preferredLanguage)
    }

    @Test
    fun `parses event records from fixed slots`() {
        val events = buildList {
            add(eventRecord(0x07, 1000, 2000, 0x01, "B-XY 999")) // over speeding
            repeat(4) { add(eventRecord(0x00, 0, 0, 0x00, "")) }
        }.reduce { acc, bytes -> acc + bytes }
        val dump = DriverCardDump.of(
            Layout.EF_APPLICATION_IDENTIFICATION to g2ApplicationId(),
            Layout.EF_EVENTS_DATA to events,
        )
        val card = DriverCardParser.parse(dump)
        assertEquals(5, card.events.size)
        val speeding = card.events.first()
        assertTrue(speeding.isOpen.not())
        assertEquals("B-XY 999", speeding.vehicle.number)
    }

    @Test
    fun `parses daily activity with change records`() {
        val dump = DriverCardDump.of(
            Layout.EF_APPLICATION_IDENTIFICATION to g2ApplicationId(),
            Layout.EF_DRIVER_ACTIVITY_DATA to activityEfs(),
        )
        val card = DriverCardParser.parse(dump)
        assertEquals(1, card.activity.size)
        val day = card.activity.first()
        assertEquals(2, day.activityChanges.size)
        assertEquals(Activity.BREAK_OR_REST, day.activityChanges[0].activity)
        assertEquals(Activity.DRIVING, day.activityChanges[1].activity)
        assertEquals(480, day.activityChanges[1].minutesOfDay)
        assertEquals(123, day.dayDistance)
    }

    @Test
    fun `parses vehicle records`() {
        val dump = DriverCardDump.of(
            Layout.EF_APPLICATION_IDENTIFICATION to g2ApplicationId(),
            Layout.EF_VEHICLES_USED to (byteArrayOf(0x00, 0x00) + vehicleRecord()),
        )
        val card = DriverCardParser.parse(dump)
        assertEquals(1, card.vehicles.size)
        val vehicle = card.vehicles.first()
        assertEquals(1500, vehicle.odometerDriven)
        assertEquals("B-AB 1234", vehicle.vehicle.number)
        assertEquals("WVWZZZ1KZAW123456", vehicle.vin)
    }

    // ---- helpers ----------------------------------------------------------

    private fun writeU16(b: ByteArrayOutputStream, v: Int) {
        b.write((v shr 8) and 0xFF)
        b.write(v and 0xFF)
    }

    private fun writeU32(b: ByteArrayOutputStream, v: Long) {
        b.write(((v shr 24) and 0xFF).toInt())
        b.write(((v shr 16) and 0xFF).toInt())
        b.write(((v shr 8) and 0xFF).toInt())
        b.write((v and 0xFF).toInt())
    }

    private fun writeU24(b: ByteArrayOutputStream, v: Int) {
        b.write((v shr 16) and 0xFF)
        b.write((v shr 8) and 0xFF)
        b.write(v and 0xFF)
    }

    /** 4-byte BCD date: 2-byte year (4 digits), month, day. */
    private fun writeDate(b: ByteArrayOutputStream, yyyy: Int, mm: Int, dd: Int) {
        b.write(((yyyy / 1000) shl 4) or ((yyyy / 100) % 10))
        b.write((((yyyy / 10) % 10) shl 4) or (yyyy % 10))
        b.write(((mm / 10) shl 4) or (mm % 10))
        b.write(((dd / 10) shl 4) or (dd % 10))
    }

    private fun writePadded(b: ByteArrayOutputStream, data: ByteArray, size: Int) {
        require(data.size <= size)
        b.write(data)
        repeat(size - data.size) { b.write(0x20) }
    }
}
