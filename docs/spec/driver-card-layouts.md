# Driver card layouts (verified)

Source of truth for the binary parser in `core`. Every number here was verified
against the official texts:

- Regulation (EU) 2016/799 Annex IC, Appendix 1 (Data Dictionary) and Appendix 2
  (Tachograph cards specification), original OJ text.
- Commission Implementing Regulation (EU) 2021/1228 (OJ L 273, 30.7.2021),
  which replaces TCS_148..TCS_155 for the second generation driver card.
- JRC Digital Tachograph nation codes page (nation list is maintained by the
  appointed interoperability laboratory, not by the regulation text).

Totals were cross-checked arithmetically (min/max sums balance exactly).

## Primitive types

| Type | Size | Encoding |
| --- | --- | --- |
| `TimeReal` | 4 | unsigned seconds since 1970-01-01 00:00:00 GMT (octet aligned) |
| `Datef` | 4 | BCD: year(2) month(1) day(1); `00000000` = no date |
| `Nation` | 1 | numeric code, see nation table below |
| `ActivityChangeInfo` | 2 | bits: `s`(slot 0=DRIVER/1=CO-DRIVER) `c`(crew) `p`(card present: 0=inserted/1=withdrawn) `aa`(activity 00=BREAK/REST 01=AVAILABILITY 10=WORK 11=DRIVING) `ttttttttttt` (11 bits: minutes since 00h00) |
| String | n | bytes: left aligned, space padded (`20`), zero filled (`00`) means "not set" per field default |

## G1 driver card (TCS_148 / TCS_150, 2016/799)

DF Tachograph `0500`, total **11378 - 24926** bytes. Reads are plain APDUs
(`Read = SC2` = `ALW OR SM-MAC-G1 OR SM-MAC-G2`).

| EF | FID | Content | Size |
| --- | --- | --- | --- |
| Application_Identification | `0501` | `CardApplicationIdentification` | 10 |
| Card_Certificate | `C100` | DER certificate | 194 |
| CA_Certificate | `C108` | DER certificate | 194 |
| Identification | `0520` | CardIdentification 65 + DriverCardHolderIdentification 78 | 143 |
| Card_Download | `050E` | lastCardDownload TimeReal | 4 |
| Driving_Licence_Info | `0521` | authority 36 + nation 1 + number 16 | 53 |
| Events_Data | `0502` | 6 sets x noOfEventsPerType x CardEventRecord(24) | 864-1728 |
| Faults_Data | `0503` | 2 sets x noOfFaultsPerType x CardFaultRecord(24) | 576-1152 |
| Driver_Activity_Data | `0504` | pointers 2+2 + activityDailyRecords(activityStructureLength) | 5548-13780 |
| Vehicles_Used | `0505` | pointer 2 + noOfCardVehicleRecords x VehicleRecord(31) | 2606-6202 |
| Places | `0506` | pointer 1 + noOfCardPlaceRecords x PlaceRecord(10) | 841-1121 |
| Current_Usage | `0507` | sessionOpenTime 4 + sessionOpenVehicle 15 | 19 |
| Control_Activity_Data | `0508` | CardControlActivityDataRecord | 46 |
| Specific_Conditions | `0522` | 56 x SpecificConditionRecord(5) | 280 |

Spec ranges for the AppId counters: noOfEventsPerType 6..12,
noOfFaultsPerType 12..24, noOfCardVehicleRecords 84..200,
noOfCardPlaceRecords 84..112, activityStructureLength (CardActivityLengthRange)
5544..13776 (28 days x 198..492 bytes/day).

### Element layouts (G1 card format)

- `CardApplicationIdentification` (10): typeOfTachographCardId 1, cardStructureVersion 2,
  noOfEventsPerType 1, noOfFaultsPerType 1, activityStructureLength 2,
  noOfCardVehicleRecords 2, noOfCardPlaceRecords 1.
- `CardIdentification` (65): cardIssuingMemberState 1, cardNumber 16
  (cardType 1, cardIssuingMemberState 1, cardNumber 9, cardRevisionNumber 1,
  cardIssuanceDate 4 BCD), cardIssuingAuthorityName 36, cardIssueDate 4,
  cardValidityBegin 4, cardExpiryDate 4.
- `DriverCardHolderIdentification` (78): cardHolderName 72 (holderSurname 36,
  holderFirstNames 36), cardHolderBirthDate 4, cardHolderPreferredLanguage 2.
- `CardEventRecord` (24): eventType 1, eventBeginTime 4, eventEndTime 4,
  eventVehicleRegistration 15 (nation 1 + registrationNumber 14).
- `CardFaultRecord` (24): faultType 1, faultBeginTime 4, faultEndTime 4,
  faultVehicleRegistration 15.
- `CardActivityDailyRecord` (variable): activityPreviousRecordLength 2,
  activityRecordLength 2, activityRecordDate 4, activityDailyPresenceCounter 2,
  activityDayDistance 2, then activityChangeInfo entries
  (2 bytes each; `activityRecordLength - 12` bytes).
- `VehicleRecord` (31): vehicleOdometerBegin 3, vehicleOdometerEnd 3,
  vehicleFirstUse 4, vehicleLastUse 4, vehicleRegistration 15, vuDataBlockCounter 2.
- `PlaceRecord` (10): entryTime 4, entryTypeDailyWorkPeriod 1,
  dailyWorkPeriodCountry 1, dailyWorkPeriodRegion 1, vehicleOdometerValue 3.
- `SpecificConditionRecord` (5): entryTime 4, specificConditionType 1.

## G2 driver card, amended by (EU) 2021/1228 (TCS_152 / TCS_154)

DF Tachograph_G2 `0500` (AID `FF 53 4D 52 44 54`), total **98300 - 98848** bytes
(certificate spread 4 x (204..341) = 548). Reads are plain APDUs (`Read = SC1`).
Version 2 cards additionally contain the `*_V2`/authentication files; the sizes
below are the amended table (version 2 layout). cardStructureVersion `01 01`
marks a version 2 card.

| EF | FID | SFID | Content | Size |
| --- | --- | --- | --- | --- |
| Application_Identification | `0501` | 1 | 10 counters (see below) | 17 |
| CardMA_Certificate | `C100` | 2 | | 204-341 |
| CardSignCertificate | `C101` | 3 | | 204-341 |
| CA_Certificate | `C108` | 4 | | 204-341 |
| Link_Certificate | `C109` | 5 | | 204-341 |
| Identification | `0520` | 6 | same layout as G1 | 143 |
| Card_Download | `050E` | 7 | lastCardDownload | 4 |
| Driving_Licence_Info | `0521` | 10 | same layout as G1 | 53 |
| Events_Data | `0502` | 12 | 11 sets x 12 x CardEventRecord(24) | 3168 |
| Faults_Data | `0503` | 13 | 2 sets x 24 x CardFaultRecord(24) | 1152 |
| Driver_Activity_Data | `0504` | 14 | 2+2 + 13776 (56 days) | 13780 |
| Vehicles_Used | `0505` | 15 | pointer 2 + 200 x VehicleRecord(48) | 9602 |
| Places | `0506` | 16 | pointer 2 + 112 x PlaceRecord(21) | 2354 |
| Current_Usage | `0507` | 17 | | 19 |
| Control_Activity_Data | `0508` | 18 | | 46 |
| Specific_Conditions | `0522` | 19 | pointer 2 + 112 x 5 | 562 |
| VehicleUnits_Used | `0523` | 20 | pointer 2 + 200 x CardVehicleUnitRecord(10) | 2002 |
| GNSS_Places | `0524` | 21 | pointer 2 + 336 x GNSSAccumulatedDrivingRecord(18) | 6050 |
| Application_Identification_V2 | `0525` | 22 | lengthOfFollowingData 2 + 3x2 + range 2 | 10 |
| Places_Authentication | `0526` | 23 | pointer 2 + 112 x PlaceAuthStatusRecord(5) | 562 |
| GNSS_Places_Authentication | `0527` | 24 | pointer 2 + 336 x 5 | 1682 |
| Border_Crossings | `0528` | 25 | pointer 2 + 1120 x CardBorderCrossingRecord(17) | 19042 |
| Load_Unload_Operations | `0529` | 26 | pointer 2 + 1624 x CardLoadUnloadRecord(20) | 32482 |
| Load_Type_Entries | `0530` | 27 | pointer 2 + 336 x CardLoadTypeEntryRecord(5) | 1682 |
| VU_Configuration | `0540` | 30 | VuConfigurations | 3072 |

G2 element differences vs G1:

- `CardApplicationIdentification` (17): typeOfTachographCardId 1,
  cardStructureVersion 2, noOfEventsPerType 1, noOfFaultsPerType 1,
  activityStructureLength 2, noOfCardVehicleRecords 2, noOfCardPlaceRecords 2,
  noOfGNSSADRecords 2, noOfSpecificConditionRecords 2, noOfCardVehicleUnitRecords 2.
- `VehicleRecord` (48): G1 fields + vehicleIdentificationNumber 17.
- `PlaceRecord` (21): G1 fields + entryGNSSPlaceRecord 11
  (timeStamp 4, gnssAccuracy 1, geoCoordinates 6).
- `CardVehicleUnitRecord` (10): timeStamp 4, manufacturerCode 1, deviceID 1,
  vuSoftwareVersion 4.
- `GNSSAccumulatedDrivingRecord` (18): timeStamp 4 + gnssPlaceRecord 14
  (timeStamp 4, gnssAccuracy 1, geoCoordinates 6, vehicleOdometerValue 3).
- `CardBorderCrossingRecord` (17): countryLeft 1, countryEntered 1,
  gnssPlaceAuthRecord 12 (timeStamp 4, gnssAccuracy 1, geoCoordinates 6,
  authenticationStatus 1), vehicleOdometerValue 3.
- `CardLoadUnloadRecord` (20): timestamp 4, operationType 1, gnssPlaceAuthRecord 12,
  vehicleOdometerValue 3.
- `CardLoadTypeEntryRecord` (5): timestamp 4, loadTypeEntered 1.
- `GeoCoordinates` (6): latitude 3 + longitude 3.

G2 spec ranges: noOfEventsPerType 12..12, noOfFaultsPerType 24..24,
noOfCardVehicleRecords 200..200, noOfCardPlaceRecords 112..112,
noOfCardVehicleUnitRecords 200..200, noOfGNSSADRecords 336..336,
noOfSpecificConditionRecords 112..112, noOfBorderCrossingRecords 1120..1120,
noOfLoadUnloadRecords 1624..1624, noOfLoadTypeEntryRecords 336..336,
activityStructureLength 13776..13776 (56 days x 246 bytes/day).

## Raw dump order

Downloads assembled by this app (and expected of imports) concatenate the EF
contents in the order listed above (G1 set for G1 cards; the G2 set for G2
cards). There is no per-file header in a driver card dump.

## Nations (JRC list)

`00` no info, `01` Austria, `02` Albania, `03` Andorra, `04` Armenia,
`05` Azerbaijan, `06` Belgium, `07` Bulgaria, `08` Bosnia Herzegovina,
`09` Belarus, `0A` Switzerland, `0B` Cyprus, `0C` Czech Republic, `0D` Germany,
`0E` Denmark, `0F` Spain, `10` Estonia, `11` France, `12` Finland,
`13` Liechtenstein, `14` Faroe Islands, `15` United Kingdom, `16` Georgia,
`17` Greece, `18` Hungary, `19` Croatia, `1A` Italy, `1B` Ireland,
`1C` Iceland, `1D` Kazakhstan, `1E` Luxembourg, `1F` Lithuania, `20` Latvia,
`21` Malta, `22` Monaco, `23` Moldova, `24` North Macedonia, `25` Norway,
`26` Netherlands, `27` Portugal, `28` Poland, `29` Romania, `2A` San Marino,
`2B` Russia, `2C` Sweden, `2D` Slovakia, `2E` Slovenia, `2F` Turkmenistan,
`30` Turkiye, `31` Ukraine, `32` Vatican City, `33` Yugoslavia (retired),
`34` Montenegro, `35` Serbia, `36` Uzbekistan, `37` Tajikistan,
`38` Kyrgyz Republic, `39` Israel, `3A..FC` RFU, `FD` European Community,
`FE` Rest of Europe, `FF` Rest of the World.

## Events and faults (EventFaultType)

Category blocks at `x0` boundaries. G1 values also used by G2, which adds
`0B` time conflict and three new blocks (`5x` GNSS faults, `6x` remote
communication module faults, `7x` ITS interface faults).

- `00` general event, `01` driving without an appropriate licence,
  `02` card interruption (discontinuity), `03` data confusion,
  `04` driving without a valid driver's card, `05` unauthorised power supply
  interruption, `06` time adjustment, `07` motion with design speed over 0 km/h,
  `08` lower borders crossing, `09` load/unload types, `0A` RFU,
  `0B` time conflict (G2), `0C..0F` RFU.
- `10` VU security breach attempt, `11` motion sensor authentication failure,
  `12` tachograph card authentication failure, `13` unauthorised change of
  motion sensor, `14` card data input integrity error, `15` stored user data
  integrity error, `16` internal data transfer error, `17` unauthorised case
  opening, `18` hardware sabotage, `19..1F` RFU.
- `20` sensor security breach attempt, `21` authentication failure,
  `22` stored data integrity error, `23` internal data transfer error,
  `24` unauthorised case opening, `25` hardware sabotage, `26..2F` RFU.
- `30` recording equipment fault, `31` VU internal fault, `32` printer fault,
  `33` display fault, `34` downloading fault, `35` sensor fault, `36..3F` RFU.
- `40` card fault, `41..4F` RFU.
- `50` GNSS fault, `51` internal GNSS receiver fault, `52` external GNSS
  receiver fault, `53` external GNSS communication fault, `54` no GNSS position
  data, `55` tamper detection of GNSS, `56` external GNSS facility certificate
  expired (G2 only).
- `60` remote communication module fault (G2 only), `61` RCM fault,
  `62` RCM communication fault.
- `70` ITS interface fault (G2 only).
- `F0..FF` manufacturer specific.

`entryTypeDailyWorkPeriod`: 0 begin/related time, 1 end/related time,
2 begin/manually entered, 3 end/manually entered, 4 begin/assumed by VU,
5 end/assumed by VU (G1). G2 adds 6 begin/GNSS, 7 end/GNSS.

`specificConditionType`: 1 out of scope begin, 2 out of scope end,
3 ferry/train crossing (G1; G2: begin), 4 ferry/train crossing end (G2).
