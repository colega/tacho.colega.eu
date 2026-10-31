package eu.colega.tacho.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware date rendering for the UI (medium style, e.g. "20 Jan 2025"). */
fun LocalDate.displayDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(this)
