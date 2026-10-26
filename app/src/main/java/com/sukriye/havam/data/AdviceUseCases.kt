package com.sukriye.havam.data

import com.sukriye.havam.model.Advice
import com.sukriye.havam.model.SevereWeatherAlert
import com.sukriye.havam.model.WeatherCode
import com.sukriye.havam.model.WeatherSnapshot
import com.sukriye.havam.model.formatClock
import javax.inject.Inject
import kotlin.math.roundToInt

// "Ne giysem?" kartındaki öneriler. Basit kurallar: sıcaklık, yağmur, kar, rüzgar, güneş, hava kalitesi.
class GenerateOutfitAdviceUseCase @Inject constructor() {
    operator fun invoke(snapshot: WeatherSnapshot, nowEpochSeconds: Long): List<Advice> {
        val c = snapshot.current
        val advice = mutableListOf<Advice>()
        val temp = c.apparentTemperature
        // Şu anki saat ve sonraki 11 saatte en yüksek yağmur olasılığı
        val rainChance = snapshot.upcomingHours(nowEpochSeconds).take(12).maxOfOrNull { it.rainChance } ?: c.rainChance

        advice += when {
            temp < 5 -> Advice("🧥", "Kalın mont, bere ve eldiven giy")
            temp < 12 -> Advice("🧥", "Mont giymeden çıkma")
            temp < 18 -> Advice("🧶", "Hırka ya da ince bir ceket al")
            temp < 25 -> Advice("👕", "Tişört ve ince bir üst yeterli")
            else -> Advice("🩳", "İnce ve açık renkli giysiler tercih et")
        }
        when {
            WeatherCode.isThunder(c.weatherCode) -> advice += Advice("⛈️", "Fırtına var, mümkünse dışarı çıkma")
            WeatherCode.isSnow(c.weatherCode) -> advice += Advice("🥾", "Kar var, kaymayan ayakkabı giy")
        }
        if (rainChance >= 40) {
            advice += Advice("☂️", "Önümüzdeki saatlerde yağmur olabilir (%$rainChance), şemsiyeni al")
        }
        if (c.windSpeed >= 30) {
            advice += Advice("🌬️", "Rüzgar sert, şapka ve şemsiye zorlanabilir")
        }
        if (c.isDay && c.uvIndex >= 6) {
            advice += Advice("🕶️", "UV ${c.uvIndex.roundToInt()}: güneş gözlüğü ve güneş kremi unutma")
        }
        snapshot.airQuality?.let {
            if (it.aqi > 60) advice += Advice("😷", "Hava kalitesi kötü, dışarıda uzun süre kalma")
        }
        return advice
    }
}

// Arka plan senkronundan sonra çalışır: önümüzdeki 6 saatte şiddetli hava var mı?
class DetectSevereWeatherUseCase @Inject constructor() {
    operator fun invoke(snapshot: WeatherSnapshot, nowEpochSeconds: Long): SevereWeatherAlert? {
        val hours = snapshot.upcomingHours(nowEpochSeconds).take(6).filterNot { it.isExtrapolated }
        for (h in hours) {
            val reason = when {
                WeatherCode.isThunder(h.weatherCode) -> "Gök gürültülü fırtına" to "Ani sağanak, yıldırım ve dolu riski"
                h.precipitation >= 10 -> "Kuvvetli yağış" to "Saatte ${h.precipitation.roundToInt()} mm yağış, su baskını riski"
                h.windGusts >= 70 -> "Fırtına şiddetinde rüzgar" to "${h.windGusts.roundToInt()} km/sa hamleler, devrilme riski"
                WeatherCode.isFreezing(h.weatherCode) -> "Dondurucu yağış" to "Yollarda gizli buzlanma, kaza riski"
                h.weatherCode == 75 || h.weatherCode == 86 -> "Yoğun kar" to "Ulaşımda aksama olabilir"
                else -> null
            } ?: continue
            val clock = formatClock(h.epochSeconds, snapshot.utcOffsetSeconds)
            return SevereWeatherAlert(
                key = "${snapshot.location.key}:${h.epochSeconds}:${reason.first}",
                locationName = snapshot.location.name,
                title = "${snapshot.location.name}: ${reason.first}",
                message = "Saat $clock civarı. ${reason.second}.",
                startEpochSeconds = h.epochSeconds
            )
        }
        return null
    }
}
