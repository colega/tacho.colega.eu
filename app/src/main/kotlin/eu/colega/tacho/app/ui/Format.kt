package eu.colega.tacho.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import eu.colega.tacho.core.card.TimeReal

private val zone: ZoneId = ZoneId.systemDefault()

/** Locale-aware date rendering for the UI (medium style, e.g. "20 Jan 2025"). */
fun LocalDate.displayDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(this)

/** Local date-time rendering for card timestamps, e.g. "20 Jan 2025, 14:30". */
fun TimeReal.displayDateTime(): String {
    val local = Instant.ofEpochSecond(seconds).atZone(zone)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(local)
    val time = DateTimeFormatter.ofPattern("HH:mm").format(local)
    return "$date, $time"
}
