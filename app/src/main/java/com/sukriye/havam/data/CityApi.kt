package com.sukriye.havam.data

import retrofit2.http.GET
import retrofit2.http.Query

// 81 ilin özet hava durumu. Virgülle ayrılmış koordinatlar verilirse servis her nokta için
// bir cevap döndürür; böylece 81 ilin sıcaklığı 81 ayrı istek yerine tek istekte gelir.
// Örnek: https://api.open-meteo.com/v1/forecast?latitude=39.92,41.01&longitude=32.85,28.97&current=temperature_2m
interface CityApi {
    @GET("v1/forecast")
    suspend fun summaries(
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String,
        @Query("current") current: String = "temperature_2m,weather_code,is_day"
    ): List<SummaryDto>
}
