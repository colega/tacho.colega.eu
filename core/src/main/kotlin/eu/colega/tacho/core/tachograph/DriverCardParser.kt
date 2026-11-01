package eu.colega.tacho.core.tachograph

import eu.colega.tacho.core.card.ByteReader
import eu.colega.tacho.core.card.CardStructureVersion
import eu.colega.tacho.core.card.EquipmentType
import eu.colega.tacho.core.card.Nation
import eu.colega.tacho.core.card.TimeReal
import eu.colega.tacho.core.card.TruncatedDataException

/**
 * Parses a complete driver card dump into a [DriverCard].
 *
 * Sizes follow the amended (2021/1228) generation 2 layout; generation 1 and
 * generation 2 version 1 cards are handled by dividing the actual EF payload
 * by the record size, which also covers their smaller allocations.
 */
object DriverCardParser {

    /**
     * Parse a driver card dump. Generation is detected from the
     * cardStructureVersion field of EF Application_Identification (0501h):
     * {00 00} = generation 1, {01 00} = generation 2 version 1,
     * {01 01} = generation 2 version 2 (amended layout).
     */
    fun parse(dump: DriverCardDump): DriverCard {
        val appIdBytes = dump.file(Layout.EF_APPLICATION_IDENTIFICATION)
            ?: throw TruncatedDataException("missing EF Application_Identification")
        // cardStructureVersion is 2 bytes: byte 0 = generation, byte 1 = version.
        val generationByte = (ByteReader(appIdBytes).u16At(1) shr 8) and 0xFF
        val generation = CardStructureVersion.from(generationByte)
        val appId = parseApplicationId(generation, appIdBytes)

        val identification = dump.file(Layout.EF_IDENTIFICATION)?.let {
            G1Card.identification(ByteReader(it), generation)
        }
        val holder = dump.file(Layout.EF_IDENTIFICATION)?.let {
            val r = ByteReader(it)
            r.skip(65) // CardIdentification
            G1Card.holderFull(r, generation)
        }

        val events = dump.file(Layout.EF_EVENTS_DATA)?.let {
            G1Card.eventFaultRecords(ByteReader(it), it.size / Layout.EVENT_RECORD)
        } ?: emptyList()

        val faults = dump.file(Layout.EF_FAULTS_DATA)?.let {
            G1Card.eventFaultRecords(ByteReader(it), it.size / Layout.FAULT_RECORD)
        } ?: emptyList()

        val activity = dump.file(Layout.EF_DRIVER_ACTIVITY_DATA)?.let {
            G1Card.activity(ByteReader(it))
        } ?: emptyList()

        val vehicles = dump.file(Layout.EF_VEHICLES_USED)?.let {
            if (generation == CardStructureVersion.GENERATION_1) {
                G1Card.vehiclesG1(ByteReader(it))
            } else {
                G1Card.vehiclesG2(ByteReader(it))
            }
        } ?: emptyList()

        val places = dump.file(Layout.EF_PLACES)?.let {
            if (generation == CardStructureVersion.GENERATION_1) {
                G1Card.placesG1(ByteReader(it))
            } else {
                G1Card.placesG2(ByteReader(it))
            }
        } ?: emptyList()

        val currentUsage = dump.file(Layout.EF_CURRENT_USAGE)?.let {
            G1Card.currentUsage(ByteReader(it))
        }

        val specificConditions = dump.file(Layout.EF_SPECIFIC_CONDITIONS)?.let {
            if (generation == CardStructureVersion.GENERATION_1) {
                G1Card.specificConditionsG1(ByteReader(it))
            } else {
                G1Card.specificConditionsG2(ByteReader(it))
            }
        } ?: emptyList()

        val vehicleUnits = dump.file(Layout.EF_VEHICLE_UNITS_USED)?.let {
            G1Card.vehicleUnits(ByteReader(it))
        } ?: emptyList()

        val gnssAd = dump.file(Layout.EF_GNSS_PLACES)?.let { bytes ->
            val r = ByteReader(bytes)
            r.skip(2) // gnssADPointerNewestRecord
            // Record size per generation, derived from the declared record
            // count: 18 bytes in amended G2 (with odometer), 15 in version 1.
            val recordSize = when {
                appId.noOfGnssAdRecords == null || appId.noOfGnssAdRecords == 0 -> Layout.GNSS_AD_RECORD_G2
                r.remaining % appId.noOfGnssAdRecords == 0 ->
                    r.remaining / appId.noOfGnssAdRecords
                else -> Layout.GNSS_AD_RECORD_G2
            }
            buildList {
                while (r.remaining >= recordSize) {
                    val ts = G1Card.timeReal(r)
                    val place = if (recordSize == Layout.GNSS_AD_RECORD_G2) {
                        G2Card.gnssPlaceWithOdometer(r)
                    } else {
                        G1Card.gnssPlaceRecord(r)
                    }
                    add(GnssAdRecord(ts, place))
                }
            }
        } ?: emptyList()

        val appIdV2 = dump.file(Layout.EF_APPLICATION_IDENTIFICATION_V2)?.let {
            G2Card.applicationIdentificationV2(ByteReader(it))
        }

        val placeAuth = dump.file(Layout.EF_PLACES_AUTHENTICATION)?.let {
            val r = ByteReader(it)
            r.skip(2)
            buildList {
                while (r.remaining >= 5) {
                    val time = G1Card.timeReal(r)
                    add(PlaceAuthStatusRecord(time, r.u8()))
                }
            }
        } ?: emptyList()

        val gnssAuth = dump.file(Layout.EF_GNSS_PLACES_AUTHENTICATION)?.let {
            val r = ByteReader(it)
            r.skip(2)
            buildList {
                while (r.remaining >= 5) {
                    val time = G1Card.timeReal(r)
                    add(GnssAuthStatusRecord(time, r.u8()))
                }
            }
        } ?: emptyList()

        val borderCrossings = dump.file(Layout.EF_BORDER_CROSSINGS)?.let {
            val r = ByteReader(it)
            r.skip(2)
            buildList {
                while (r.remaining >= Layout.BORDER_CROSSING_RECORD) {
                    add(G2Card.borderCrossingRecord(r))
                }
            }
        } ?: emptyList()

        val loadUnload = dump.file(Layout.EF_LOAD_UNLOAD_OPERATIONS)?.let {
            val r = ByteReader(it)
            r.skip(2)
            buildList {
                while (r.remaining >= Layout.LOAD_UNLOAD_RECORD) {
                    add(G2Card.loadUnloadRecord(r))
                }
            }
        } ?: emptyList()

        val loadTypes = dump.file(Layout.EF_LOAD_TYPE_ENTRIES)?.let {
            val r = ByteReader(it)
            r.skip(2)
            buildList {
                while (r.remaining >= Layout.LOAD_TYPE_RECORD) {
                    add(G2Card.loadTypeEntryRecord(r))
                }
            }
        } ?: emptyList()

        return DriverCard(
            applicationId = appId,
            applicationIdV2 = appIdV2,
            cardCertificate = dump.file(Layout.EF_CARD_MA_CERTIFICATE) ?: ByteArray(0),
            caCertificate = dump.file(Layout.EF_CA_CERTIFICATE) ?: ByteArray(0),
            identification = identification ?: G1Card.emptyIdentification(),
            holder = holder ?: G1Card.emptyHolder(),
            drivingLicence = dump.file(Layout.EF_DRIVING_LICENCE_INFO)?.let {
                val r = ByteReader(it)
                DrivingLicenceInfo(r.ascii(36), Nation.from(r.u8()), r.ascii(16))
            } ?: DrivingLicenceInfo("", Nation.NOT_AVAILABLE, ""),
            lastCardDownload = dump.file(Layout.EF_CARD_DOWNLOAD)?.let {
                G1Card.timeReal(ByteReader(it))
            } ?: TimeReal(0),
            events = events,
            faults = faults,
            activity = activity,
            vehicles = vehicles,
            places = places,
            currentUsage = currentUsage,
            specificConditions = specificConditions,
            vehicleUnits = vehicleUnits,
            gnssAdRecords = gnssAd,
            borderCrossings = borderCrossings,
            loadUnloadOperations = loadUnload,
            loadTypeEntries = loadTypes,
        )
    }

    /** Parse the AppId bytes of a card with a known generation. */
    fun parseApplicationId(generation: CardStructureVersion, bytes: ByteArray): ApplicationIdentification {
        val reader = ByteReader(bytes)
        return if (generation == CardStructureVersion.GENERATION_1) {
            parseG1ApplicationId(reader)
        } else {
            // Byte 2 of cardStructureVersion tells version 1 (15-byte AppId)
            // from version 2 (17-byte AppId with the VU record counter).
            val isVersion2 = bytes.getOrNull(2)?.toInt()?.and(0xFF)?.let { it >= 1 } == true
            G2Card.applicationIdentification(reader, includeVehicleUnitRecords = isVersion2)
        }
    }

    /** Generation 1 AppId (10 bytes): no GNSS, specific-condition or VU counts. */
    private fun parseG1ApplicationId(r: ByteReader): ApplicationIdentification {
        val type = EquipmentType.from(r.u8())
        val generationByte = r.u8()
        val versionByte = r.u8()
        val events = r.u8()
        val faults = r.u8()
        val activityLength = r.u16()
        val vehicles = r.u16()
        val places = r.u8()
        return ApplicationIdentification(
            type,
            CardStructureVersion.from(generationByte),
            versionByte,
            events,
            faults,
            activityLength,
            vehicles,
            places,
            0,
            0,
            0,
        )
    }
}
