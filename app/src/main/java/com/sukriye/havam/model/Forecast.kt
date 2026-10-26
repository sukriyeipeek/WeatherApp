package com.sukriye.havam.model

// model/ saf Kotlin: Android, Room ya da Retrofit sınıfı içermez.
// Böylece iş kuralları (indeksler, trend tahmini) JVM üzerinde birim testiyle denenebilir.

data class CurrentConditions(
    val epochSeconds: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val weatherCode: Int,
    val precipitation: Double,
    val rainChance: Int,
    val isDay: Boolean,
    // Deniz seviyesine indirgenmiş basınç: hava değişimi trendi için
    val pressureMsl: Double,
    // Yüzey basıncı (deniz seviyesine indirgenmemiş)
    val surfacePressure: Double,
    val cloudCover: Int,
    val uvIndex: Double,
    val visibilityMeters: Double
)

data class HourlyPoint(
    val epochSeconds: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double,
    val pressureMsl: Double,
    val cloudCover: Int,
    val visibilityMeters: Double,
    val windSpeed: Double,
    val windGusts: Double,
    val uvIndex: Double,
    val rainChance: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    // true ise bu saat API'den değil, çevrimdışı trend tahmininden geldi
    val isExtrapolated: Boolean = false
)

data class DailyPoint(
    val epochSeconds: Long,
    val sunriseEpochSeconds: Long,
    val sunsetEpochSeconds: Long,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val uvIndexMax: Double
)

data class AirQuality(
    val aqi: Int,
    val pm25: Double,
    val pm10: Double
)

// Bir noktanın tüm hava durumu verisi. hourly geçmiş 24 saati de içerir (basınç trendi için).
data class WeatherSnapshot(
    val location: WeatherLocation,
    val utcOffsetSeconds: Int,
    val current: CurrentConditions,
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    val airQuality: AirQuality?,
    val fetchedAtMillis: Long
) {
    // Şu andan itibaren gelen saatler (şimdiki saat dahil)
    fun upcomingHours(nowEpochSeconds: Long): List<HourlyPoint> = hourly.filter { it.epochSeconds >= nowEpochSeconds - 3600 }

    fun pastHours(nowEpochSeconds: Long): List<HourlyPoint> = hourly.filter { it.epochSeconds < nowEpochSeconds - 3600 }

    // Günlük kayıtların zamanı, o yerin yerel gece yarısının anıdır
    fun today(nowEpochSeconds: Long): DailyPoint? = daily.lastOrNull { it.epochSeconds <= nowEpochSeconds } ?: daily.firstOrNull()
}

// Verinin ne kadar güvenilir olduğu. Ekrandaki rozet buna göre seçilir.
sealed interface DataFreshness {
    data object Live : DataFreshness
    data class Cached(val ageMinutes: Long) : DataFreshness

    // Önbellekteki saatler bitti, kalan saatler trend ile tahmin edildi
    data class Extrapolated(val ageMinutes: Long, val extrapolatedHours: Int) : DataFreshness
}

// Veri internetten mi geldi yoksa telefona kaydedilmiş eski veri mi?
data class Loaded<T>(
    val data: T,
    val updatedAt: Long,
    val isOffline: Boolean
)

data class SevereWeatherAlert(
    // Aynı uyarı için ikinci bildirim atılmasın diye: konum + saat + tür
    val key: String,
    val locationName: String,
    val title: String,
    val message: String,
    val startEpochSeconds: Long
)
