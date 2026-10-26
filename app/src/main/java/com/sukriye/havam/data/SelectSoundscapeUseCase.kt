package com.sukriye.havam.data

import com.sukriye.havam.model.DayPhase
import com.sukriye.havam.model.NoiseColor
import com.sukriye.havam.model.PressureTendency
import com.sukriye.havam.model.PressureTrend
import com.sukriye.havam.model.Soundscape
import com.sukriye.havam.model.WeatherCode
import com.sukriye.havam.model.WeatherSnapshot
import javax.inject.Inject

/**
 * Audio-Weather: günün evresi, yağış şiddeti ve barometrik basınçtan bir ses manzarası seçer.
 * Gürültü rengi cihazda üretilir (AmbientSoundPlayer); müzik için Spotify arama sorgusu önerilir.
 */
class SelectSoundscapeUseCase @Inject constructor() {

    operator fun invoke(snapshot: WeatherSnapshot, trend: PressureTrend, nowEpochSeconds: Long): Soundscape {
        val phase = dayPhase(snapshot, nowEpochSeconds)
        val c = snapshot.current
        val stormy = WeatherCode.isThunder(c.weatherCode) || WeatherCode.isHeavyRain(c.weatherCode) || c.precipitation >= 4
        val rainy = WeatherCode.isRain(c.weatherCode) || c.precipitation > 0.2
        val lowPressure = c.pressureMsl < 1005 ||
            trend.tendency == PressureTendency.Falling || trend.tendency == PressureTendency.FallingFast

        val base = when {
            stormy -> Triple(
                NoiseColor.Brown,
                "Fırtına sığınağı",
                "Derin kahverengi gürültü gök gürültüsünü yumuşatır" to "stormy night piano ambient"
            )
            WeatherCode.isSnow(c.weatherCode) -> Triple(
                NoiseColor.Brown,
                "Kar sessizliği",
                "Karın yuttuğu sesler gibi yumuşak, boğuk bir doku" to "snowfall calm piano"
            )
            rainy -> Triple(
                NoiseColor.Pink,
                "Yağmur ritmi",
                "Pembe gürültü yağmurun ritmini tamamlar, odaklanmayı artırır" to "rainy day lofi jazz"
            )
            WeatherCode.isFog(c.weatherCode) -> Triple(
                NoiseColor.Brown,
                "Sisli sessizlik",
                "Sisin örttüğü şehir için alçak frekanslı, sakin bir drone" to "foggy ambient drone"
            )
            c.windSpeed >= 30 -> Triple(
                NoiseColor.Pink,
                "Rüzgar senfonisi",
                "Rüzgarın uğultusuna eşlik eden dengeli pembe gürültü" to "windy day indie folk"
            )
            else -> when (phase) {
                DayPhase.Dawn -> Triple(
                    NoiseColor.Pink,
                    "Şafak uyanışı",
                    "Gün ağarırken yumuşak bir başlangıç" to "sunrise acoustic morning"
                )
                DayPhase.Morning -> Triple(
                    NoiseColor.White,
                    "Sabah odağı",
                    "Beyaz gürültü dikkat dağıtan sesleri maskeler" to "morning focus instrumental"
                )
                DayPhase.Noon -> Triple(
                    NoiseColor.White,
                    "Öğle enerjisi",
                    "Parlak gün ışığına eşlik eden canlı bir doku" to "upbeat sunny day"
                )
                DayPhase.Afternoon -> Triple(
                    NoiseColor.Pink,
                    "İkindi akışı",
                    "Günün ikinci yarısında akışta kalmak için" to "afternoon chill vibes"
                )
                DayPhase.Dusk -> Triple(
                    NoiseColor.Pink,
                    "Altın saat",
                    "Gün batımının sıcak tonlarına yavaşlayan bir tempo" to "golden hour indie"
                )
                DayPhase.Night -> Triple(
                    NoiseColor.Brown,
                    "Gece derinliği",
                    "Uykuya hazırlayan derin kahverengi gürültü" to "night ambient sleep"
                )
            }
        }
        val (noise, title, rest) = base
        val (description, query) = rest

        var bpm = when (phase) {
            DayPhase.Dawn -> 80
            DayPhase.Morning -> 105
            DayPhase.Noon -> 120
            DayPhase.Afternoon -> 100
            DayPhase.Dusk -> 90
            DayPhase.Night -> 65
        }
        if (lowPressure) bpm -= 10
        if (rainy) bpm -= 10
        if (stormy) bpm -= 5
        if (c.weatherCode <= 1 && c.isDay) bpm += 5

        return Soundscape(
            phase = phase,
            noiseColor = noise,
            title = title,
            description = if (lowPressure) "$description. Düşük basınç: daha sakin bir tempo" else description,
            suggestedBpm = bpm.coerceIn(55, 130),
            spotifySearchQuery = query
        )
    }

    companion object {
        fun dayPhase(snapshot: WeatherSnapshot, now: Long): DayPhase {
            val day = snapshot.today(now) ?: return if (snapshot.current.isDay) DayPhase.Noon else DayPhase.Night
            val sunrise = day.sunriseEpochSeconds
            val sunset = day.sunsetEpochSeconds
            val solarNoon = (sunrise + sunset) / 2
            return when {
                now < sunrise - 30 * 60 -> DayPhase.Night
                now < sunrise + 60 * 60 -> DayPhase.Dawn
                now < solarNoon - 90 * 60 -> DayPhase.Morning
                now < solarNoon + 90 * 60 -> DayPhase.Noon
                now < sunset - 60 * 60 -> DayPhase.Afternoon
                now < sunset + 40 * 60 -> DayPhase.Dusk
                else -> DayPhase.Night
            }
        }
    }
}
