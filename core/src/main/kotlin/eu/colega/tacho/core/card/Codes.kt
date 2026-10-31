package eu.colega.tacho.core.card

/**
 * Coded enumerations from the tachograph card specification
 * (Regulation (EU) 2016/799 Appendix 1/2 and Regulation 1360/2002 Appendix 1).
 */

/** EquipmentType (SIZE(1)): type of tachograph card. */
enum class EquipmentType(val code: Int) {
    DRIVER(0x00),
    WORKSHOP(0x01),
    CONTROL(0x02),
    COMPANY(0x03),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): EquipmentType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** CardStructureVersion (SIZE(2)): version of the implemented structure. */
enum class CardStructureVersion(val code: Int) {
    GENERATION_1(0x00),
    GENERATION_2(0x01),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): CardStructureVersion = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * NationNumeric / NationAlpha (Annex 1C req. 440 list maintained by the
 * interoperability test laboratory; ISO 3166 alpha-2 distinguishing signs).
 */
enum class Nation(val code: Int, val alpha: String) {
    NOT_AVAILABLE(0x00, ""),
    AUSTRIA(0x01, "AT"),
    BELGIUM(0x02, "BE"),
    BULGARIA(0x03, "BG"),
    CROATIA(0x04, "HR"),
    CYPRUS(0x05, "CY"),
    CZECH_REPUBLIC(0x06, "CZ"),
    DENMARK(0x07, "DK"),
    ESTONIA(0x08, "EE"),
    FINLAND(0x09, "FI"),
    FRANCE(0x0A, "FR"),
    GERMANY(0x0B, "DE"),
    GREECE(0x0C, "GR"),
    HUNGARY(0x0D, "HU"),
    ICELAND(0x0E, "IS"),
    IRELAND(0x0F, "IE"),
    ITALY(0x10, "IT"),
    LATVIA(0x11, "LV"),
    LIECHTENSTEIN(0x12, "LI"),
    LITHUANIA(0x13, "LT"),
    LUXEMBOURG(0x14, "LU"),
    MALTA(0x15, "MT"),
    NETHERLANDS(0x16, "NL"),
    NORWAY(0x17, "NO"),
    POLAND(0x18, "PL"),
    PORTUGAL(0x19, "PT"),
    ROMANIA(0x1A, "RO"),
    SAN_MARINO(0x1B, "SM"),
    SLOVAKIA(0x1C, "SK"),
    SLOVENIA(0x1D, "SI"),
    SPAIN(0x1E, "ES"),
    SWEDEN(0x1F, "SE"),
    SWITZERLAND(0x20, "CH"),
    UNITED_KINGDOM(0x21, "GB"),
    TURKEY(0x22, "TR"),
    NORTH_MACEDONIA(0x23, "MK"),
    SERBIA(0x24, "RS"),
    MONTENEGRO(0x25, "ME"),
    BOSNIA_HERZEGOVINA(0x26, "BA"),
    ALBANIA(0x27, "AL"),
    KOSOVO(0x28, "XK"),
    RFU(0xFF, "");

    companion object {
        fun from(code: Int): Nation = entries.firstOrNull { it.code == code } ?: RFU
        fun fromAlpha(alpha: String): Nation = entries.firstOrNull { it.alpha == alpha } ?: RFU
    }
}

/**
 * EventFaultType (SIZE(1)). Generation 1 values plus generation 2 additions
 * (GNSS, remote communication module, ITS faults).
 */
enum class EventType(val code: Int) {
    // General events
    GENERAL_NO_DETAILS(0x00),
    INSERTION_OF_NON_VALID_CARD(0x01),
    CARD_CONFLICT(0x02),
    TIME_OVERLAP(0x03),
    DRIVING_WITHOUT_APPROPRIATE_CARD(0x04),
    CARD_INSERTION_WHILE_DRIVING(0x05),
    LAST_CARD_SESSION_NOT_CLOSED(0x06),
    OVER_SPEEDING(0x07),
    POWER_SUPPLY_INTERRUPTION(0x08),
    MOTION_DATA_ERROR(0x09),
    VEHICLE_MOTION_CONFLICT(0x0A),
    TIME_CONFLICT_GNSS(0x0B), // generation 2
    GENERAL_RFU_0C(0x0C),
    GENERAL_RFU_0D(0x0D),
    GENERAL_RFU_0E(0x0E),
    GENERAL_RFU_0F(0x0F),

    // Vehicle unit security breach attempts
    VU_SECURITY_NO_DETAILS(0x10),
    VU_MOTION_SENSOR_AUTH_FAILURE(0x11),
    VU_CARD_AUTH_FAILURE(0x12),
    VU_UNAUTHORISED_CHANGE_MOTION_SENSOR(0x13),
    VU_CARD_DATA_INPUT_INTEGRITY_ERROR(0x14),
    VU_STORED_USER_DATA_INTEGRITY_ERROR(0x15),
    VU_INTERNAL_DATA_TRANSFER_ERROR(0x16),
    VU_UNAUTHORISED_CASE_OPENING(0x17),
    VU_HARDWARE_SABOTAGE(0x18),
    VU_RFU_19(0x19),
    VU_RFU_1A(0x1A),
    VU_RFU_1B(0x1B),
    VU_RFU_1C(0x1C),
    VU_RFU_1D(0x1D),
    VU_RFU_1E(0x1E),
    VU_RFU_1F(0x1F),

    // Sensor security breach attempts
    SENSOR_SECURITY_NO_DETAILS(0x20),
    SENSOR_AUTH_FAILURE(0x21),
    SENSOR_STORED_DATA_INTEGRITY_ERROR(0x22),
    SENSOR_INTERNAL_DATA_TRANSFER_ERROR(0x23),
    SENSOR_UNAUTHORISED_CASE_OPENING(0x24),
    SENSOR_HARDWARE_SABOTAGE(0x25),
    SENSOR_RFU_26(0x26),
    SENSOR_RFU_27(0x27),
    SENSOR_RFU_28(0x28),
    SENSOR_RFU_29(0x29),
    SENSOR_RFU_2A(0x2A),
    SENSOR_RFU_2B(0x2B),
    SENSOR_RFU_2C(0x2C),
    SENSOR_RFU_2D(0x2D),
    SENSOR_RFU_2E(0x2E),
    SENSOR_RFU_2F(0x2F),

    // Recording equipment faults
    VU_FAULT_NO_DETAILS(0x30),
    VU_INTERNAL_FAULT(0x31),
    PRINTER_FAULT(0x32),
    DISPLAY_FAULT(0x33),
    DOWNLOADING_FAULT(0x34),
    SENSOR_FAULT(0x35),
    VU_FAULT_RFU_36(0x36),
    VU_FAULT_RFU_37(0x37),
    VU_FAULT_RFU_38(0x38),
    VU_FAULT_RFU_39(0x39),
    VU_FAULT_RFU_3A(0x3A),
    VU_FAULT_RFU_3B(0x3B),
    VU_FAULT_RFU_3C(0x3C),
    VU_FAULT_RFU_3D(0x3D),
    VU_FAULT_RFU_3E(0x3E),
    VU_FAULT_RFU_3F(0x3F),

    // Card faults
    CARD_FAULT_NO_DETAILS(0x40),
    CARD_FAULT_RFU_41(0x41),
    CARD_FAULT_RFU_42(0x42),
    CARD_FAULT_RFU_43(0x43),
    CARD_FAULT_RFU_44(0x44),
    CARD_FAULT_RFU_45(0x45),
    CARD_FAULT_RFU_46(0x46),
    CARD_FAULT_RFU_47(0x47),
    CARD_FAULT_RFU_48(0x48),
    CARD_FAULT_RFU_49(0x49),
    CARD_FAULT_RFU_4A(0x4A),
    CARD_FAULT_RFU_4B(0x4B),
    CARD_FAULT_RFU_4C(0x4C),
    CARD_FAULT_RFU_4D(0x4D),
    CARD_FAULT_RFU_4E(0x4E),
    CARD_FAULT_RFU_4F(0x4F),

    // Generation 2: GNSS related faults
    GNSS_NO_DETAILS(0x50),
    GNSS_INTERNAL_RECEIVER_FAULT(0x51),
    GNSS_EXTERNAL_RECEIVER_FAULT(0x52),
    GNSS_EXTERNAL_COMMUNICATION_FAULT(0x53),
    GNSS_NO_POSITION_DATA(0x54),
    GNSS_TAMPER_DETECTION(0x55),
    GNSS_FACILITY_CERTIFICATE_EXPIRED(0x56),
    GNSS_RFU_57(0x57),
    GNSS_RFU_58(0x58),
    GNSS_RFU_59(0x59),
    GNSS_RFU_5A(0x5A),
    GNSS_RFU_5B(0x5B),
    GNSS_RFU_5C(0x5C),
    GNSS_RFU_5D(0x5D),
    GNSS_RFU_5E(0x5E),
    GNSS_RFU_5F(0x5F),

    // Generation 2: Remote Communication Module related faults
    RCM_NO_DETAILS(0x60),
    RCM_FAULT(0x61),
    RCM_COMMUNICATION_FAULT(0x62),
    RCM_RFU_63(0x63),
    RCM_RFU_64(0x64),
    RCM_RFU_65(0x65),
    RCM_RFU_66(0x66),
    RCM_RFU_67(0x67),
    RCM_RFU_68(0x68),
    RCM_RFU_69(0x69),
    RCM_RFU_6A(0x6A),
    RCM_RFU_6B(0x6B),
    RCM_RFU_6C(0x6C),
    RCM_RFU_6D(0x6D),
    RCM_RFU_6E(0x6E),
    RCM_RFU_6F(0x6F),

    // Generation 2: ITS interface faults
    ITS_NO_DETAILS(0x70),
    ITS_RFU_71(0x71),
    ITS_RFU_72(0x72),
    ITS_RFU_73(0x73),
    ITS_RFU_74(0x74),
    ITS_RFU_75(0x75),
    ITS_RFU_76(0x76),
    ITS_RFU_77(0x77),
    ITS_RFU_78(0x78),
    ITS_RFU_79(0x79),
    ITS_RFU_7A(0x7A),
    ITS_RFU_7B(0x7B),
    ITS_RFU_7C(0x7C),
    ITS_RFU_7D(0x7D),
    ITS_RFU_7E(0x7E),
    ITS_RFU_7F(0x7F),

    RFU(0x80),
    MANUFACTURER_SPECIFIC(0xF0),
    UNKNOWN(-1);

    /** true if this is a security-breach class (vehicle unit or sensor). */
    val isSecurityBreach: Boolean
        get() = code in 0x10..0x2F

    /** true if this is a fault class (equipment, card, GNSS, RCM, ITS). */
    val isFault: Boolean
        get() = code >= 0x30

    companion object {
        fun from(code: Int): EventType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** EntryTypeDailyWorkPeriod (SIZE(1)): 00h start, 01h end. */
enum class EntryTypeDailyWorkPeriod(val code: Int) {
    START(0x00),
    END(0x01),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): EntryTypeDailyWorkPeriod = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** SpecificConditionType (SIZE(1)). */
enum class SpecificConditionType(val code: Int) {
    OUT_OF_SCOPE_BEGIN(0x00),
    OUT_OF_SCOPE_END(0x01),
    RFU(0x02),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): SpecificConditionType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** EquipmentSoftwareVersionSoftwareType (SIZE(1)). */
enum class SoftwareType(val code: Int) {
    VU_APPLICATION(0x00),
    MOTION_SENSOR_APPLICATION(0x01),
    GNSS_FACILITY_APPLICATION(0x02),
    RCM_FACILITY_APPLICATION(0x03),
    ITS_FACILITY_APPLICATION(0x04),
    UNKNOWN(-1);

    companion object {
        fun from(code: Int): SoftwareType = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * GNSSAccuracy (SIZE(1)): INTEGER(1..100), a multiple (factor 10) of the X.Y
 * accuracy in metres reported by the GSA NMEA sentence. A raw value of 25
 * therefore means 2.5 m.
 */
data class GnssAccuracy(val raw: Int) {
    init {
        require(raw in 0..255)
    }

    /** Reported accuracy in metres (raw / 10). */
    val meters: Double get() = raw / 10.0
}

/** RegionNumeric (SIZE(1)): 00h not available, else laboratory-maintained region codes. */
data class RegionNumeric(val code: Int) {
    init {
        require(code in 0..255)
    }

    val isAvailable: Boolean get() = code != 0x00
}
