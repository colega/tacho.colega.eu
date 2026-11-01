# Tachograph card interactions: what we learned

Notes for anyone (human or agent) working on the card download, parsing
or the reader transports. Everything here was verified against a real
generation 2 driver card (an ACS ACR39U reader)
during the November 2026 debugging session. The card and holder shown in examples are always synthetic; never commit real card data.

## The card is a contact chip card, not an NFC card

EU driver tachograph cards have chip contacts. They have no NFC radio.
The phone path is: USB CCID card reader plugged into the phone, the app
talks CCID over Android USB host, then ISO 7816 APDUs to the card.
The original NFC (IsoDep) transport was wrong and was removed.

## Two transports, one protocol

- Android: `UsbCcidTransceiver` (app module) - CCID 1.10 over USB bulk
  endpoints, short APDU exchange level.
- Desktop: the `:cli` module - `./gradlew :cli:run` reads a card through
  PC/SC (javax.smartcardio) with the same `CardDumpReader` from `:core`.

The APDU-level exchange is identical on both. When something fails on
the phone, run the desktop runner with the same reader and card and
compare traces. javax.smartcardio needs `--add-modules java.smartcardio`
at compile time and at runtime (see `cli/build.gradle.kts`).

## CCID transport rules (the desync bug)

Responses and unsolicited notifications share one bulk-IN endpoint. The
reader sends `RDR_to_PC_SlotStatus` notifications on card insert and
removal, and a late response of an earlier command can arrive at any
time. `UsbCcidTransceiver.exchange()` must therefore accept only the
frame with the expected `RDR_to_PC_*` message type AND the echoed
`bSeq` of the current request; every other frame is skipped and logged.

Before that fix, one stale frame consumed as the answer to the
"select generation 2 application" command made the app fall back to
generation 1 file sizes, and a stale 255-byte data block satisfied a
198-byte read. The symptom was
`EF 0502 delivered 1785 bytes, expected 1728` (6 x 12 x 24 = the G1
events size for a G2 card). If you see size mismatches that make no
sense, suspect transport desynchronisation first.

CCID details that matter:

- Frame = 10-byte header + payload. Request types: `0x62` IccPowerOn,
  `0x63` IccPowerOff, `0x65` GetSlotStatus, `0x6F` XfrBlock. Response
  types: `0x80` DataBlock, `0x81` SlotStatus. `bSeq` at byte 6,
  `bStatus` at 7, `bError` at 8.
- IccPowerOn: the ACR39U manual places the voltage/protocol selection
  in the header (byte 7 = voltage, 00h = automatic), not as payload.
  Requesting automatic matches what desktop PC/SC does.
- Readers commonly answer the first power-on with "ICC present but
  inactive" when the card was powered by a previous session. The power
  on retries with slot-status polls in between (see `powerOn()`).
- Every APDU exchange is logged under the `TachoUSB` log tag:
  `adb logcat -s TachoUSB` gives the full phone-side trace.

## Card protocol rules

- AIDs select the generation: `FF534D524454` = generation 2,
  `FF544143484F` = generation 1. A real card answers only the AID that
  matches its own generation; the other select returns 6A82.
- Select file: `00 A4 02 0C 02 <FID hi> <FID lo>`. Read binary:
  `00 B0 <offset hi> <offset lo> <Le>`, offset is a 15-bit value.
- TCS_42: a read whose `offset + Le` passes the EF size answers 6700
  (or 6Cxx with the exact length on some cards). The 6700 form carries
  no length, so you cannot probe sizes. Never over-read: compute every
  file size exactly (see below). The first reader did fixed 255-byte
  reads, over-read almost every file, and produced empty dumps.
- The download order: select application, read
  EF Application_Identification exactly (10 bytes G1, 15 bytes G2
  version 1, then +2 bytes if the version byte at offset 2 is >= 1),
  derive every further EF size from the AppId counters and the layout
  tables in `Layout.kt`, read each file in exact chunks.
- Certificates (C100, C101, C108, C109) have variable size and no
  counter: read the 5-byte header. The outer wrapper is the ISO 7816-4
  certificate template `7F 21` (older structures may use `0x30`), with
  long-form length 81/82. On the real card each of C100/C101/C108 was
  205 bytes. A zero-filled certificate means "not issued on this card"
  (C109 was zero): skip it, do not fail the download.
- EF VU_Configuration (0540) needs secure messaging (SC5). A plain
  read answers 6982. It holds vehicle-unit settings, not driver data;
  the download must skip it and never abort on it.
- The card keeps its own "last card download" timestamp on every
  download session. That is a normal, mandated card behaviour, not a
  write we perform. The whole download is read-only: SELECT, READ
  BINARY, GET RESPONSE - no other instructions exist in `Apdu.kt`.

## Generation 2 data encodings (verified against a real card)

- Name fields (issuing authority, holder surname, holder first names)
  carry a one-byte code page marker (0x01) before 35 bytes of Latin-1
  text, space padded. Generation 1 names are plain text of the full
  field size.
- Card validity dates (issue, valid from, expiry) are 4-byte big-endian
  epoch seconds, aligned to 00:00:01. Birth date stays BCD (a date
  before 1970 cannot be an unsigned timestamp).
- EF sizes: events = 11 sets x n1 x 24 (G1: 6 sets), faults =
  2 x n2 x 24, activity = 4 + activityStructureLength, vehicles = 2 +
  n3 x 48 (G1: 31 without VIN), places = 2 + n4 x 21 (G1: 1 + n4 x 10),
  GNSS places = 2 + n8 x 18 (version 1 records are 15 bytes),
  vehicle units = 2 + n5 x 10, specific conditions = 2 + n7 x 5
  (G1: fixed 280 without pointer).
- A card can hold far more than the classic 28 days of activity (the
  real card gave 127 daily records spanning months). Do not assume a
  28-day window anywhere in the UI.

## The .tcd container

The portable dump format lives in `core/.../TcdContainer.kt`:
for each EF in canonical order (`driverCardDumpFileIds()`, deduplicated
G2 then G1 lists), 2-byte big-endian FID + 4-byte big-endian length +
payload. The app export/import and the desktop runner use the same
codec. The canonical list must not contain duplicate FIDs; a
concatenation of the G1 and G2 lists once shipped 14 duplicated files
in every export.

## Local test data and privacy

- `testdata/card/` holds the real card dump: `card.tcd` plus one
  `XXXX.bin` per elementary file. Regenerate with `./gradlew :cli:run`
  while a reader is plugged in. The directory is git-ignored.
- `testdata/screenshots/` holds emulator screenshots for review without
  reinstalling. Also git-ignored.
- Never commit card data: it is personal data (name, card number,
  locations). The dump directory is git-ignored and the git history
  must stay free of it.

## Emulator workflow (no USB in the emulator)

The Android emulator has no USB host passthrough. Use the container:

1. Create/ boot an AVD headless:
   `$ANDROID_HOME/emulator/emulator -avd <name> -no-window -no-audio
   -no-boot-anim -no-snapshot -gpu host -port 5554`
   On this Mac set `ANDROID_AVD_HOME=$HOME/.toolbox/config/.android/avd`
   (the toolbox environment redirects the AVD home).
2. Install: `adb -s emulator-5554 install -r
   app/build/outputs/apk/debug/app-debug.apk`. Run `assembleDebug`
   first - a successful `compileDebugKotlin` does not refresh the APK
   (this mistake cost one debugging round).
3. Seed the card: `adb push testdata/card/card.tcd /data/local/tmp/`
   then
   `adb shell "cat /data/local/tmp/card.tcd | run-as eu.colega.tacho
   sh -c 'mkdir -p files && cat > files/last-card.tcd'"`.
   The app restores `last-card.tcd` on start, no file picker needed.
4. Spanish UI: `adb shell cmd locale set-app-locales eu.colega.tacho
   --locales es-ES`.
5. Navigate with `input tap` (bottom nav at y=2272 on the Pixel 5 AVD;
   x = 135 / 405 / 675 / 945) and capture with
   `adb exec-out screencap -p > file.png`.
6. `adb -s emulator-5554` - always pass the serial; plain `adb -e`
   fails when more than one transport exists.
7. UI Automator dump (`uiautomator dump /sdcard/ui.xml`) gives element
   bounds; the "show layout bounds" system setting did not render over
   the app in this setup.

## Analysis rules (Regulation 561/2006)

Implemented in `core/.../analysis/ActivityAnalysis.kt`, minute
resolution from the card's activity change records:

- Daily driving: 9 h, extendable to 10 h twice per fixed ISO week.
  A day over 10 h is always a violation; a day over 9 h only becomes
  one when the week has a third such day. Never flag a 9 h 15 day by
  itself (this exact false positive shipped once).
- Weekly 56 h, two consecutive weeks 90 h, break after 4 h 30 of
  driving (45 min, or split 15 + 30 in order).
- Daily rest (Art. 8): rest runs merge across midnight on an absolute
  minute timeline. A work stretch beyond 24 h without a qualifying
  rest start is a violation; a fourth reduced rest (9-11 h) between
  weekly rests (>= 45 h) is a violation. Gaps without card records
  count as rest when at least 9 h long. The reduced weekly rest
  (24 h every second week with compensation) is NOT implemented.
- Colour language across the UI: green = normal driving day,
  amber = extension used (9-10 h), red = over 10 h, blue = rest.
  9-10 h days must not be red.

## Debugging workflow that worked

1. Reproduce on the desktop with `:cli` (full APDU trace on real
   hardware) before touching the app.
2. Fix `:core` with scripted-card unit tests, then confirm on the
   desktop runner.
3. Use the emulator with the seeded container for UI verification and
   store screenshots in `testdata/screenshots/`.
4. Watch for data-dependent layout bugs (calendar last row, chart
   column widths) - they only appear with real, multi-month data.
