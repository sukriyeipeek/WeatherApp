package com.sukriye.havam.data

import com.sukriye.havam.model.AirQuality
import com.sukriye.havam.model.CurrentConditions
import com.sukriye.havam.model.DailyPoint
import com.sukriye.havam.model.HourlyPoint
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Open-Meteo cevabının bize lazım olan kısmı (DTO) ve onu veritabanı/uygulama modellerine çeviren
// fonksiyonlar. Cevap sütun biçiminde gelir: hourly.time[i] ile hourly.temperature_2m[i] aynı saate aittir.
// timeformat=unixtime ile tüm zamanlar epoch saniyesi.
// Saatlik listelerde bazı modeller bazı saatler için null döndürebildiği için elemanlar nullable.
@Serializable
data class ForecastDto(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto
)

@Serializable
data class CurrentDto(
    val time: Long,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val humidity: Int? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("weather_code") val weatherCode: Int = 0,
    val precipitation: Double? = null,
    @SerialName("precipitation_probability") val rainChance: Int? = null,
    @SerialName("is_day") val isDay: Int = 1,
    @SerialName("pressure_msl") val pressureMsl: Double? = null,
    @SerialName("surface_pressure") val surfacePressure: Double? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("uv_index") val uvIndex: Double? = null,
    val visibility: Double? = null
)

@Serializable
data class HourlyDto(
    val time: List<Long>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?> = emptyList(),
    @SerialName("dew_point_2m") val dewPoint: List<Double?> = emptyList(),
    @SerialName("pressure_msl") val pressureMsl: List<Double?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val rainChance: List<Int?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList()
)

@Serializable
data class DailyDto(
    val time: List<Long>,
    val sunrise: List<Long> = emptyList(),
    val sunset: List<Long> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList()
)

// 81 il tek istekte sorulunca her il için bir tane bu nesneden gelir (JSON dizisi)
@Serializable
data class SummaryDto(val current: SummaryCurrentDto)

@Serializable
data class SummaryCurrentDto(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int = 0,
    @SerialName("is_day") val isDay: Int = 1
)

// Hava kalitesi servisi: { "current": { "european_aqi": 23, "pm2_5": 4.1, "pm10": 9.8 } }
@Serializable
data class AirQualityDto(val current: AirQualityCurrentDto)

@Serializable
data class AirQualityCurrentDto(
    @SerialName("european_aqi") val aqi: Int? = null,
    @SerialName("pm2_5") val pm25: Double? = null,
    @SerialName("pm10") val pm10: Double? = null
)

// Ağ (DTO) -> veritabanı (Entity) -> model dönüşümleri. Eksik değerler makul varsayılanlarla doldurulur.

private const val STANDARD_PRESSURE = 1013.25
private const val CLEAR_VISIBILITY = 24_000.0

fun ForecastDto.toLocationEntity(location: WeatherLocation, air: AirQualityDto?, fetchedAtMillis: Long): LocationWeatherEntity {
    val c = current
    val humidity = c.humidity ?: hourly.humidity.firstNotNullOfOrNull { it } ?: 50
    return LocationWeatherEntity(
        locationKey = location.key,
        name = location.name,
        latitude = location.latitude,
        longitude = location.longitude,
        isDevice = location.isDeviceLocation,
        utcOffsetSeconds = utcOffsetSeconds,
        fetchedAtMillis = fetchedAtMillis,
        current = CurrentEntity(
            epochSeconds = c.time,
            temperature = c.temperature,
            apparentTemperature = c.apparentTemperature ?: c.temperature,
            humidity = humidity,
            windSpeed = c.windSpeed ?: 0.0,
            windGusts = c.windGusts ?: c.windSpeed ?: 0.0,
            weatherCode = c.weatherCode,
            precipitation = c.precipitation ?: 0.0,
            rainChance = c.rainChance ?: 0,
            isDay = c.isDay == 1,
            pressureMsl = c.pressureMsl ?: STANDARD_PRESSURE,
            surfacePressure = c.surfacePressure ?: c.pressureMsl ?: STANDARD_PRESSURE,
            cloudCover = c.cloudCover ?: 0,
            uvIndex = c.uvIndex ?: 0.0,
            visibilityMeters = c.visibility ?: CLEAR_VISIBILITY
        ),
        air = air?.current?.let { a ->
            a.aqi?.let { AirEntity(aqi = it, pm25 = a.pm25 ?: 0.0, pm10 = a.pm10 ?: 0.0) }
        }
    )
}

// Ayrı listeleri sıra numarasıyla birleştirip saat saat tek satıra çeviriyoruz.
// Sıcaklığı olmayan saatler atlanır.
fun ForecastDto.toHourlyEntities(locationKey: String): List<HourlyEntity> = with(hourly) {
    time.indices.mapNotNull { i ->
        val temperature = temperature.getOrNull(i) ?: return@mapNotNull null
        val humidity = humidity.getOrNull(i) ?: 50
        HourlyEntity(
            locationKey = locationKey,
            epochSeconds = time[i],
            temperature = temperature,
            apparentTemperature = apparentTemperature.getOrNull(i) ?: temperature,
            humidity = humidity,
            dewPoint = dewPoint.getOrNull(i) ?: CalculateLifestyleIndicesUseCase.dewPoint(temperature, humidity),
            pressureMsl = pressureMsl.getOrNull(i) ?: STANDARD_PRESSURE,
            cloudCover = cloudCover.getOrNull(i) ?: 0,
            visibilityMeters = visibility.getOrNull(i) ?: CLEAR_VISIBILITY,
            windSpeed = windSpeed.getOrNull(i) ?: 0.0,
            windGusts = windGusts.getOrNull(i) ?: windSpeed.getOrNull(i) ?: 0.0,
            uvIndex = uvIndex.getOrNull(i) ?: 0.0,
            rainChance = rainChance.getOrNull(i) ?: 0,
            precipitation = precipitation.getOrNull(i) ?: 0.0,
            weatherCode = weatherCode.getOrNull(i) ?: 0,
            isDay = (isDay.getOrNull(i) ?: 1) == 1
        )
    }
}

fun ForecastDto.toDailyEntities(locationKey: String): List<DailyEntity> = with(daily) {
    time.indices.mapNotNull { i ->
        val sunrise = sunrise.getOrNull(i) ?: return@mapNotNull null
        val sunset = sunset.getOrNull(i) ?: return@mapNotNull null
        DailyEntity(
            locationKey = locationKey,
            epochSeconds = time[i],
            sunriseEpochSeconds = sunrise,
            sunsetEpochSeconds = sunset,
            temperatureMax = temperatureMax.getOrNull(i) ?: 0.0,
            temperatureMin = temperatureMin.getOrNull(i) ?: 0.0,
            uvIndexMax = uvIndexMax.getOrNull(i) ?: 0.0
        )
    }
}

fun LocationWeatherEntity.toLocation() = WeatherLocation(
    key = locationKey,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isDeviceLocation = isDevice
)

fun LocationWeatherEntity.toSnapshot(hourly: List<HourlyEntity>, daily: List<DailyEntity>) = WeatherSnapshot(
    location = toLocation(),
    utcOffsetSeconds = utcOffsetSeconds,
    current = with(current) {
        CurrentConditions(
            epochSeconds = epochSeconds,
            temperature = temperature,
            apparentTemperature = apparentTemperature,
            humidity = humidity,
            windSpeed = windSpeed,
            windGusts = windGusts,
            weatherCode = weatherCode,
            precipitation = precipitation,
            rainChance = rainChance,
            isDay = isDay,
            pressureMsl = pressureMsl,
            surfacePressure = surfacePressure,
            cloudCover = cloudCover,
            uvIndex = uvIndex,
            visibilityMeters = visibilityMeters
        )
    },
    hourly = hourly.map { h ->
        HourlyPoint(
            epochSeconds = h.epochSeconds,
            temperature = h.temperature,
            apparentTemperature = h.apparentTemperature,
            humidity = h.humidity,
            dewPoint = h.dewPoint,
            pressureMsl = h.pressureMsl,
            cloudCover = h.cloudCover,
            visibilityMeters = h.visibilityMeters,
            windSpeed = h.windSpeed,
            windGusts = h.windGusts,
            uvIndex = h.uvIndex,
            rainChance = h.rainChance,
            precipitation = h.precipitation,
            weatherCode = h.weatherCode,
            isDay = h.isDay
        )
    },
    daily = daily.map { d ->
        DailyPoint(
            epochSeconds = d.epochSeconds,
            sunriseEpochSeconds = d.sunriseEpochSeconds,
            sunsetEpochSeconds = d.sunsetEpochSeconds,
            temperatureMax = d.temperatureMax,
            temperatureMin = d.temperatureMin,
            uvIndexMax = d.uvIndexMax
        )
    },
    airQuality = air?.let { AirQuality(aqi = it.aqi, pm25 = it.pm25, pm10 = it.pm10) },
    fetchedAtMillis = fetchedAtMillis
)
