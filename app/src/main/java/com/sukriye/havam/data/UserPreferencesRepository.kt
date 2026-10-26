@file:Suppress("DEPRECATION")

package com.sukriye.havam.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.sukriye.havam.model.ThemeMode
import com.sukriye.havam.model.UserInterest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UserPreferencesRepository {
    val favorites: StateFlow<Set<String>>
    val interests: StateFlow<Set<UserInterest>>
    val themeMode: StateFlow<ThemeMode>

    fun toggleFavorite(name: String)
    fun toggleInterest(interest: UserInterest)
    fun setThemeMode(mode: ThemeMode)

    // Konum hassas veri: şifreli saklanır
    fun lastDeviceLocation(): GeoPoint?
    fun saveDeviceLocation(point: GeoPoint)

    // Aynı fırtına için tekrar tekrar bildirim atmamak için
    fun lastAlertKey(): String?
    fun saveAlertKey(key: String)
}

// Favoriler, ilgi alanları ve tema normal, son konum şifreli SharedPreferences'ta saklanır.
// Liste ekranı, panel, widget ve arka plan senkronu aynı örneği kullanır (Singleton).
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : UserPreferencesRepository {
    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = createSecurePreferences(context)

    // StateFlow: değeri değişince onu dinleyen ekranlar otomatik yeniden çizilir
    private val _favorites = MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet())
    override val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _interests = MutableStateFlow(readInterests())
    override val interests: StateFlow<Set<UserInterest>> = _interests.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.default
    )
    override val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    override fun toggleFavorite(name: String) {
        val current = _favorites.value
        val updated = if (name in current) current - name else current + name
        prefs.edit { putStringSet(KEY_FAVORITES, updated) }
        _favorites.value = updated
    }

    override fun toggleInterest(interest: UserInterest) {
        val current = _interests.value
        val updated = if (interest in current) current - interest else current + interest
        prefs.edit { putStringSet(KEY_INTERESTS, updated.map { it.name }.toSet()) }
        _interests.value = updated
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    override fun lastDeviceLocation(): GeoPoint? {
        if (!secure.contains(KEY_LAT)) return null
        return GeoPoint(
            latitude = secure.getFloat(KEY_LAT, 0f).toDouble(),
            longitude = secure.getFloat(KEY_LON, 0f).toDouble()
        )
    }

    override fun saveDeviceLocation(point: GeoPoint) {
        secure.edit {
            putFloat(KEY_LAT, point.latitude.toFloat())
            putFloat(KEY_LON, point.longitude.toFloat())
        }
    }

    override fun lastAlertKey(): String? = prefs.getString(KEY_ALERT, null)

    override fun saveAlertKey(key: String) = prefs.edit { putString(KEY_ALERT, key) }

    private fun readInterests(): Set<UserInterest> {
        val stored = prefs.getStringSet(KEY_INTERESTS, null) ?: return UserInterest.defaults
        return stored.mapNotNull { name -> UserInterest.entries.firstOrNull { it.name == name } }.toSet()
    }

    private companion object {
        const val KEY_FAVORITES = "names"
        const val KEY_INTERESTS = "interests"
        const val KEY_THEME = "theme_mode"
        const val KEY_ALERT = "last_alert"
        const val KEY_LAT = "device_lat"
        const val KEY_LON = "device_lon"

        // Bazı cihazlarda Android Keystore bozuk olabiliyor; o zaman uygulama çökmesin, düz dosyaya düşsün
        fun createSecurePreferences(context: Context): SharedPreferences = try {
            val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                context,
                "secure_location",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            context.getSharedPreferences("secure_location_fallback", Context.MODE_PRIVATE)
        } catch (e: IOException) {
            context.getSharedPreferences("secure_location_fallback", Context.MODE_PRIVATE)
        }
    }
}
