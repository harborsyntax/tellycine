package app.tellycine.ui.components

import android.text.format.DateUtils
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

fun formatTime(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

fun formatDay(ms: Long): String = DateUtils.getRelativeTimeSpanString(
    ms, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_SHOW_WEEKDAY,
).toString()

fun formatRelative(ms: Long): String = DateUtils.getRelativeTimeSpanString(
    ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
).toString()

fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

/** A date such as "2019-05-19" in the device's style ("19 May 2019"), or null if it isn't one. */
fun formatIsoDate(date: String?): String? =
    date?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull() }

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatCount(n: Int): String = "%,d".format(n)
