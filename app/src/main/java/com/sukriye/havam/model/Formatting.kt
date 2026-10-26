package com.sukriye.havam.model

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

private val turkish: Locale = Locale.forLanguageTag("tr")
private val clockFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", turkish)

// Saat, verinin geldiği yerin saat dilimine göre yazılır (telefonun değil). Örnek: "18:20"
fun formatClock(epochSeconds: Long, utcOffsetSeconds: Int): String = Instant.ofEpochSecond(epochSeconds)
    .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
    .format(clockFormatter)

fun formatWindow(window: TimeWindow, utcOffsetSeconds: Int): String =
    "${formatClock(window.startEpochSeconds, utcOffsetSeconds)}–${formatClock(window.endEpochSeconds, utcOffsetSeconds)}"

// Örnek: 3.25 -> "3,3"
fun Double.oneDecimal(): String = String.format(turkish, "%.1f", this)

// Telefonun saatiyle. Örnek: "14:30"
fun clockText(millis: Long): String = SimpleDateFormat("HH:mm", turkish).format(Date(millis))

// Örnek: "25 Ekim Pazar"
fun todayText(): String = SimpleDateFormat("d MMMM EEEE", turkish).format(Date())
