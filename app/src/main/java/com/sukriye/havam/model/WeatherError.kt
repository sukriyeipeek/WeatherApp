package com.sukriye.havam.model

// Data katmanı Retrofit/IO hatalarını bu türlere çevirir; üst katmanlar Retrofit'i tanımaz
sealed class WeatherError(message: String) : Exception(message) {
    class NoConnection : WeatherError("İnternet bağlantısı yok")
    class Server(val code: Int) : WeatherError("Sunucu hatası ($code)")
    class InvalidData : WeatherError("Sunucudan beklenmeyen veri geldi")
    class LocationUnavailable : WeatherError("Konum bulunamadı. Telefonunun konumu açık mı?")
}
