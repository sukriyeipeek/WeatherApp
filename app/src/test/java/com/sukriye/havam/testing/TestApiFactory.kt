package com.sukriye.havam.testing

import com.sukriye.havam.data.ApiClient
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer

/**
 * Testlerde gerçek Open-Meteo yerine yerel bir sahte sunucuya bağlanan Retrofit kurar.
 * Böylece testler internetsiz çalışır, her zaman aynı sonucu verir ve JSON eşlemesini
 * gerçek cevap üzerinden doğrular. Uygulamayla aynı Json ayarları kullanılır.
 */
internal inline fun <reified T> MockWebServer.createApi(): T =
    ApiClient.createApi(url("/").toString(), OkHttpClient(), ApiClient.provideJson())

/** `src/test/resources` altındaki bir JSON dosyasını metin olarak okur. */
internal fun readResource(name: String): String = checkNotNull(object {}.javaClass.classLoader?.getResourceAsStream(name)) {
    "Test kaynağı bulunamadı: $name"
}.bufferedReader().use { it.readText() }
