package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.ByteReader
import eu.colega.tacho.core.card.EquipmentType
import eu.colega.tacho.core.card.Nation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Byte-level tests for the amended (2021/1228) generation 2 structures:
 * the 17-byte Application_Identification, the Application_Identification_V2
 * and every new V2 elementary file record layout.
 */
class G2CardTest {

    // ---- synthetic record builders ---------------------------------------

    private fun timeReal(epochSeconds: Long): ByteArray = byteArrayOf(
        ((epochSeconds shr 24) and 0xFF).toByte(),
        ((epochSeconds shr 16) and 0xFF).toByte(),
        ((epochSeconds shr 8) and 0xFF).toByte(),
        (epochSeconds and 0xFF).toByte(),
    )

    /** GNSS place without odometer: ts(4) + accuracy(1) + lat(3) + lon(3) = 11 bytes. */
    private fun gnssPlace(epochSeconds: Long, accuracy: Int, lat: Long, lon: Long): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(timeReal(epochSeconds))
        b.write(accuracy)
        b.write24(lat)
        b.write24(lon)
        return b.toByteArray()
    }

    /** GNSS place with odometer (G2v2): 11 bytes + odometer(3). */
    private fun gnssPlaceWithOdometer(epochSeconds: Long, accuracy: Int, lat: Long, lon: Long, odometer: Long): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(gnssPlace(epochSeconds, accuracy, lat, lon))
        b.write24(odometer)
        return b.toByteArray()
    }

    /** 24-bit big-endian, two's complement for negatives. */
    private fun ByteArrayOutputStream.write24(value: Long) {
        val v = value and 0xFFFFFF
        write(((v shr 16) and 0xFF).toInt())
        write(((v shr 8) and 0xFF).toInt())
        write((v and 0xFF).toInt())
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun u16be(value: Int) = byteArrayOf(
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    // ---- Application_Identification (17 bytes) ----------------------------

    @Test
    fun `parses amended 17-byte application identification`() {
        val b = ByteArrayOutputStream()
        b.write(0x00) // driver card
        b.write(0x01); b.write(0x01) // generation 2, version 2
        b.write(12) // noOfEventsPerType
        b.write(24) // noOfFaultsPerType
        b.writeU16(0x35D0) // activityStructureLength 13776
        b.writeU16(200) // noOfCardVehicleRecords
        b.writeU16(112) // noOfCardPlaceRecords
        b.writeU16(336) // noOfGNSSADRecords
        b.writeU16(112) // noOfSpecificConditionRecords
        b.writeU16(200) // noOfCardVehicleUnitRecords (added by the amendment)
        assertEquals(Layout.APP_ID_G2, b.size())

        val appId = G2Card.applicationIdentification(ByteReader(b.toByteArray()), includeVehicleUnitRecords = true)
        assertEquals(EquipmentType.DRIVER, appId.typeOfTachographCardId)
        assertEquals(1, appId.structureVersionByte) // 0x01 = version 2
        assertEquals(12, appId.noOfEventsPerType)
        assertEquals(24, appId.noOfFaultsPerType)
        assertEquals(13776, appId.activityStructureLength)
        assertEquals(200, appId.noOfCardVehicleRecords)
        assertEquals(112, appId.noOfCardPlaceRecords)
        assertEquals(336, appId.noOfGnssAdRecords)
        assertEquals(112, appId.noOfSpecificConditionRecords)
        assertEquals(200, appId.noOfCardVehicleUnitRecords)
    }

    // ---- Application_Identification_V2 (10 bytes) -------------------------

    @Test
    fun `parses application identification v2 file`() {
        val b = ByteArrayOutputStream()
        b.writeU16(8) // lengthOfFollowingData
        b.writeU16(60) // noOfBorderCrossingRecords
        b.writeU16(30) // noOfLoadUnloadRecords
        b.writeU16(30) // noOfLoadTypeEntryRecords
        b.writeU16(40) // vuConfigurationLength min
        assertEquals(10, b.size())

        val v2 = G2Card.applicationIdentificationV2(ByteReader(b.toByteArray()))
        assertEquals(8, v2.lengthOfFollowingData)
        assertEquals(60, v2.noOfBorderCrossingRecords)
        assertEquals(30, v2.noOfLoadUnloadRecords)
        assertEquals(30, v2.noOfLoadTypeEntryRecords)
        assertEquals(40, v2.vuConfigurationLengthRange)
    }

    // ---- GNSS accumulated driving (18 bytes) ------------------------------

    @Test
    fun `parses gnss accumulated driving record with odometer`() {
        // Madrid: 40°25.0' N -> 40*1000 + 25*10 = 40250; 3°42.0' W -> -3420.
        val place = gnssPlaceWithOdometer(1_700_000_000, 25, 40_250, -3_420, 123456)
        val record = timeReal(1_700_000_000) + place
        assertEquals(Layout.GNSS_AD_RECORD, record.size)

        val parsed = G2Card.gnssAdRecord(ByteReader(record))
        assertEquals(1_700_000_000, parsed.timestamp.seconds)
        assertEquals(40.0 + 25.0 / 60.0, parsed.place.latitudeDeg, 1e-9)
        assertEquals(-(3.0 + 42.0 / 60.0), parsed.place.longitudeDeg, 1e-9)
        assertEquals(2.5, parsed.place.accuracy.meters, 1e-9)
        assertEquals(123456L, parsed.place.odometer)
    }

    // ---- Border crossing (17 bytes) ---------------------------------------

    @Test
    fun `parses border crossing record`() {
        val b = ByteArrayOutputStream()
        b.write(Nation.SPAIN.code) // country left
        b.write(Nation.FRANCE.code) // country entered
        // Border of France: 42°39.5' N, 0°42.0' E -> 42_395, 420.
        b.write(gnssPlace(1_700_000_000, 8, 42_395, 420))
        b.write(0x01) // authentication verified
        b.write24(98_765) // odometer
        assertEquals(Layout.BORDER_CROSSING_RECORD, b.size())

        val parsed = G2Card.borderCrossingRecord(ByteReader(b.toByteArray()))
        assertEquals(Nation.SPAIN, parsed.countryLeft)
        assertEquals(Nation.FRANCE, parsed.countryEntered)
        assertEquals(42.0 + 39.5 / 60.0, parsed.gnssPlaceAuth.latitudeDeg, 1e-9)
        assertNull(parsed.gnssPlaceAuth.odometer)
        assertEquals(1, parsed.authenticationStatus)
        assertEquals(98_765L, parsed.odometer)
    }

    // ---- Load/unload (20 bytes) and load type (5 bytes) -------------------

    @Test
    fun `parses load unload record`() {
        val b = ByteArrayOutputStream()
        b.write(timeReal(1_700_000_100)) // operation time
        b.write(0x02) // unload operation
        // Rotterdam: 51°55.5' N, 4°28.8' E -> 51_555, 4_288.
        b.write(gnssPlace(1_700_000_100, 4, 51_555, 4_288))
        b.write(0x00) // authentication not verified
        b.write24(1_000)
        assertEquals(Layout.LOAD_UNLOAD_RECORD, b.size())

        val parsed = G2Card.loadUnloadRecord(ByteReader(b.toByteArray()))
        assertEquals(1_700_000_100, parsed.timestamp.seconds)
        assertEquals(0x02, parsed.operationType)
        assertEquals(51.0 + 55.5 / 60.0, parsed.gnssPlaceAuth.latitudeDeg, 1e-9)
        assertEquals(0, parsed.authenticationStatus)
        assertEquals(1_000L, parsed.odometer)
    }

    @Test
    fun `parses load type entry record`() {
        val b = ByteArrayOutputStream()
        b.write(timeReal(1_700_000_200))
        b.write(0x03) // load type 3
        assertEquals(Layout.LOAD_TYPE_RECORD, b.size())

        val parsed = G2Card.loadTypeEntryRecord(ByteReader(b.toByteArray()))
        assertEquals(1_700_000_200, parsed.timestamp.seconds)
        assertEquals(0x03, parsed.loadType)
    }

    // ---- Authentication status records (5 bytes) --------------------------

    @Test
    fun `parses place and gnss authentication status records`() {
        val placeRecord = timeReal(1_700_000_300) + byteArrayOf(0x02)
        val gnssRecord = timeReal(1_700_000_301) + byteArrayOf(0x01)

        val place = G2Card.placeAuthStatusRecord(ByteReader(placeRecord))
        assertEquals(1_700_000_300, place.entryTime.seconds)
        assertEquals(0x02, place.authenticationStatus)

        val gnss = G2Card.gnssAuthStatusRecord(ByteReader(gnssRecord))
        assertEquals(1_700_000_301, gnss.timestamp.seconds)
        assertEquals(0x01, gnss.authenticationStatus)
    }

    // ---- constant layout checks ------------------------------------------

    @Test
    fun `amended layout constants match the specification`() {
        assertEquals(17, Layout.APP_ID_G2)
        assertEquals(18, Layout.GNSS_AD_RECORD)
        assertEquals(17, Layout.BORDER_CROSSING_RECORD)
        assertEquals(20, Layout.LOAD_UNLOAD_RECORD)
        assertEquals(5, Layout.LOAD_TYPE_RECORD)
    }

}
