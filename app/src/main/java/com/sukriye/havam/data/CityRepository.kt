package com.sukriye.havam.data

import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.Loaded
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.provinces
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// Liste ve harita ekranı için 81 ilin anlık sıcaklığı. Anahtar: il adı
interface CityRepository {
    // İnternet yoksa son kaydedilen liste döner; o da yoksa WeatherError fırlatır
    suspend fun getSummaries(): Loaded<Map<String, CitySummary>>
}

@Singleton
class CityRepositoryImpl @Inject constructor(
    private val api: CityApi,
    private val dao: WeatherDao,
    private val network: NetworkMonitor,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : CityRepository {

    override suspend fun getSummaries(): Loaded<Map<String, CitySummary>> = withContext(io) {
        try {
            val responses = network.mapErrors {
                api.summaries(
                    latitudes = provinces.joinToString(",") { it.latitude.toString() },
                    longitudes = provinces.joinToString(",") { it.longitude.toString() }
                )
            }
            val now = clock.millis()
            // Cevaplar istekteki sırayla geliyor, yani i. cevap i. ile ait
            val entities = provinces.zip(responses).map { (province, response) ->
                SummaryEntity(
                    name = province.name,
                    temperature = response.current.temperature,
                    weatherCode = response.current.weatherCode,
                    isDay = response.current.isDay == 1,
                    updatedAtMillis = now
                )
            }
            dao.upsertSummaries(entities)
            Loaded(entities.toSummaries(), updatedAt = now, isOffline = false)
        } catch (e: WeatherError.NoConnection) {
            // İnternet yok: daha önce kaydedilmiş veri varsa onu göster, yoksa hatayı ekrana ilet
            val cached = dao.summaries().ifEmpty { throw e }
            Loaded(cached.toSummaries(), updatedAt = cached.maxOf { it.updatedAtMillis }, isOffline = true)
        }
    }

    private fun List<SummaryEntity>.toSummaries() = associate { it.name to CitySummary(it.temperature, it.weatherCode, it.isDay) }
}
