package eu.colega.tacho.core.tachograph

/**
 * File identifiers (FIDs), short file identifiers (SFIDs) and byte layouts for
 * the tachograph card application, per Regulation (EU) 2016/799 Annex IC
 * (requirements 278-280a, TCS_148-TCS_155) as amended by Regulation (EU)
 * 2021/1228 (smart tachograph version 2, applicable from 21.08.2023).
 *
 * All EF structures are transparent; records are read with READ BINARY /
 * READ RECORD via the SFIDs given here.
 */
object Layout {
    /** Dedicated File (AID) for tachograph card applications. */
    const val DF_TACHOGRAPH_G1 = "A0000000454143480101" // ISO RID + TACHOG + G1
    const val DF_TACHOGRAPH_G2 = "A0000000454143480102" // ISO RID + TACHOG + G2

    /** Elementary file identifiers. */
    const val EF_APPLICATION_IDENTIFICATION = 0x0501
    const val EF_CARD_CERTIFICATE = 0xC100 // generation 1 RSA card certificate
    const val EF_CARD_MA_CERTIFICATE = 0xC100 // generation 2 MAC certificate
    const val EF_CARD_SIGN_CERTIFICATE = 0xC101
    const val EF_CA_CERTIFICATE = 0xC108
    const val EF_LINK_CERTIFICATE = 0xC109
    const val EF_IDENTIFICATION = 0x0520
    const val EF_CARD_DOWNLOAD = 0x050E
    const val EF_DRIVING_LICENCE_INFO = 0x0521
    const val EF_EVENTS_DATA = 0x0502
    const val EF_FAULTS_DATA = 0x0503
    const val EF_DRIVER_ACTIVITY_DATA = 0x0504
    const val EF_VEHICLES_USED = 0x0505
    const val EF_PLACES = 0x0506
    const val EF_CURRENT_USAGE = 0x0507
    const val EF_CONTROL_ACTIVITY_DATA = 0x0508
    const val EF_SPECIFIC_CONDITIONS = 0x0522
    const val EF_VEHICLE_UNITS_USED = 0x0523
    const val EF_GNSS_PLACES = 0x0524
    const val EF_APPLICATION_IDENTIFICATION_V2 = 0x0525
    const val EF_PLACES_AUTHENTICATION = 0x0526
    const val EF_GNSS_PLACES_AUTHENTICATION = 0x0527
    const val EF_BORDER_CROSSINGS = 0x0528
    const val EF_LOAD_UNLOAD_OPERATIONS = 0x0529
    const val EF_LOAD_TYPE_ENTRIES = 0x0530
    const val EF_VU_CONFIGURATION = 0x0540

    /** Short EF identifiers (SFID) for READ RECORD. */
    val SFIDS = mapOf(
        EF_APPLICATION_IDENTIFICATION to 1,
        EF_CARD_MA_CERTIFICATE to 2,
        EF_CARD_SIGN_CERTIFICATE to 3,
        EF_CA_CERTIFICATE to 4,
        EF_LINK_CERTIFICATE to 5,
        EF_IDENTIFICATION to 6,
        EF_CARD_DOWNLOAD to 7,
        EF_DRIVING_LICENCE_INFO to 10,
        EF_EVENTS_DATA to 12,
        EF_FAULTS_DATA to 13,
        EF_DRIVER_ACTIVITY_DATA to 14,
        EF_VEHICLES_USED to 15,
        EF_PLACES to 16,
        EF_CURRENT_USAGE to 17,
        EF_CONTROL_ACTIVITY_DATA to 18,
        EF_SPECIFIC_CONDITIONS to 19,
        EF_VEHICLE_UNITS_USED to 20,
        EF_GNSS_PLACES to 21,
        EF_APPLICATION_IDENTIFICATION_V2 to 22,
        EF_PLACES_AUTHENTICATION to 23,
        EF_GNSS_PLACES_AUTHENTICATION to 24,
        EF_BORDER_CROSSINGS to 25,
        EF_LOAD_UNLOAD_OPERATIONS to 26,
        EF_LOAD_TYPE_ENTRIES to 27,
        EF_VU_CONFIGURATION to 30,
    )

    /**
     * Amended generation 2 driver card byte sizes (TCS_154 after amendment).
     * Certificates have variable size 204..341 (ECC); every other EF is fixed.
     */
    const val APP_ID_G2 = 17 // + noOfCardVehicleUnitRecords vs generation 1
    const val APP_ID_G2_V2 = 10
    const val CERT_MAX = 341
    const val IDENTIFICATION = 143
    const val CARD_DOWNLOAD = 4
    const val DRIVING_LICENCE_INFO = 53
    const val EVENTS_DATA = 3168 // 132 CardEventRecords (11 sets * n1=12) * 24
    const val FAULTS_DATA = 1152 // 48 CardFaultRecords * 24
    const val DRIVER_ACTIVITY_DATA = 13780 // 2 pointers + n6=13776
    const val VEHICLES_USED = 9602 // 2 + 200 * 48
    const val PLACES = 2354 // 2 + 112 * 21
    const val CURRENT_USAGE = 19
    const val CONTROL_ACTIVITY_DATA = 46
    const val SPECIFIC_CONDITIONS = 562 // 2 + 112 * 5
    const val VEHICLE_UNITS_USED = 2002 // 2 + 200 * 10
    const val GNSS_PLACES = 6050 // 2 + 336 * 18
    const val PLACES_AUTHENTICATION = 562 // 2 + 112 * 5
    const val GNSS_PLACES_AUTHENTICATION = 1682 // 2 + 336 * 5
    const val BORDER_CROSSINGS = 19042 // 2 + 1120 * 17
    const val LOAD_UNLOAD_OPERATIONS = 32482 // 2 + 1624 * 20
    const val LOAD_TYPE_ENTRIES = 1682 // 2 + 336 * 5
    const val VU_CONFIGURATION = 3072

    /** Amended record counts (TCS_155 after amendment). */
    const val N1_EVENTS_PER_TYPE = 12
    const val N2_FAULTS_PER_TYPE = 24
    const val N3_VEHICLE_RECORDS = 200
    const val N4_PLACE_RECORDS = 112
    const val N6_ACTIVITY_LENGTH = 13776 // 56 days * 117 activity changes
    const val N7_VEHICLE_UNIT_RECORDS = 200
    const val N8_GNSS_AD_RECORDS = 336
    const val N9_SPECIFIC_CONDITIONS = 112
    const val N10_BORDER_CROSSINGS = 1120
    const val N11_LOAD_UNLOAD = 1624
    const val N12_LOAD_TYPE_ENTRIES = 336
    const val N13_VU_CONFIG_LENGTH = 3072

    /** Fixed record sizes (bytes). */
    const val EVENT_RECORD = 24
    const val FAULT_RECORD = 24
    const val VEHICLE_RECORD = 48
    const val PLACE_RECORD = 21 // incl. 11-byte GNSS place record
    const val SPECIFIC_CONDITION_RECORD = 5
    const val VEHICLE_UNIT_RECORD = 10
    const val GNSS_AD_RECORD = 18
    const val GNSS_AD_RECORD_G2 = 18 // amended: place record carries odometer
    const val GNSS_AD_RECORD_G1 = 15 // version 1: timestamp + 11-byte place record
    const val LOAD_TYPE_RECORD = 5
    const val PLACE_AUTH_RECORD = 5
    const val GNSS_AUTH_RECORD = 5
    const val BORDER_CROSSING_RECORD = 17
    const val LOAD_UNLOAD_RECORD = 20
    const val LOAD_TYPE_ENTRY_RECORD = 5
    const val ACTIVITY_CHANGE_INFO = 2 // 'scpaattttttttttt'
    const val ACTIVITY_DAILY_HEADER = 12 // prev/cur lengths 2+2, date 4, presence 2, distance 2

    /** Generation 1 driver card sizes (TCS_150, unchanged by the amendment). */
    const val APP_ID_G1 = 10
    const val CERT_G1 = 194
    val EVENTS_DATA_G1 = 1584..3168
    val FAULTS_DATA_G1 = 576..1152
    val DRIVER_ACTIVITY_G1 = 5548..13780
    val VEHICLES_USED_G1 = 4034..9602
    val PLACES_G1 = 1766..2354
    val SPECIFIC_CONDITIONS_G1 = 282..562
    val VEHICLE_UNITS_G1 = 842..2002
    val GNSS_PLACES_G1 = 3782..5042
}
