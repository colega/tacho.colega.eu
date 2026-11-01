package eu.colega.tacho.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import eu.colega.tacho.core.card.CardDate
import eu.colega.tacho.core.card.TimeReal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val zone: ZoneId = ZoneId.systemDefault()

/** Locale-aware date rendering for the UI (medium style, e.g. "20 Jan 2025"). */
@Composable
fun LocalDate.displayDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(currentLocale())
        .format(this)

/** Compact chart label, e.g. "20 ene". */
@Composable
fun LocalDate.displayDateShort(): String =
    DateTimeFormatter.ofPattern("d MMM", currentLocale()).format(this)

/** Local date-time rendering for card timestamps, e.g. "20 Jan 2025, 14:30". */
@Composable
fun TimeReal.displayDateTime(): String {
    val local = Instant.ofEpochSecond(seconds).atZone(zone)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(currentLocale())
        .format(local)
    val time = DateTimeFormatter.ofPattern("HH:mm").format(local)
    return "$date, $time"
}

/** The locale from the observable Compose configuration. */
@Composable
private fun currentLocale(): java.util.Locale =
    LocalConfiguration.current.locales.get(0)


/** Card date rendering; unpadded or zeroed dates fall back to the raw form. */
@Composable
fun CardDate.displayDate(): String =
    if (year in 1900..9999 && month in 1..12 && day in 1..31) {
        LocalDate.of(year, month, day).displayDate()
    } else {
        toString()
    }
