package com.sukriye.havam.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sukriye.havam.MainActivity
import com.sukriye.havam.model.SevereWeatherAlert
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.nearestProvince
import com.sukriye.havam.model.provinces
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Saatlik arka plan senkronu: favori iller + son cihaz konumu yenilenir, ardından önümüzdeki
 * 6 saatte şiddetli hava varsa bildirim atılır. Sadece internet varken ve pil düşük değilken çalışır.
 */
@HiltWorker
class WeatherSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: WeatherRepository,
    private val preferences: UserPreferencesRepository,
    private val detectSevereWeather: DetectSevereWeatherUseCase,
    private val notifier: StormNotifier,
    private val widgetUpdater: HomeWidgetUpdater,
    private val clock: Clock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val targets = syncTargets()
        var failures = 0
        for (location in targets) {
            try {
                repository.refresh(location)
            } catch (e: WeatherError) {
                failures++
            }
        }

        val now = clock.millis() / 1000
        for (location in targets) {
            val snapshot = repository.observeWeather(location).first() ?: continue
            val alert = detectSevereWeather(snapshot, now) ?: continue
            if (alert.key != preferences.lastAlertKey()) {
                notifier.show(alert)
                preferences.saveAlertKey(alert.key)
            }
            // Tek seferde en fazla bir uyarı: bildirim yağmuru olmasın
            break
        }
        widgetUpdater.refresh()
        return if (targets.isNotEmpty() && failures == targets.size) Result.retry() else Result.success()
    }

    private fun syncTargets(): List<WeatherLocation> {
        val favorites = preferences.favorites.value
        val provinceTargets = provinces.filter { it.name in favorites }.map { WeatherLocation.of(it) }
            // Favori yoksa widget'ın gösterdiği İstanbul güncel kalsın
            .ifEmpty { provinces.filter { it.name == "İstanbul" }.map { WeatherLocation.of(it) } }
        val device = preferences.lastDeviceLocation()?.let { point ->
            val name = nearestProvince(point.latitude, point.longitude)?.let { "${it.name} · Konumun" } ?: "Konumun"
            WeatherLocation.device(name, point.latitude, point.longitude)
        }
        return (listOfNotNull(device) + provinceTargets).take(MAX_TARGETS)
    }

    companion object {
        private const val MAX_TARGETS = 6
        private const val WORK_NAME = "weather-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

@Singleton
class StormNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    fun show(alert: SevereWeatherAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Şiddetli hava uyarıları", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Fırtına, kuvvetli yağış ve buzlanma uyarıları"
            }
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ ${alert.title}")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(alert.key.hashCode(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "severe_weather"
    }
}
