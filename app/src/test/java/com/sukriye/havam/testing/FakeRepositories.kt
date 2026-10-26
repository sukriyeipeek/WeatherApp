package com.sukriye.havam.testing

import com.sukriye.havam.data.AmbientSoundPlayer
import com.sukriye.havam.data.CityRepository
import com.sukriye.havam.data.GeoPoint
import com.sukriye.havam.data.HomeWidgetUpdater
import com.sukriye.havam.data.LocationRepository
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.data.WeatherRepository
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.Loaded
import com.sukriye.havam.model.NoiseColor
import com.sukriye.havam.model.ThemeMode
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Testlerde gerçek repository'ler yerine kullanılan sahteler.
 *
 * Repository'leri arayüz olarak tanımlamamızın sebebi tam olarak budur: ViewModel testleri
 * internete ve veritabanına çıkmadan, istenen her sonucu (başarı / hata) kolayca üretebilir.
 */

/**
 * Room yerine bellekte tutar: [refresh] çağrılınca [snapshotFor] ile üretilen veri "veritabanına"
 * yazılır ve [observeWeather] dinleyenlerine gider.
 *
 * @param error Doluysa [refresh] bu hatayı fırlatır.
 */
class FakeWeatherRepository(
    var error: Throwable? = null,
    var snapshotFor: (WeatherLocation) -> WeatherSnapshot = { WeatherTestData.snapshot().copy(location = it) }
) : WeatherRepository {
    private val store = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())

    /** Hangi noktaların yenilendiği; ViewModel'in doğru yeri istediğini doğrulamak için. */
    val refreshed = mutableListOf<WeatherLocation>()

    override fun observeWeather(location: WeatherLocation): Flow<WeatherSnapshot?> = store.map { it[location.key] }

    override suspend fun refresh(location: WeatherLocation) {
        refreshed += location
        error?.let { throw it }
        store.update { it + (location.key to snapshotFor(location)) }
    }

    override suspend fun storedLocations(): List<WeatherLocation> = refreshed.distinct()
}

class FakeCityRepository(
    var result: Loaded<Map<String, CitySummary>> = Loaded(emptyMap(), updatedAt = 0L, isOffline = false),
    var error: Throwable? = null
) : CityRepository {
    var callCount: Int = 0
        private set

    override suspend fun getSummaries(): Loaded<Map<String, CitySummary>> {
        callCount++
        error?.let { throw it }
        return result
    }
}

class FakePreferencesRepository(
    favorites: Set<String> = emptySet(),
    interests: Set<UserInterest> = UserInterest.defaults
) : UserPreferencesRepository {
    private val _favorites = MutableStateFlow(favorites)
    private val _interests = MutableStateFlow(interests)
    private val _themeMode = MutableStateFlow(ThemeMode.default)
    override val favorites: StateFlow<Set<String>> = _favorites
    override val interests: StateFlow<Set<UserInterest>> = _interests
    override val themeMode: StateFlow<ThemeMode> = _themeMode

    var deviceLocation: GeoPoint? = null
    private var alertKey: String? = null

    override fun toggleFavorite(name: String) = _favorites.update { if (name in it) it - name else it + name }

    override fun toggleInterest(interest: UserInterest) = _interests.update { if (interest in it) it - interest else it + interest }

    override fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override fun lastDeviceLocation(): GeoPoint? = deviceLocation

    override fun saveDeviceLocation(point: GeoPoint) {
        deviceLocation = point
    }

    override fun lastAlertKey(): String? = alertKey

    override fun saveAlertKey(key: String) {
        alertKey = key
    }
}

class FakeLocationRepository(var permission: Boolean = true, var location: GeoPoint? = ANKARA) : LocationRepository {
    override fun hasPermission(): Boolean = permission

    override suspend fun currentLocation(): GeoPoint? = if (permission) location else null

    companion object {
        val ANKARA = GeoPoint(39.92, 32.85)
    }
}

class FakeAmbientSoundPlayer : AmbientSoundPlayer {
    private val _playing = MutableStateFlow<NoiseColor?>(null)
    override val playing: StateFlow<NoiseColor?> = _playing

    override fun play(color: NoiseColor) {
        _playing.value = color
    }

    override fun stop() {
        _playing.value = null
    }
}

class FakeHomeWidgetUpdater : HomeWidgetUpdater {
    var refreshCount: Int = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }
}
