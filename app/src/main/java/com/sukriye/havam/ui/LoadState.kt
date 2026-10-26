package com.sukriye.havam.ui

import com.sukriye.havam.model.WeatherError

/**
 * Bir ekranın içeriğinin bulunabileceği dört durum. Ekran her an tam olarak birindedir:
 * - [Loading] : Veri bekleniyor (iskelet görünüm).
 * - [Success] : Veri geldi.
 * - [Empty]   : Cevap geldi ama gösterilecek bir şey yok.
 * - [Error]   : Bir şey ters gitti; anlaşılır mesaj + gerekiyorsa "Tekrar dene".
 *
 * `sealed` olduğu için `when` içinde bir dal unutulursa derleme hatası alınır.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Success<T>(val data: T) : LoadState<T>

    data object Empty : LoadState<Nothing>

    data class Error(val error: UiError) : LoadState<Nothing>
}

// Kullanıcıya gösterilen, teknik terim içermeyen hata mesajları
sealed interface UiError {
    val message: String
    val canRetry: Boolean get() = true

    data object NoConnection : UiError {
        override val message = "İnternet bağlantısı yok ve bu konum için kayıtlı veri bulunamadı"
    }

    data class Server(val code: Int) : UiError {
        override val message = "Sunucu şu an cevap vermiyor ($code)"
    }

    data object InvalidData : UiError {
        override val message = "Bir şeyler ters gitti. Tekrar dene."
    }

    data object LocationUnavailable : UiError {
        override val message = "Konum bulunamadı. Telefonunun konumu açık mı?"
    }

    data object LocationPermissionDenied : UiError {
        override val message = "Konum izni verilmedi. Listeden bir il seçebilirsin."
        override val canRetry = false
    }
}

fun WeatherError.toUiError(): UiError = when (this) {
    is WeatherError.NoConnection -> UiError.NoConnection
    is WeatherError.Server -> UiError.Server(code)
    is WeatherError.InvalidData -> UiError.InvalidData
    is WeatherError.LocationUnavailable -> UiError.LocationUnavailable
}
