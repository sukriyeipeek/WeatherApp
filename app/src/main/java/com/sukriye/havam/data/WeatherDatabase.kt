package com.sukriye.havam.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "weather_location")
data class LocationWeatherEntity(
    @PrimaryKey val locationKey: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isDevice: Boolean,
    val utcOffsetSeconds: Int,
    val fetchedAtMillis: Long,
    @Embedded(prefix = "current_") val current: CurrentEntity,
    // Hava kalitesi servisi cevap vermezse bütün sütunlar boş kalır
    @Embedded(prefix = "air_") val air: AirEntity?
)

data class CurrentEntity(
    val epochSeconds: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double,
    val weatherCode: Int,
    val precipitation: Double,
    val rainChance: Int,
    val isDay: Boolean,
    val pressureMsl: Double,
    val surfacePressure: Double,
    val cloudCover: Int,
    val uvIndex: Double,
    val visibilityMeters: Double
)

data class AirEntity(val aqi: Int, val pm25: Double, val pm10: Double)

@Entity(tableName = "hourly_forecast", primaryKeys = ["locationKey", "epochSeconds"])
data class HourlyEntity(
    val locationKey: String,
    val epochSeconds: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double,
    val pressureMsl: Double,
    val cloudCover: Int,
    val visibilityMeters: Double,
    val windSpeed: Double,
    val windGusts: Double,
    val uvIndex: Double,
    val rainChance: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val isDay: Boolean
)

@Entity(tableName = "daily_forecast", primaryKeys = ["locationKey", "epochSeconds"])
data class DailyEntity(
    val locationKey: String,
    val epochSeconds: Long,
    val sunriseEpochSeconds: Long,
    val sunsetEpochSeconds: Long,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val uvIndexMax: Double
)

@Entity(tableName = "province_summary")
data class SummaryEntity(
    @PrimaryKey val name: String,
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val updatedAtMillis: Long
)

@Dao
abstract class WeatherDao {
    @Query("SELECT * FROM weather_location WHERE locationKey = :key")
    abstract fun observeLocation(key: String): Flow<LocationWeatherEntity?>

    @Query("SELECT * FROM hourly_forecast WHERE locationKey = :key ORDER BY epochSeconds")
    abstract fun observeHourly(key: String): Flow<List<HourlyEntity>>

    @Query("SELECT * FROM daily_forecast WHERE locationKey = :key ORDER BY epochSeconds")
    abstract fun observeDaily(key: String): Flow<List<DailyEntity>>

    @Query("SELECT * FROM weather_location")
    abstract suspend fun allLocations(): List<LocationWeatherEntity>

    @Query("SELECT * FROM hourly_forecast WHERE locationKey = :key")
    protected abstract suspend fun hourlyFor(key: String): List<HourlyEntity>

    @Upsert
    protected abstract suspend fun upsertLocation(entity: LocationWeatherEntity)

    @Upsert
    protected abstract suspend fun upsertHourly(entities: List<HourlyEntity>)

    @Upsert
    protected abstract suspend fun upsertDaily(entities: List<DailyEntity>)

    @Query("DELETE FROM hourly_forecast WHERE locationKey = :key AND epochSeconds < :before")
    protected abstract suspend fun deleteHourlyBefore(key: String, before: Long)

    @Query("DELETE FROM daily_forecast WHERE locationKey = :key AND epochSeconds < :before")
    protected abstract suspend fun deleteDailyBefore(key: String, before: Long)

    /**
     * Delta senkron: sadece değişen saatlik satırlar yazılır, eskiler budanır.
     * Böylece her senkronda 72 satır yerine genellikle birkaç satır güncellenir ve
     * Room'un Flow'ları gereksiz yere tetiklenmez. Değişen satır sayısını döner.
     */
    @Transaction
    open suspend fun applyDelta(
        location: LocationWeatherEntity,
        hourly: List<HourlyEntity>,
        daily: List<DailyEntity>,
        keepAfterEpochSeconds: Long
    ): Int {
        upsertLocation(location)
        val existing = hourlyFor(location.locationKey).associateBy { it.epochSeconds }
        val changed = hourly.filter { existing[it.epochSeconds] != it }
        if (changed.isNotEmpty()) upsertHourly(changed)
        deleteHourlyBefore(location.locationKey, keepAfterEpochSeconds)
        upsertDaily(daily)
        deleteDailyBefore(location.locationKey, keepAfterEpochSeconds - 86_400)
        return changed.size
    }

    @Query("SELECT * FROM province_summary")
    abstract suspend fun summaries(): List<SummaryEntity>

    @Upsert
    abstract suspend fun upsertSummaries(entities: List<SummaryEntity>)
}

@Database(
    entities = [LocationWeatherEntity::class, HourlyEntity::class, DailyEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
