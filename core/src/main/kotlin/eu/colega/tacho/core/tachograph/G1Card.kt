package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.ByteReader
import eu.colega.tacho.core.card.CardDate
import eu.colega.tacho.core.card.EntryTypeDailyWorkPeriod
import eu.colega.tacho.core.card.EquipmentType
import eu.colega.tacho.core.card.GnssAccuracy
import eu.colega.tacho.core.card.Nation
import eu.colega.tacho.core.card.RegionNumeric
import eu.colega.tacho.core.card.SpecificConditionType
import eu.colega.tacho.core.card.TimeReal

/**
 * Parsers for generation 1 driver card structures (TCS_150) and the record
 * formats shared by both generations. All integer fields are big-endian.
 */
object G1Card {

    /** EF Identification: CardIdentification (65) + DriverCardHolderIdentification (78). */
    fun identification(r: ByteReader): CardIdentification {
        val nation = Nation.from(r.u8())
        val cardNumber = r.ascii(16)
        val authority = r.ascii(36)
        val issueDate = CardDate.from(r)
        val begin = CardDate.from(r)
        val expiry = CardDate.from(r)
        return CardIdentification(nation, cardNumber, authority, issueDate, begin, expiry)
    }

    fun holder(r: ByteReader): HolderName {
        val surname = r.ascii(36)
        val firstNames = r.ascii(36)
        return HolderName(surname, firstNames)
    }

    /** holderName (72) + birthDate (4) + preferredLanguage (2) = 78 bytes. */
    fun holderFull(r: ByteReader): HolderIdentification {
        val name = holder(r)
        val birth = CardDate.from(r)
        val language = r.ascii(2)
        return HolderIdentification(name, birth, language)
    }

    /** CardEventRecord / CardFaultRecord (24 bytes each): type, begin, end, vehicle. */
    fun eventOrFaultRecord(r: ByteReader): EventFaultRecord {
        val type = r.u8()
        val begin = TimeReal.from(r)
        val end = TimeReal.from(r)
        val vehicle = vehicleRegistration(r)
        return EventFaultRecord(type, begin, end, vehicle)
    }

    fun vehicleRegistration(r: ByteReader): VehicleRegistration {
        val nation = Nation.from(r.u8())
        val number = r.ascii(14)
        return VehicleRegistration(nation, number)
    }

    /** CardVehicleRecord (48 bytes). */
    fun vehicleRecord(r: ByteReader): VehicleUse {
        val odometerBegin = r.u24().toLong()
        val odometerEnd = r.u24().toLong()
        val firstUse = TimeReal.from(r)
        val lastUse = TimeReal.from(r)
        val vehicle = vehicleRegistration(r)
        val vuDataBlockCounter = r.u16()
        val vin = r.ascii(17)
        return VehicleUse(odometerBegin, odometerEnd, firstUse, lastUse, vehicle, vuDataBlockCounter, vin)
    }

    /** PlaceRecord (21 bytes): entry info + GNSS place record. */
    fun placeRecord(r: ByteReader): PlaceRecord {
        val time = TimeReal.from(r)
        val entryType = EntryTypeDailyWorkPeriod.from(r.u8())
        val country = Nation.from(r.u8())
        val region = RegionNumeric(r.u8())
        val odometer = r.u24().toLong()
        val gnss = gnssPlaceRecord(r)
        return PlaceRecord(time, entryType, country, region, odometer, gnss)
    }

    /**
     * GNSSPlaceRecord (11 bytes): timestamp, accuracy byte, latitude (3),
     * longitude (3). Coordinates are the ±DDMM.M×10 integers of GeoCoordinates.
     */
    fun gnssPlaceRecord(r: ByteReader): GnssPlaceRecord {
        val ts = TimeReal.from(r)
        val accuracy = gnssAccuracy(r)
        val lat = signed24(r).toInt()
        val lon = signed24(r).toInt()
        return GnssPlaceRecord(ts, accuracy, lat, lon)
    }

    /** SpecificConditionRecord (5 bytes). */
    fun specificConditionRecord(r: ByteReader): SpecificConditionRecord {
        val time = TimeReal.from(r)
        val type = SpecificConditionType.from(r.u8())
        return SpecificConditionRecord(time, type)
    }

    /** CardVehicleUnitRecord (10 bytes). */
    fun vehicleUnitRecord(r: ByteReader): VehicleUnitUse {
        val ts = TimeReal.from(r)
        val manufacturer = r.u8()
        val deviceId = r.u8()
        val softwareVersion = r.u32()
        return VehicleUnitUse(ts, manufacturer, deviceId, softwareVersion)
    }

    /** Current_Usage (19 bytes). */
    fun currentUsage(r: ByteReader): CurrentUsage {
        val openTime = TimeReal.from(r)
        val vehicle = vehicleRegistration(r)
        return CurrentUsage(openTime, vehicle)
    }

    /** Control_Activity_Data (46 bytes). */
    fun controlActivity(r: ByteReader): ControlActivityRecord {
        val type = r.u8()
        val time = TimeReal.from(r)
        val cardType = EquipmentType.from(r.u8())
        val issuingNation = Nation.from(r.u8())
        val cardNumber = r.ascii(16)
        val vehicle = vehicleRegistration(r)
        val downloadBegin = TimeReal.from(r)
        val downloadEnd = TimeReal.from(r)
        return ControlActivityRecord(type, time, cardType, issuingNation, cardNumber, vehicle, downloadBegin, downloadEnd)
    }

    /** Blank identification for partially populated dumps. */
    fun emptyIdentification(): CardIdentification = CardIdentification(
        Nation.NOT_AVAILABLE, "", "", CardDate(0, 0, 0), CardDate(0, 0, 0), CardDate(0, 0, 0),
    )

    /** Blank holder for partially populated dumps. */
    fun emptyHolder(): HolderIdentification = HolderIdentification(HolderName("", ""), CardDate(0, 0, 0), "")

    // ---- low-level shared helpers ----

    fun timeReal(r: ByteReader): TimeReal = TimeReal.from(r)

    fun gnssAccuracy(r: ByteReader): GnssAccuracy = GnssAccuracy(r.u8())

    /** Unsigned 24-bit big-endian. */
    fun u24(r: ByteReader): Long = r.u24().toLong()

    /** Signed 24-bit big-endian (two's complement), used for GeoCoordinates. */
    fun signed24(r: ByteReader): Long {
        val v = r.u24()
        return if (v >= 0x800000) (v - 0x1000000).toLong() else v.toLong()
    }

    // ---- section parsers (shared by both generations) ----

    /** EF Events_Data / EF Faults_Data payload: n 24-byte records. */
    fun eventFaultRecords(r: ByteReader, count: Int): List<EventFaultRecord> =
        List(count) { eventOrFaultRecord(r) }

    /**
     * EF Driver_Activity_Data payload: 2 + 2 pointer bytes, then a cyclic OCTET
     * STRING of daily records. Each record: previousRecordLength (2),
     * recordLength (2), date (4), presence counter (2), day distance (2), then
     * the 2-byte ActivityChangeInfo set. The buffer wraps when full: the oldest
     * record is the one with previousRecordLength == 0.
     */
    fun activity(r: ByteReader): List<ActivityDay> {
        r.skip(4) // activityPointerOldestDayRecord + activityPointerNewestRecord
        val buffer = r.bytes(r.remaining)
        if (buffer.size < ACTIVITY_HEADER) return emptyList()

        fun read16(at: Int): Int = ((buffer[at].toInt() and 0xFF) shl 8) or (buffer[at + 1].toInt() and 0xFF)

        // Find the oldest record: first record whose previousRecordLength == 0.
        var oldestOffset = -1
        var at = 0
        while (at + ACTIVITY_HEADER <= buffer.size) {
            if (read16(at) == 0) {
                oldestOffset = at
                break
            }
            val len = read16(at + 2)
            if (len <= 0) break // unwritten space
            at += len
        }
        if (oldestOffset < 0) oldestOffset = 0

        val days = mutableListOf<ActivityDay>()
        var offset = oldestOffset
        var index = 0
        val visited = mutableSetOf<Int>()
        while (offset + ACTIVITY_HEADER <= buffer.size && offset !in visited) {
            visited += offset
            val previousLength = read16(offset)
            val recordLength = read16(offset + 2)
            if (recordLength < ACTIVITY_HEADER || offset + recordLength > buffer.size) break
            val date = TimeReal(((buffer[offset + 4].toLong() and 0xFF) shl 24) or
                ((buffer[offset + 5].toLong() and 0xFF) shl 16) or
                ((buffer[offset + 6].toLong() and 0xFF) shl 8) or
                (buffer[offset + 7].toLong() and 0xFF))
            val presence = read16(offset + 8)
            val distance = read16(offset + 10)
            val changeBytes = recordLength - ACTIVITY_HEADER
            val changes = mutableListOf<ActivityChange>()
            if (changeBytes % ACTIVITY_CHANGE_INFO == 0) {
                var p = offset + ACTIVITY_HEADER
                repeat(changeBytes / ACTIVITY_CHANGE_INFO) {
                    val word = ((buffer[p].toInt() and 0xFF) shl 8) or (buffer[p + 1].toInt() and 0xFF)
                    changes += decodeActivityChange(word)
                    p += ACTIVITY_CHANGE_INFO
                }
            }
            days += ActivityDay(index, date, presence, distance, changes)
            index++
            offset += recordLength
            if (offset >= buffer.size) offset = 0 // ring wrap: newest record lives at the start
        }
        return days
    }

    private const val ACTIVITY_HEADER = 12
    private const val ACTIVITY_CHANGE_INFO = 2

    /** Decode an ActivityChangeInfo word: 'scpaattttttttttt'. */
    fun decodeActivityChange(word: Int): ActivityChange {
        val slot = (word ushr 15) and 0x01
        val crew = ((word ushr 14) and 0x01) == 1
        val cardInserted = ((word ushr 13) and 0x01) == 0 // p=0 means inserted
        val activityCode = (word ushr 11) and 0x03
        val minutes = word and 0x07FF
        return ActivityChange(minutes, slot, crew, cardInserted, Activity.from(activityCode))
    }

    /** EF Vehicles_Used payload: pointer + n 48-byte records. */
    fun vehicles(r: ByteReader): List<VehicleUse> {
        r.skip(2) // vehiclePointerNewestRecord
        return buildList {
            while (r.remaining >= VEHICLE_RECORD) add(vehicleRecord(r))
        }
    }

    private const val VEHICLE_RECORD = 48

    /** EF Places payload: pointer + n 21-byte records. */
    fun places(r: ByteReader): List<PlaceRecord> {
        r.skip(2) // placePointerNewestRecord
        return buildList {
            while (r.remaining >= PLACE_RECORD) add(placeRecord(r))
        }
    }

    private const val PLACE_RECORD = 21

    /** EF Specific_Conditions payload: pointer + n 5-byte records. */
    fun specificConditions(r: ByteReader): List<SpecificConditionRecord> {
        r.skip(2) // conditionPointerNewestRecord
        return buildList {
            while (r.remaining >= 5) add(specificConditionRecord(r))
        }
    }

    /** EF VehicleUnits_Used payload: pointer + n 10-byte records. */
    fun vehicleUnits(r: ByteReader): List<VehicleUnitUse> {
        r.skip(2) // vehicleUnitPointerNewestRecord
        return buildList {
            while (r.remaining >= 10) add(vehicleUnitRecord(r))
        }
    }
}
