package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.ByteReader
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.card.EquipmentType
import eu.colega.tacho.core.card.Nation

/**
 * Parsers for the amended generation 2 driver card (TCS_154 after amendment
 * 2021/1228). Layout highlights:
 *  - EF Application_Identification is 17 bytes: the amendment added
 *    noOfCardVehicleUnitRecords (2 bytes) to the 15-byte pre-amendment layout.
 *  - cardStructureVersion is {01 01} for version 2 cards ({01 00} for version 1).
 *  - Version 2 cards add EF 0525h..0530h and 0540h (see Layout).
 */
object G2Card {

    /** DriverCardApplicationIdentification (17 bytes). */
    fun applicationIdentification(r: ByteReader): ApplicationIdentification {
        val type = EquipmentType.from(r.u8())
        val generationByte = r.u8() // 0x00 = G1, 0x01 = G2
        val versionByte = r.u8() // 0x00 = version 1, 0x01 = version 2
        val eventsPerType = r.u8()
        val faultsPerType = r.u8()
        val activityLength = r.u16()
        val vehicleRecords = r.u16()
        val placeRecords = r.u16()
        val gnssAdRecords = r.u16()
        val specificConditions = r.u16()
        val vehicleUnitRecords = r.u16()
        return ApplicationIdentification(
            type,
            CardStructureVersion.from(generationByte),
            versionByte,
            eventsPerType,
            faultsPerType,
            activityLength,
            vehicleRecords,
            placeRecords,
            gnssAdRecords,
            specificConditions,
            vehicleUnitRecords,
        )
    }

    /** DriverCardApplicationIdentificationV2 (10 bytes). */
    fun applicationIdentificationV2(r: ByteReader): ApplicationIdentificationV2 {
        val length = r.u16()
        val borderCrossings = r.u16()
        val loadUnload = r.u16()
        val loadTypeEntries = r.u16()
        val vuConfigLength = r.u16()
        return ApplicationIdentificationV2(length, borderCrossings, loadUnload, loadTypeEntries, vuConfigLength)
    }

    /** GNSSAccumulatedDrivingRecord (18 bytes): timestamp + 14-byte GNSS place (with odometer). */
    fun gnssAdRecord(r: ByteReader): GnssAdRecord {
        val ts = G1Card.timeReal(r)
        val place = gnssPlaceWithOdometer(r)
        return GnssAdRecord(ts, place)
    }

    /** GNSS place record with odometer (14 bytes, G2v2). */
    fun gnssPlaceWithOdometer(r: ByteReader): GnssPlaceRecord {
        val ts = G1Card.timeReal(r)
        val accuracy = G1Card.gnssAccuracy(r)
        val lat = G1Card.signed24(r)
        val lon = G1Card.signed24(r)
        val odometer = G1Card.u24(r)
        return GnssPlaceRecord(ts, accuracy, lat, lon, odometer)
    }

    /** PlaceAuthStatusRecord (5 bytes). */
    fun placeAuthStatusRecord(r: ByteReader): PlaceAuthStatusRecord {
        val time = G1Card.timeReal(r)
        val status = r.u8()
        return PlaceAuthStatusRecord(time, status)
    }

    /** GNSSAuthStatusADRecord (5 bytes). */
    fun gnssAuthStatusRecord(r: ByteReader): GnssAuthStatusRecord {
        val ts = G1Card.timeReal(r)
        val status = r.u8()
        return GnssAuthStatusRecord(ts, status)
    }

    /** CardBorderCrossingRecord (17 bytes): countries, authenticated position, odometer. */
    fun borderCrossingRecord(r: ByteReader): BorderCrossingRecord {
        val left = Nation.from(r.u8())
        val entered = Nation.from(r.u8())
        val ts = G1Card.timeReal(r)
        val accuracy = G1Card.gnssAccuracy(r)
        val lat = G1Card.signed24(r)
        val lon = G1Card.signed24(r)
        val authStatus = r.u8()
        val odometer = G1Card.u24(r)
        val place = GnssPlaceRecord(ts, accuracy, lat, lon, null)
        return BorderCrossingRecord(left, entered, place, authStatus, odometer)
    }

    /** CardLoadUnloadRecord (20 bytes). */
    fun loadUnloadRecord(r: ByteReader): LoadUnloadRecord {
        val ts = G1Card.timeReal(r)
        val operationType = r.u8()
        val placeTs = G1Card.timeReal(r)
        val accuracy = G1Card.gnssAccuracy(r)
        val lat = G1Card.signed24(r)
        val lon = G1Card.signed24(r)
        val authStatus = r.u8()
        val odometer = G1Card.u24(r)
        val place = GnssPlaceRecord(placeTs, accuracy, lat, lon, null)
        return LoadUnloadRecord(ts, operationType, place, authStatus, odometer)
    }

    /** CardLoadTypeEntryRecord (5 bytes). */
    fun loadTypeEntryRecord(r: ByteReader): LoadTypeEntryRecord {
        val ts = G1Card.timeReal(r)
        val loadType = r.u8()
        return LoadTypeEntryRecord(ts, loadType)
    }
}
