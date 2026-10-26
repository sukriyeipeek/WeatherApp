package com.sukriye.havam.ui.forecast

import androidx.compose.runtime.Immutable
import com.sukriye.havam.model.Advice
import com.sukriye.havam.model.DataFreshness
import com.sukriye.havam.model.LifestyleReport
import com.sukriye.havam.model.NoiseColor
import com.sukriye.havam.model.Soundscape
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import com.sukriye.havam.ui.LoadState

// MVI sözleşmesi: ekran yalnızca WeatherUiState'i çizer, kullanıcı eylemlerini WeatherIntent olarak
// gönderir; tek seferlik olaylar (mesaj, izin isteği, link açma) WeatherSideEffect ile gelir.

@Immutable
data class WeatherUiState(
    val location: WeatherLocation? = null,
    val content: LoadState<WeatherContent> = LoadState.Loading,
    val isRefreshing: Boolean = false,
    val isLocating: Boolean = false,
    val isFavorite: Boolean = false,
    val interests: Set<UserInterest> = UserInterest.defaults,
    val selectedHourIndex: Int = 0,
    val playingNoise: NoiseColor? = null
) {
    val canFavorite: Boolean get() = location?.isDeviceLocation == false
}

// Ekranın ihtiyaç duyduğu her şey önceden hesaplanmış hâlde: Composable'lar hesap yapmaz
@Immutable
data class WeatherContent(
    val snapshot: WeatherSnapshot,
    val freshness: DataFreshness,
    val hourly: List<ChartPoint>,
    val lifestyle: LifestyleReport,
    val soundscape: Soundscape,
    val outfit: List<Advice>,
    val todayMin: Double?,
    val todayMax: Double?,
    val sunrise: String?,
    val sunset: String?
)

@Immutable
data class ChartPoint(
    val epochSeconds: Long,
    val label: String,
    val temperature: Double,
    val rainChance: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val isExtrapolated: Boolean
)

sealed interface WeatherIntent {
    data class LocationPermissionResult(val granted: Boolean) : WeatherIntent
    data object Refresh : WeatherIntent
    data object Retry : WeatherIntent
    data object ToggleFavorite : WeatherIntent
    data class ToggleInterest(val interest: UserInterest) : WeatherIntent
    data class SelectHour(val index: Int) : WeatherIntent
    data object ToggleAmbientSound : WeatherIntent
    data object OpenPlaylist : WeatherIntent
}

sealed interface WeatherSideEffect {
    data class ShowMessage(val message: String) : WeatherSideEffect

    // Spotify uygulaması yoksa web adresi açılır
    data class OpenUri(val uri: String, val fallbackUri: String) : WeatherSideEffect
    data object RequestLocationPermission : WeatherSideEffect
    data object RequestNotificationPermission : WeatherSideEffect
}
