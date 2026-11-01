package eu.colega.tacho.cli

import eu.colega.tacho.core.tachograph.Activity
import eu.colega.tacho.core.tachograph.DriverCardDump
import eu.colega.tacho.core.tachograph.Layout
import eu.colega.tacho.core.tachograph.TcdContainer
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Builds a synthetic driver card dump for public screenshots and demos.
 * Every byte is generated here: the holder is a fictional driver, so the
 * derived screenshots carry no personal data and can be committed to the
 * landing site.
 */
object DemoCard {

    private const val NAME = "MARTIN RUIZ"
    private const val FIRST_NAMES = "ANA"

    fun dump(days: Int = 70): DriverCardDump {
        val files = mutableMapOf<Int, ByteArray>()
        files[Layout.EF_APPLICATION_IDENTIFICATION] = applicationId()
        files[Layout.EF_CARD_MA_CERTIFICATE] = certificate(205, 1)
        files[Layout.EF_CARD_SIGN_CERTIFICATE] = certificate(205, 2)
        files[Layout.EF_CA_CERTIFICATE] = certificate(205, 3)
        files[Layout.EF_IDENTIFICATION] = identification()
        files[Layout.EF_CARD_DOWNLOAD] = ByteArray(4)
        files[Layout.EF_DRIVING_LICENCE_INFO] = ByteArray(53)
        files[Layout.EF_EVENTS_DATA] = ByteArray(11 * 12 * 24)
        files[Layout.EF_FAULTS_DATA] = ByteArray(2 * 24 * 24)
        files[Layout.EF_DRIVER_ACTIVITY_DATA] = activity(days)
        files[Layout.EF_VEHICLES_USED] = ByteArray(2 + 200 * 48)
        files[Layout.EF_PLACES] = ByteArray(2 + 112 * 21)
        files[Layout.EF_CURRENT_USAGE] = ByteArray(19)
        files[Layout.EF_CONTROL_ACTIVITY_DATA] = ByteArray(46)
        files[Layout.EF_SPECIFIC_CONDITIONS] = ByteArray(2 + 112 * 5)
        files[Layout.EF_VEHICLE_UNITS_USED] = ByteArray(2 + 200 * 10)
        files[Layout.EF_GNSS_PLACES] = ByteArray(2 + 336 * 18)
        files[Layout.EF_APPLICATION_IDENTIFICATION_V2] = byteArrayOf(
            0x00, 0x08,
            0x00, 0x3C,
            0x00, 0x1E,
            0x00, 0x1E,
            0x0C, 0x00,
        )
        files[Layout.EF_PLACES_AUTHENTICATION] = ByteArray(2 + 112 * 5)
        files[Layout.EF_GNSS_PLACES_AUTHENTICATION] = ByteArray(2 + 336 * 5)
        files[Layout.EF_BORDER_CROSSINGS] = ByteArray(2 + 60 * 17)
        files[Layout.EF_LOAD_UNLOAD_OPERATIONS] = ByteArray(2 + 30 * 20)
        files[Layout.EF_LOAD_TYPE_ENTRIES] = ByteArray(2 + 30 * 5)
        return DriverCardDump(files)
    }

    fun container(days: Int = 70): ByteArray = TcdContainer.toBytes(dump(days))

    /** 17-byte amended G2v2 AppId: driver card, n1=12, n2=24 and the usual counters. */
    private fun applicationId() = byteArrayOf(
        0x00, 0x01, 0x01, 0x0C, 0x18,
        0x35, 0xD0.toByte(),
        0x00, 0xC8.toByte(),
        0x00, 0x70,
        0x01, 0x50,
        0x00, 0x70,
        0x00, 0xC8.toByte(),
    )

    /** Certificate-shaped 7F21 blob; contents are synthetic, never verified. */
    private fun certificate(size: Int, seed: Int): ByteArray {
        val out = ByteArray(size)
        out[0] = 0x7F
        out[1] = 0x21
        out[2] = 0x81.toByte()
        out[3] = (size - 4).toByte()
        for (i in 4 until size) out[i] = ((i * seed + 7) and 0xFF).toByte()
        return out
    }

    /** EF Identification (143 bytes) with a fictional Spanish driver. */
    private fun identification(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x0F) // cardIssuingMemberState: Spain
        writeAscii(out, "E123456789012345", 16)
        writeName(out, "SEVILLA", 36)
        writeEpoch(out, LocalDate.of(2025, 6, 10)) // issue
        writeEpoch(out, LocalDate.of(2025, 6, 12)) // valid from
        writeEpoch(out, LocalDate.of(2030, 6, 12)) // expiry
        writeName(out, NAME, 36)
        writeName(out, FIRST_NAMES, 36)
        // Birth date stays BCD in generation 2.
        out.write(byteArrayOf(0x90.toByte(), 0x04, 0x12, 0x00))
        writeAscii(out, "es", 2)
        assertEquals(143, out.size())
        return out.toByteArray()
    }

    /**
     * EF Driver_Activity_Data: a ring of daily records, newest first,
     * covering [days] days ending yesterday. The week pattern is compliant:
     * regular weekly rests every weekend, one reduced weekly rest that is
     * compensated by the next weekend's long rest.
     */
    private fun activity(days: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ByteArray(4)) // activity pointers, skipped by the parser
        val today = LocalDate.now(ZoneOffset.UTC)
        // Day plans oldest first, written newest first afterwards.
        val plans = (days - 1 downTo 0).map { back -> plan(today.minusDays(back.toLong())) }
        var previousLength = 0
        for ((index, entry) in plans.reversed().withIndex()) {
            val changes = entry.first
            val date = entry.second
            val record = ByteArrayOutputStream()
            // previousRecordLength: zero marks the oldest record.
            val isOldest = index == plans.size - 1
            val prev = if (isOldest) 0 else previousLength
            val length = 12 + 2 * changes.size
            record.write((prev ushr 8) and 0xFF)
            record.write(prev and 0xFF)
            record.write((length ushr 8) and 0xFF)
            record.write(length and 0xFF)
            val seconds = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            repeat(4) { shift -> record.write(((seconds ushr (8 * (3 - shift))) and 0xFFL).toInt()) }
            record.write(0x00); record.write(0x01) // dailyPresenceCounter
            record.write(0x00); record.write(0x00) // dayDistance
            for ((minute, activity) in changes) {
                val word = (0 shl 15) or (0 shl 14) or (0 shl 13) or (activity.code shl 11) or minute
                record.write((word ushr 8) and 0xFF)
                record.write(word and 0xFF)
            }
            previousLength = length
            out.write(record.toByteArray())
        }
        return out.toByteArray()
    }

    /** One day's (changes, date): a compliant driver week with variety. */
    private fun plan(date: LocalDate): Pair<List<Pair<Int, Activity>>, LocalDate> {
        val dayOfWeek = date.dayOfWeek.value // 1 = Monday
        val changes = mutableListOf<Pair<Int, Activity>>()
        when {
            // Full rest days (weekends).
            dayOfWeek == 6 || dayOfWeek == 7 -> changes += 0 to Activity.BREAK_OR_REST
            // One mid-week extended day in the second half of the period.
            dayOfWeek == 3 && date.dayOfMonth % 2 == 0 -> {
                // 9 h 30 legally: 2 h 15 + 15 min split, 2 h 15 + 30 min,
                // then 4 h 30 with a full 45 min break, 30 min more.
                changes += 0 to Activity.BREAK_OR_REST
                changes += 360 to Activity.DRIVING // 2 h 15
                changes += 495 to Activity.BREAK_OR_REST // 15 min
                changes += 510 to Activity.DRIVING // 2 h 15
                changes += 645 to Activity.BREAK_OR_REST // 30 min
                changes += 675 to Activity.DRIVING // 4 h 30
                changes += 945 to Activity.BREAK_OR_REST // 45 min
                changes += 990 to Activity.DRIVING // 30 min
                changes += 1020 to Activity.WORK // 2 h
                changes += 1140 to Activity.BREAK_OR_REST
            }
            // Regular working day: 6 h 45 of driving with a full 45 min
            // break and an 11 h plus nightly rest.
            else -> {
                changes += 0 to Activity.BREAK_OR_REST
                changes += 360 to Activity.DRIVING // 4 h
                changes += 600 to Activity.BREAK_OR_REST // 45 min
                changes += 645 to Activity.DRIVING // 2 h 45
                changes += 810 to Activity.WORK // 1 h 30
                changes += 900 to Activity.BREAK_OR_REST
            }
        }
        return changes to date
    }

    private fun writeAscii(out: ByteArrayOutputStream, text: String, size: Int) {
        val bytes = text.toByteArray(Charsets.ISO_8859_1).copyOf(size)
        out.write(bytes)
    }

    /** Generation 2 Name: one code page byte plus space-padded text. */
    private fun writeName(out: ByteArrayOutputStream, text: String, size: Int) {
        out.write(0x01)
        writeAscii(out, text, size - 1)
    }

    private fun writeEpoch(out: ByteArrayOutputStream, date: LocalDate) {
        val seconds = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        repeat(4) { shift -> out.write(((seconds ushr (8 * (3 - shift))) and 0xFFL).toInt()) }
    }

    private fun assertEquals(expected: Int, actual: Int) {
        check(expected == actual) { "demo identification is $actual bytes, expected $expected" }
    }
}
