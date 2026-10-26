package com.clearroom.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

/** 9.0 → "9", 3.25 → "3.25", 7.333 → "7.33": metres the way people say room sizes. */
fun Double.formatMetres(): String {
    val hundredths = (this * 100).roundToLong()
    return if (hundredths % 100 == 0L) {
        (hundredths / 100).toString()
    } else {
        String.format(Locale.ROOT, "%.2f", hundredths / 100.0).trimEnd('0')
    }
}

/** A moment as the phone shows dates, e.g. "27 Oct 2026, 14:30", in [zone]. */
fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(Instant.ofEpochMilli(millis))
