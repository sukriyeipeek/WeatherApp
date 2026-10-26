package com.sukriye.havam.ui.city

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukriye.havam.data.CityRepository
import com.sukriye.havam.data.HomeWidgetUpdater
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.clockText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Liste ve harita ekranının ortak ViewModel'i: 81 ilin sıcaklıkları ve favoriler
@HiltViewModel
class CityViewModel @Inject constructor(
    private val repository: CityRepository,
    private val preferences: UserPreferencesRepository,
    private val widgetUpdater: HomeWidgetUpdater
) : ViewModel() {

    val favorites: StateFlow<Set<String>> = preferences.favorites

    // Anahtar il adı. Henüz yüklenmediyse boş, listede sıcaklık yerine sadece ok görünür
    var summaries by mutableStateOf<Map<String, CitySummary>>(emptyMap())
        private set
    var updatedAt by mutableStateOf<Long?>(null)
        private set
    var isOffline by mutableStateOf(false)
        private set
    var isRefreshing by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var locationMessage by mutableStateOf<String?>(null)
        private set

    // Başlığın altındaki durum satırı
    val statusText: String
        get() {
            val updated = updatedAt
            return when {
                updated == null && errorMessage != null -> "$errorMessage · aşağı çekip yenile"
                updated == null -> "81 il · sıcaklıklar yükleniyor…"
                isOffline -> "📴 Çevrimdışı · ${clockText(updated)} verisi"
                else -> "81 il · Güncelleme ${clockText(updated)}"
            }
        }

    init {
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            try {
                val result = repository.getSummaries()
                summaries = result.data
                updatedAt = result.updatedAt
                isOffline = result.isOffline
                errorMessage = null
            } catch (e: WeatherError) {
                errorMessage = e.message
            } finally {
                isRefreshing = false
            }
        }
    }

    fun toggleFavorite(name: String) {
        preferences.toggleFavorite(name)
        viewModelScope.launch { widgetUpdater.refresh() }
    }

    fun onLocationDenied() {
        locationMessage = "Konum izni verilmedi"
    }

    fun dismissLocationMessage() {
        locationMessage = null
    }
}
