package com.sukriye.havam.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class GeoPoint(val latitude: Double, val longitude: Double)

interface LocationRepository {
    fun hasPermission(): Boolean

    // İzin yoksa ya da konum kapalıysa null
    suspend fun currentLocation(): GeoPoint?
}

// Telefonun konumunu bulur. Önce Google'ın Fused Location servisi (pil dostu, hızlı),
// Play Services yoksa Android'in kendi LocationManager'ı kullanılır.
@Singleton
class LocationRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocationRepository {

    override fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val location = fusedLocation() ?: platformLocation()
        return location?.let { GeoPoint(it.latitude, it.longitude) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private suspend fun fusedLocation(): Location? {
        val tokenSource = CancellationTokenSource()
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            // İl düzeyinde mikro-iklim için şehir bloğu doğruluğu yeterli, GPS'i uyandırmaya gerek yok
            withTimeoutOrNull(10_000) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token).await(tokenSource)
            } ?: client.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Play Services yok ya da güncel değil
            null
        } finally {
            tokenSource.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun platformLocation(): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        return try {
            providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
                ?: providers.firstNotNullOfOrNull { provider ->
                    withTimeoutOrNull(10_000) {
                        suspendCancellableCoroutine { continuation ->
                            LocationManagerCompat.getCurrentLocation(
                                manager,
                                provider,
                                // Kütüphanede iki farklı CancellationSignal türü alan iki sürüm var
                                null as android.os.CancellationSignal?,
                                ContextCompat.getMainExecutor(context)
                            ) { location: Location? ->
                                if (continuation.isActive) continuation.resume(location)
                            }
                        }
                    }
                }
        } catch (e: SecurityException) {
            null
        }
    }
}
