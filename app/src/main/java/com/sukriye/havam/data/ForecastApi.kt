package com.sukriye.havam.data

import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo: ücretsiz, API anahtarı gerektirmez
interface ForecastApi {
    // Geçmiş 24 saat basınç trendi ve çevrimdışı tahmin için, sonraki 48 saat grafik için
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = HOURLY_FIELDS,
        @Query("daily") daily: String = "sunrise,sunset,temperature_2m_max,temperature_2m_min,uv_index_max",
        @Query("past_hours") pastHours: Int = 24,
        @Query("forecast_hours") forecastHours: Int = 48,
        @Query("forecast_days") forecastDays: Int = 3,
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime"
    ): ForecastDto

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_gusts_10m," +
                "weather_code,precipitation,precipitation_probability,is_day,pressure_msl,surface_pressure," +
                "cloud_cover,uv_index,visibility"
        private const val HOURLY_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,pressure_msl,cloud_cover," +
                "visibility,wind_speed_10m,wind_gusts_10m,uv_index,precipitation_probability,precipitation," +
                "weather_code,is_day"
    }
}

// Hava kalitesi ayrı bir adreste çalışıyor (Copernicus CAMS verisi)
interface AirQualityApi {
    @GET("v1/air-quality")
    suspend fun airQuality(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "european_aqi,pm2_5,pm10"
    ): AirQualityDto

    companion object {
        const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    }
}
