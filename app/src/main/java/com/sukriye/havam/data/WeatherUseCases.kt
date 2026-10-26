package com.sukriye.havam.data

import com.sukriye.havam.model.DataFreshness
import com.sukriye.havam.model.Province
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import com.sukriye.havam.model.nearestProvince
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

data class WeatherData(val snapshot: WeatherSnapshot, val freshness: DataFreshness)

// Room'daki veriyi dinler; her dakika tazeliği yeniden hesaplar, önbellek bittiyse tahminle tamamlar
class ObserveWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository,
    private val extrapolate: ExtrapolateForecastUseCase,
    private val clock: Clock,
    private val ticker: Ticker
) {
    operator fun invoke(location: WeatherLocation): Flow<WeatherData?> =
        repository.observeWeather(location).combine(ticker.ticks()) { snapshot, _ ->
            snapshot?.let { build(it) }
        }

    private fun build(snapshot: WeatherSnapshot): WeatherData {
        val nowMillis = clock.millis()
        val ageMinutes = (nowMillis - snapshot.fetchedAtMillis) / 60_000
        val result = extrapolate(snapshot, nowMillis / 1000)
        val freshness = when {
            result.extrapolatedHours > 0 -> DataFreshness.Extrapolated(ageMinutes, result.extrapolatedHours)
            ageMinutes <= LIVE_MINUTES -> DataFreshness.Live
            else -> DataFreshness.Cached(ageMinutes)
        }
        return WeatherData(result.snapshot, freshness)
    }

    private companion object {
        const val LIVE_MINUTES = 30L
    }
}

// Tazelik ve tahminin dakikada bir yeniden hesaplanması için saat sinyali.
// Arayüz olması testlerde sonsuz döngü yerine tek sinyal verilebilmesi içindir.
fun interface Ticker {
    fun ticks(): Flow<Unit>
}

class MinuteTicker @Inject constructor() : Ticker {
    override fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }
}

class RefreshWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository
) {
    // Hata olursa WeatherError fırlatır
    suspend operator fun invoke(location: WeatherLocation) = repository.refresh(location)
}

// Cihazın konumunu bulur ve hava durumu noktasına çevirir. Konum alınamazsa son bilinen konum kullanılır.
class ResolveDeviceLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val preferences: UserPreferencesRepository
) {
    suspend operator fun invoke(): WeatherLocation {
        val point = locationRepository.currentLocation()?.also { preferences.saveDeviceLocation(it) }
            ?: preferences.lastDeviceLocation()
            ?: throw WeatherError.LocationUnavailable()
        val province: Province? = nearestProvince(point.latitude, point.longitude)
        val name = province?.let { "${it.name} · Konumun" } ?: "Konumun"
        return WeatherLocation.device(name, point.latitude, point.longitude)
    }

    fun hasPermission() = locationRepository.hasPermission()
}
