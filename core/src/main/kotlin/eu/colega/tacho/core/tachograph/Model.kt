package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.CardDate
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.card.EntryTypeDailyWorkPeriod
import eu.colega.tacho.core.card.EquipmentType
import eu.colega.tacho.core.card.EventType
import eu.colega.tacho.core.card.GnssAccuracy
import eu.colega.tacho.core.card.Nation
import eu.colega.tacho.core.card.RegionNumeric
import eu.colega.tacho.core.card.SpecificConditionType
import eu.colega.tacho.core.card.TimeReal

/** EF Identification (0520h) – CardIdentification (65 bytes). */
data class CardIdentification(
    val cardIssuingMemberState: Nation,
    val cardNumber: String,
    val cardIssuingAuthorityName: String,
    val cardIssueDate: CardDate,
    val cardValidityBegin: CardDate,
    val cardExpiryDate: CardDate,
) {
    val isValid: Boolean
        get() = cardNumber.isNotBlank() && cardNumber.any { it != '0' }
}

/** EF Driving_Licence_Info (0521h) – 53 bytes. */
data class DrivingLicenceInfo(
    val issuingAuthorityName: String,
    val issuingNation: Nation,
    val licenceNumber: String,
)

/** DriverCardHolderIdentification: name (72) + birthDate (4) + language (2). */
data class HolderIdentification(
    val name: HolderName,
    val birthDate: CardDate,
    val preferredLanguage: String,
)

data class HolderName(val surname: String, val firstNames: String)

/**
 * DriverCardApplicationIdentification (0501h): 17 bytes in the amended G2 card.
 * cardStructureVersion is two bytes: {00 00} G1, {01 00} G2 v1, {01 01} G2 v2.
 */
data class ApplicationIdentification(
    val typeOfTachographCardId: EquipmentType,
    val cardStructureVersion: CardStructureVersion,
    val structureVersionByte: Int,
    val noOfEventsPerType: Int,
    val noOfFaultsPerType: Int,
    val activityStructureLength: Int,
    val noOfCardVehicleRecords: Int,
    val noOfCardPlaceRecords: Int,
    val noOfGnssAdRecords: Int,
    val noOfSpecificConditionRecords: Int,
    val noOfCardVehicleUnitRecords: Int,
)

/** EF Application_Identification_V2 (0525h), version 2 only – 10 bytes. */
data class ApplicationIdentificationV2(
    val lengthOfFollowingData: Int,
    val noOfBorderCrossingRecords: Int,
    val noOfLoadUnloadRecords: Int,
    val noOfLoadTypeEntryRecords: Int,
    val vuConfigurationLengthRange: Int,
)

/** VehicleRegistrationIdentification: 1-byte nation + 14-byte IA5 number. */
data class VehicleRegistration(val nation: Nation, val number: String) {
    val isPresent: Boolean get() = number.isNotBlank() && number.any { it != '0' }
}

/** Raw event/fault record (24 bytes), shared by both generations. */
data class EventFaultRecord(
    val typeCode: Int,
    val begin: TimeReal,
    val end: TimeReal,
    val vehicle: VehicleRegistration,
) {
    val type: EventType get() = EventType.from(typeCode)
    val isOpen: Boolean get() = end.seconds == 0L
    val isEvent: Boolean get() = typeCode < 0x10
}

/** CardVehicleRecord (48 bytes). */
data class VehicleUse(
    val odometerBegin: Long,
    val odometerEnd: Long,
    val firstUse: TimeReal,
    val lastUse: TimeReal,
    val vehicle: VehicleRegistration,
    val vuDataBlockCounter: Int,
    val vin: String,
) {
    val odometerDriven: Long get() = (odometerEnd - odometerBegin).coerceAtLeast(0)
}

/** Activity codes from the 2-bit 'aa' field of ActivityChangeInfo. */
enum class Activity(val code: Int) {
    BREAK_OR_REST(0b00),
    AVAILABILITY(0b01),
    WORK(0b10),
    DRIVING(0b11),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): Activity = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * ActivityChangeInfo (2 bytes), bits 'scpaattttttttttt':
 * s slot (0 driver / 1 co-driver), c crew, p card inserted, aa activity,
 * t minutes since 00:00 (11 bits).
 */
data class ActivityChange(
    val minutesOfDay: Int,
    val slot: Int,
    val crew: Boolean,
    val cardInserted: Boolean,
    val activity: Activity,
)

/** One calendar day of driver activity. */
data class ActivityDay(
    val index: Int,
    val date: TimeReal,
    val dailyPresenceCounter: Int,
    val dayDistance: Int,
    val activityChanges: List<ActivityChange>,
)

/** PlaceRecord (21 bytes): where a daily work period started/ended. */
data class PlaceRecord(
    val time: TimeReal,
    val entryType: EntryTypeDailyWorkPeriod,
    val country: Nation,
    val region: RegionNumeric,
    val odometer: Long,
    val gnss: GnssPlaceRecord,
)

/**
 * GNSSPlaceRecord: authenticated position + time. Coordinates use the G2
 * GeoCoordinates encoding: multiples of 10 of the ±DDMM.M (latitude) and
 * ±DDDMM.M (longitude) representations, i.e. degrees*1000 + (minutes*10).
 */
data class GnssPlaceRecord(
    val timestamp: TimeReal,
    val accuracy: GnssAccuracy,
    val latitude: Int,
    val longitude: Int,
    val odometer: Long? = null,
) {
    /** Decimal degrees: sign × (degrees + minutes/60), minutes from the MM.M part. */
    val latitudeDeg: Double get() = toDegrees(latitude)
    val longitudeDeg: Double get() = toDegrees(longitude)

    companion object {
        internal fun toDegrees(value: Int): Double {
            val absolute = kotlin.math.abs(value)
            val degrees = absolute / 1000
            val tenthsOfMinute = absolute % 1000
            val sign = if (value < 0) -1.0 else 1.0
            return sign * (degrees + tenthsOfMinute / 600.0)
        }
    }
}

/** SpecificConditionRecord (5 bytes): out-of-scope begin/end. */
data class SpecificConditionRecord(
    val time: TimeReal,
    val type: SpecificConditionType,
)

/** CardVehicleUnitRecord (10 bytes). */
data class VehicleUnitUse(
    val timestamp: TimeReal,
    val manufacturerCode: Int,
    val deviceId: Int,
    val softwareVersion: Long,
)

/** GNSSAccumulatedDrivingRecord (18 bytes, G2 amended). */
data class GnssAdRecord(
    val timestamp: TimeReal,
    val place: GnssPlaceRecord,
)

/** Current_Usage (19 bytes). */
data class CurrentUsage(
    val sessionOpenTime: TimeReal,
    val vehicle: VehicleRegistration,
)

/** Control_Activity_Data (46 bytes): last control by an authority. */
data class ControlActivityRecord(
    val controlType: Int,
    val controlTime: TimeReal,
    val controlledCardType: EquipmentType,
    val controlledCardNation: Nation,
    val controlledCardNumber: String,
    val vehicle: VehicleRegistration,
    val downloadPeriodBegin: TimeReal,
    val downloadPeriodEnd: TimeReal,
)

/** PlaceAuthStatusRecord (5 bytes): authentication status of an EF Places entry. */
data class PlaceAuthStatusRecord(
    val entryTime: TimeReal,
    val authenticationStatus: Int,
)

/** GNSSAuthStatusADRecord (5 bytes). */
data class GnssAuthStatusRecord(
    val timestamp: TimeReal,
    val authenticationStatus: Int,
)

/** CardBorderCrossingRecord (17 bytes). */
data class BorderCrossingRecord(
    val countryLeft: Nation,
    val countryEntered: Nation,
    val gnssPlaceAuth: GnssPlaceRecord,
    val authenticationStatus: Int,
    val odometer: Long,
)

/** CardLoadUnloadRecord (20 bytes). */
data class LoadUnloadRecord(
    val timestamp: TimeReal,
    val operationType: Int,
    val gnssPlaceAuth: GnssPlaceRecord,
    val authenticationStatus: Int,
    val odometer: Long,
)

/** CardLoadTypeEntryRecord (5 bytes). */
data class LoadTypeEntryRecord(
    val timestamp: TimeReal,
    val loadType: Int,
)

/** Full parsed driver card content (generation 1 or 2, amended layout). */
data class DriverCard(
    val applicationId: ApplicationIdentification,
    val applicationIdV2: ApplicationIdentificationV2?,
    val cardCertificate: ByteArray,
    val caCertificate: ByteArray,
    val identification: CardIdentification,
    val holder: HolderIdentification,
    val drivingLicence: DrivingLicenceInfo,
    val lastCardDownload: TimeReal,
    val events: List<EventFaultRecord>,
    val faults: List<EventFaultRecord>,
    val activity: List<ActivityDay>,
    val vehicles: List<VehicleUse>,
    val places: List<PlaceRecord>,
    val currentUsage: CurrentUsage?,
    val specificConditions: List<SpecificConditionRecord>,
    val vehicleUnits: List<VehicleUnitUse>,
    val gnssAdRecords: List<GnssAdRecord>,
    val borderCrossings: List<BorderCrossingRecord>,
    val loadUnloadOperations: List<LoadUnloadRecord>,
    val loadTypeEntries: List<LoadTypeEntryRecord>,
) {
    val generation: CardStructureVersion get() = applicationId.cardStructureVersion
    val isV2: Boolean
        get() = applicationId.structureVersionByte >= 1 || applicationIdV2 != null
    val driverName: String get() = holder.name.surname + ", " + holder.name.firstNames
    val currentVehicle: VehicleRegistration? get() = currentUsage?.vehicle?.takeIf { it.isPresent }
    val lastVehicle: VehicleRegistration?
        get() = vehicles.maxByOrNull { it.lastUse.seconds }?.vehicle?.takeIf { it.isPresent }
}
