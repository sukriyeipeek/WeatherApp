package com.sukriye.havam.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Uygulamanın ortak renkleri. Hava paneli temadan bağımsız hep gökyüzü renklerinde çizilir.
val SkyLight = Color(0xFF4FC3F7)
val SkyDark = Color(0xFF1E6FE0)
val Night = Color(0xFF0B1426)
val NightCard = Color(0xFF131F38)
val CardBorder = Color.White.copy(alpha = 0.12f)
val SoftWhite = Color.White.copy(alpha = 0.7f)

// Liste, harita, favoriler ve ayarlar ekranlarının açık/koyu temaya göre değişen renkleri
@Immutable
data class AppPalette(
    val isDark: Boolean,
    // Zemin: yukarıdan aşağı üç duraklı yumuşak geçiş ve köşelerde iki hafif ışık
    val backgroundTop: Color,
    val backgroundMiddle: Color,
    val backgroundBottom: Color,
    val glow: Color,
    val glowSecondary: Color,
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val textSoft: Color,
    val mapLand: Color
)

val DarkPalette = AppPalette(
    isDark = true,
    backgroundTop = Night,
    backgroundMiddle = Color(0xFF10203D),
    backgroundBottom = Color(0xFF191A3A),
    glow = SkyLight.copy(alpha = 0.16f),
    glowSecondary = Color(0xFF7B61FF).copy(alpha = 0.12f),
    card = NightCard.copy(alpha = 0.82f),
    cardBorder = CardBorder,
    text = Color.White,
    textSoft = SoftWhite,
    mapLand = NightCard
)

val LightPalette = AppPalette(
    isDark = false,
    backgroundTop = Color(0xFFDDEEFF),
    backgroundMiddle = Color(0xFFF2F7FD),
    backgroundBottom = Color(0xFFECE8FA),
    glow = SkyLight.copy(alpha = 0.28f),
    glowSecondary = Color(0xFFB39DDB).copy(alpha = 0.22f),
    card = Color.White.copy(alpha = 0.78f),
    cardBorder = Color(0xFF1E3A5F).copy(alpha = 0.10f),
    text = Color(0xFF0F1B2D),
    textSoft = Color(0xFF51607A),
    mapLand = Color(0xFFD6E5F7)
)

val LocalAppPalette = staticCompositionLocalOf { DarkPalette }

// Sıcaklık → renk: soğuk mor/mavi, ılık yeşil/sarı, sıcak turuncu/kırmızı
val temperatureStops = listOf(
    -10.0 to Color(0xFF7B61FF),
    0.0 to Color(0xFF3FA7F5),
    10.0 to Color(0xFF3DDC97),
    20.0 to Color(0xFFF9D648),
    30.0 to Color(0xFFFF8C42),
    40.0 to Color(0xFFE63946)
)

fun temperatureColor(temperature: Double): Color {
    val t = temperature.coerceIn(temperatureStops.first().first, temperatureStops.last().first)
    val upper = temperatureStops.indexOfFirst { it.first >= t }.coerceAtLeast(1)
    val (t0, c0) = temperatureStops[upper - 1]
    val (t1, c1) = temperatureStops[upper]
    return lerp(c0, c1, ((t - t0) / (t1 - t0)).toFloat())
}

// Avrupa Hava Kalitesi İndeksi renkleri: yeşil iyi, kırmızı kötü
fun airQualityColor(aqi: Int): Color = when {
    aqi <= 20 -> Color(0xFF50E3C2)
    aqi <= 40 -> Color(0xFF7ED957)
    aqi <= 60 -> Color(0xFFF5D547)
    aqi <= 80 -> Color(0xFFFF9F43)
    aqi <= 100 -> Color(0xFFFF5E57)
    else -> Color(0xFFB76BF0)
}
