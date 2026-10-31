package eu.colega.tacho.core.card

/**
 * Primitive data types from Appendix 1B of the tachograph card specification
 * (binary formats as laid down in EU Regulation 1360/2002 Appendix 1 Annex B).
 */

/** DateYear: value in years (0..9999), encoded as BCD SIZE(2). */
data class DateYear(val year: Int) {
    init {
        require(year in 0..9999)
    }
}

/** DateMonth: value in months (0..99), encoded as BCD SIZE(1). */
data class DateMonth(val month: Int) {
    init {
        require(month in 0..99)
    }
}

/** DateDay: value in days (0..99), encoded as BCD SIZE(1). */
data class DateDay(val day: Int) {
    init {
        require(day in 0..99)
    }
}

/** Date (SIZE(4)): 4-digit BCD year (2 bytes), month, day. */
data class CardDate(val year: Int, val month: Int, val day: Int) {
    init {
        require(year in 0..9999)
        require(month in 0..99)
        require(day in 0..99)
    }

    override fun toString(): String = "%04d-%02d-%02d".format(year, month, day)

    companion object {
        fun from(r: ByteReader): CardDate {
            val y = r.bcd2()
            val m = r.bcd()
            val d = r.bcd()
            return CardDate(y, m, d)
        }
    }
}

/** TimeStr (SIZE(3)): hour, minute, second as 2-digit BCD groups. */
data class TimeStr(val hour: Int, val minute: Int, val second: Int) {
    init {
        require(hour in 0..99)
        require(minute in 0..99)
        require(second in 0..99)
    }

    override fun toString(): String = "%02d:%02d:%02d".format(hour, minute, second)

    companion object {
        fun from(r: ByteReader): TimeStr {
            val h = r.bcd()
            val m = r.bcd()
            val s = r.bcd()
            return TimeStr(h, m, s)
        }
    }
}

/** DatefTime (SIZE(8)): extended date & time (year 4 BCD digits, month, day, hour, minute, second). */
data class DateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
) {
    init {
        require(year in 0..9999)
        require(month in 0..99)
        require(day in 0..99)
        require(hour in 0..99)
        require(minute in 0..99)
        require(second in 0..99)
    }

    override fun toString(): String =
        "%04d-%02d-%02d %02d:%02d:%02d".format(year, month, day, hour, minute, second)

    companion object {
        fun from(r: ByteReader): DateTime {
            val y = r.bcd4()
            val m = r.bcd()
            val d = r.bcd()
            val h = r.bcd()
            val mi = r.bcd()
            val s = r.bcd()
            return DateTime(y, m, d, h, mi, s)
        }
    }
}

/** TimeReal (SIZE(4)): number of seconds since 1970-01-01 00:00 UTC, unsigned. */
data class TimeReal(val seconds: Long) {
    init {
        require(seconds >= 0 && seconds <= 0xFFFFFFFFL)
    }

    override fun toString(): String {
        val d = java.time.Instant.ofEpochSecond(seconds)
        return d.toString()
    }

    companion object {
        fun from(r: ByteReader): TimeReal = TimeReal(r.u32())
    }
}

/** DurationUnit (SIZE(1)): 00h second, 01h minute, 02h hour, 03h day. */
enum class DurationUnit(val code: Int) {
    SECOND(0),
    MINUTE(1),
    HOUR(2),
    DAY(3);

    companion object {
        fun from(code: Int): DurationUnit = entries.first { it.code == code }
    }
}

/** Duration (SIZE(2)): a duration and its unit. */
data class Duration(val value: Int, val unit: DurationUnit) {
    init {
        require(value in 0..65535)
    }

    override fun toString(): String = "$value $unit"

    companion object {
        fun from(r: ByteReader): Duration {
            val v = r.u8()
            val u = r.u8()
            return Duration(v, DurationUnit.from(u))
        }
    }
}

/** Extended ByteReader helpers for BCD-encoded fields. */
fun ByteReader.bcd(): Int {
    val b = u8()
    val hi = (b ushr 4) and 0x0F
    val lo = b and 0x0F
    if (hi > 9 || lo > 9) throw TruncatedDataException("invalid BCD nibble 0x%02x".format(b))
    return hi * 10 + lo
}

fun ByteReader.bcd2(): Int {
    val hi = bcd()
    val lo = bcd()
    return hi * 100 + lo
}

fun ByteReader.bcd4(): Int {
    val hi = bcd2()
    val lo = bcd2()
    return hi * 10000 + lo
}
