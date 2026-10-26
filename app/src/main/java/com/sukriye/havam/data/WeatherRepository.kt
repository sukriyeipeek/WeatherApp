package com.sukriye.havam.data

import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/**
 * Bir noktanın ayrıntılı hava durumu. Arayüz olmasının sebebi: ViewModel testleri
 * internete ve veritabanına çıkmadan sahte bir repository ile çalışabilsin.
 */
interface WeatherRepository {
    // Tek doğruluk kaynağı Room: ekran buradan dinler, ağ sadece veritabanını günceller
    fun observeWeather(location: WeatherLocation): Flow<WeatherSnapshot?>

    // Hata olursa WeatherError fırlatır
    suspend fun refresh(location: WeatherLocation)

    // Arka plan senkronu için: veritabanında kayıtlı tüm noktalar
    suspend fun storedLocations(): List<WeatherLocation>
}

// Offline-first: ekran her zaman Room'u dinler, ağdan gelen veri önce veritabanına yazılır
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val api: ForecastApi,
    private val airApi: AirQualityApi,
    private val dao: WeatherDao,
    private val network: NetworkMonitor,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : WeatherRepository {

    override fun observeWeather(location: WeatherLocation): Flow<WeatherSnapshot?> = combine(
        dao.observeLocation(location.key),
        dao.observeHourly(location.key),
        dao.observeDaily(location.key)
    ) { meta, hourly, daily -> meta?.toSnapshot(hourly, daily) }
        .distinctUntilChanged()
        .flowOn(io)

    override suspend fun refresh(location: WeatherLocation) = withContext(io) {
        network.mapErrors {
            coroutineScope {
                // İki servise aynı anda istek atıyoruz, ikisinin de bitmesini bekliyoruz
                val forecast = async { api.forecast(location.latitude, location.longitude) }
                val air = async { fetchAirQuality(location) }
                val dto = forecast.await()
                val now = clock.millis()
                dao.applyDelta(
                    location = dto.toLocationEntity(location, air.await(), now),
                    hourly = dto.toHourlyEntities(location.key),
                    daily = dto.toDailyEntities(location.key),
                    // 36 saatten eski satırlara trend hesabı için bile gerek yok
                    keepAfterEpochSeconds = now / 1000 - 36 * 3600
                )
            }
        }
        Unit
    }

    override suspend fun storedLocations(): List<WeatherLocation> = withContext(io) { dao.allLocations().map { it.toLocation() } }

    // Hava kalitesi olmasa da hava durumu gösterilebilir, bu yüzden hata olursa null döner
    private suspend fun fetchAirQuality(location: WeatherLocation): AirQualityDto? = try {
        airApi.airQuality(location.latitude, location.longitude)
    } catch (e: IOException) {
        null
    } catch (e: HttpException) {
        null
    } catch (e: SerializationException) {
        null
    }
}
