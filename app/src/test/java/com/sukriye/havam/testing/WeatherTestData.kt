package com.sukriye.havam.testing

import com.sukriye.havam.model.CurrentConditions
import com.sukriye.havam.model.DailyPoint
import com.sukriye.havam.model.HourlyPoint
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot

// Testler için deterministik hava durumu verisi. NOW: yerel saatle 15:00 (UTC+3)
object WeatherTestData {
    const val OFFSET = 3 * 3600
    const val MIDNIGHT = 1_792_962_000L
    const val NOW = MIDNIGHT + 15 * 3600

    fun hour(
        offsetHours: Int,
        temperature: Double = 18.0,
        humidity: Int = 55,
        pressure: Double = 1015.0,
        wind: Double = 8.0,
        rainChance: Int = 0,
        precipitation: Double = 0.0,
        code: Int = 1,
        cloud: Int = 30,
        uv: Double = 3.0,
        visibility: Double = 24_000.0
    ) = HourlyPoint(
        epochSeconds = NOW + offsetHours * 3600L,
        temperature = temperature,
        apparentTemperature = temperature,
        humidity = humidity,
        dewPoint = 9.0,
        pressureMsl = pressure,
        cloudCover = cloud,
        visibilityMeters = visibility,
        windSpeed = wind,
        windGusts = wind * 1.5,
        uvIndex = uv,
        rainChance = rainChance,
        precipitation = precipitation,
        weatherCode = code,
        isDay = offsetHours in -8..3
    )

    fun snapshot(
        hourly: List<HourlyPoint> = (-24..47).map { hour(it) },
        current: CurrentConditions = currentFrom(hourly.first { it.epochSeconds == NOW })
    ) = WeatherSnapshot(
        location = WeatherLocation("test", "Test", 41.0, 29.0),
        utcOffsetSeconds = OFFSET,
        current = current,
        hourly = hourly,
        daily = listOf(
            day(MIDNIGHT - 86_400),
            day(MIDNIGHT),
            day(MIDNIGHT + 86_400)
        ),
        airQuality = null,
        fetchedAtMillis = NOW * 1000
    )

    // Gün doğumu 06:40, gün batımı 17:20 (yerel)
    private fun day(midnight: Long) = DailyPoint(
        epochSeconds = midnight,
        sunriseEpochSeconds = midnight + 6 * 3600 + 40 * 60,
        sunsetEpochSeconds = midnight + 17 * 3600 + 20 * 60,
        temperatureMax = 22.0,
        temperatureMin = 12.0,
        uvIndexMax = 4.0
    )

    fun currentFrom(h: HourlyPoint) = CurrentConditions(
        epochSeconds = h.epochSeconds,
        temperature = h.temperature,
        apparentTemperature = h.apparentTemperature,
        humidity = h.humidity,
        windSpeed = h.windSpeed,
        windGusts = h.windGusts,
        weatherCode = h.weatherCode,
        precipitation = h.precipitation,
        rainChance = h.rainChance,
        isDay = h.isDay,
        pressureMsl = h.pressureMsl,
        surfacePressure = h.pressureMsl - 5,
        cloudCover = h.cloudCover,
        uvIndex = h.uvIndex,
        visibilityMeters = h.visibilityMeters
    )
}
