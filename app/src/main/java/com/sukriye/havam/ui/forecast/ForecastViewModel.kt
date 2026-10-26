package com.sukriye.havam.ui.forecast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sukriye.havam.data.AmbientSoundPlayer
import com.sukriye.havam.data.CalculateLifestyleIndicesUseCase
import com.sukriye.havam.data.DefaultDispatcher
import com.sukriye.havam.data.GenerateOutfitAdviceUseCase
import com.sukriye.havam.data.HomeWidgetUpdater
import com.sukriye.havam.data.ObserveWeatherUseCase
import com.sukriye.havam.data.RefreshWeatherUseCase
import com.sukriye.havam.data.ResolveDeviceLocationUseCase
import com.sukriye.havam.data.SelectSoundscapeUseCase
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.data.WeatherData
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.formatClock
import com.sukriye.havam.model.provinces
import com.sukriye.havam.ui.LoadState
import com.sukriye.havam.ui.UiError
import com.sukriye.havam.ui.toUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLEncoder
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tek yönlü veri akışı:
 *   Intent -> (use case'ler) -> iç durum + Room akışı + tercihler + sensör -> WeatherUiState
 * Ekranın gördüğü durum birden çok kaynaktan türetilir ve sadece ekran dinlerken (WhileSubscribed)
 * hesaplanır; uygulama arka plandayken dakika sayacı durur.
 *
 * Hangi yerin açılacağı navigasyon argümanlarından ([SavedStateHandle]) okunur:
 * `forecast?province=Ankara` bir il, `forecast?device=true` cihazın konumu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeWeather: ObserveWeatherUseCase,
    private val refreshWeather: RefreshWeatherUseCase,
    private val resolveDeviceLocation: ResolveDeviceLocationUseCase,
    private val calculateLifestyle: CalculateLifestyleIndicesUseCase,
    private val selectSoundscape: SelectSoundscapeUseCase,
    private val generateOutfit: GenerateOutfitAdviceUseCase,
    private val preferences: UserPreferencesRepository,
    private val ambientPlayer: AmbientSoundPlayer,
    private val widgetUpdater: HomeWidgetUpdater,
    private val clock: Clock,
    @param:DefaultDispatcher private val computeDispatcher: CoroutineDispatcher
) : ViewModel() {

    // Intent'lerin doğrudan değiştirdiği kısım: konum, yenileme/konum bulma bayrakları, seçili saat, hata
    private data class LocalState(
        val location: WeatherLocation? = null,
        val isRefreshing: Boolean = false,
        val isLocating: Boolean = false,
        val selectedHourIndex: Int = 0,
        val error: UiError? = null
    )

    private val local = MutableStateFlow(LocalState())

    private val _effects = Channel<WeatherSideEffect>(Channel.BUFFERED)
    val effects: Flow<WeatherSideEffect> = _effects.receiveAsFlow()

    private val locationFlow = local.map { it.location }.distinctUntilChanged()

    // Konum değişince eski konumun akışı iptal edilip yenisine geçilir (flatMapLatest)
    private val contentFlow: Flow<WeatherContent?> = locationFlow
        .flatMapLatest { location ->
            if (location == null) {
                flowOf(null)
            } else {
                combine(observeWeather(location), preferences.interests) { data, interests ->
                    data?.let { buildContent(it, interests) }
                }
            }
        }
        // İndeks motoru ve tahmin ana thread'i yormasın
        .flowOn(computeDispatcher)

    val state: StateFlow<WeatherUiState> = combine(
        local,
        contentFlow,
        preferences.favorites,
        ambientPlayer.playing
    ) { local, content, favorites, playing ->
        WeatherUiState(
            location = local.location,
            content = when {
                content != null && content.hourly.isEmpty() -> LoadState.Empty
                content != null -> LoadState.Success(content)
                local.error != null -> LoadState.Error(local.error)
                else -> LoadState.Loading
            },
            isRefreshing = local.isRefreshing,
            isLocating = local.isLocating,
            isFavorite = local.location?.let { !it.isDeviceLocation && it.name in favorites } ?: false,
            interests = content?.lifestyle?.indices?.map { it.interest }?.toSet() ?: preferences.interests.value,
            selectedHourIndex = local.selectedHourIndex.coerceIn(0, ((content?.hourly?.size ?: 1) - 1).coerceAtLeast(0)),
            playingNoise = playing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    private var refreshJob: Job? = null
    private var locateJob: Job? = null

    init {
        val provinceName = savedStateHandle.get<String>(ARG_PROVINCE).orEmpty()
        val province = provinces.firstOrNull { it.name == provinceName }
        if (province != null) {
            load(WeatherLocation.of(province))
        } else {
            locateDevice(permissionDenied = false)
        }
    }

    fun onIntent(intent: WeatherIntent) {
        when (intent) {
            is WeatherIntent.LocationPermissionResult -> locateDevice(permissionDenied = !intent.granted)
            WeatherIntent.Refresh -> local.value.location?.let { refresh(it, userInitiated = true) }
            WeatherIntent.Retry -> {
                val location = local.value.location
                if (location == null) locateDevice(permissionDenied = false) else refresh(location, userInitiated = true)
            }
            WeatherIntent.ToggleFavorite -> toggleFavorite()
            is WeatherIntent.ToggleInterest -> preferences.toggleInterest(intent.interest)
            is WeatherIntent.SelectHour -> local.update { it.copy(selectedHourIndex = intent.index) }
            WeatherIntent.ToggleAmbientSound -> toggleAmbient()
            WeatherIntent.OpenPlaylist -> openPlaylist()
        }
    }

    private fun load(location: WeatherLocation) {
        local.value = LocalState(location = location)
        refresh(location, userInitiated = false)
    }

    private fun locateDevice(permissionDenied: Boolean) {
        val current = local.value
        // Konum zaten bulunmuşsa ya da hâlâ aranıyorsa tekrar arama
        if (!permissionDenied && (current.isLocating || (current.location?.isDeviceLocation == true && current.error == null))) {
            return
        }
        if (!permissionDenied && !resolveDeviceLocation.hasPermission()) {
            _effects.trySend(WeatherSideEffect.RequestLocationPermission)
            return
        }
        locateJob?.cancel()
        refreshJob?.cancel()
        local.value = LocalState(isLocating = true)
        locateJob = viewModelScope.launch {
            try {
                // İzin reddedilse bile daha önce kaydedilmiş konum varsa onunla devam edilir
                val location = resolveDeviceLocation()
                local.update { it.copy(location = location, isLocating = false) }
                refresh(location, userInitiated = false)
            } catch (e: WeatherError) {
                val error = if (permissionDenied) UiError.LocationPermissionDenied else e.toUiError()
                local.update { it.copy(isLocating = false, error = error) }
            }
        }
    }

    private fun refresh(location: WeatherLocation, userInitiated: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            local.update { it.copy(isRefreshing = true) }
            try {
                refreshWeather(location)
                local.update { it.copy(error = null) }
                if (userInitiated) _effects.send(WeatherSideEffect.ShowMessage("Güncellendi"))
            } catch (e: WeatherError) {
                val error = e.toUiError()
                local.update { it.copy(error = error) }
                // Kayıtlı veri ekrandaysa hata tam ekran değil, kısa bir mesaj olarak gösterilir
                if (state.value.content is LoadState.Success) {
                    _effects.send(WeatherSideEffect.ShowMessage("${e.message} · kayıtlı veri gösteriliyor"))
                }
            } finally {
                local.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun toggleFavorite() {
        val location = local.value.location ?: return
        if (location.isDeviceLocation) {
            _effects.trySend(WeatherSideEffect.ShowMessage("Konumun favorilere eklenemez, il seçerek ekleyebilirsin"))
            return
        }
        val adding = location.name !in preferences.favorites.value
        preferences.toggleFavorite(location.name)
        if (adding) {
            _effects.trySend(WeatherSideEffect.RequestNotificationPermission)
            _effects.trySend(WeatherSideEffect.ShowMessage("${location.name} favorilerde · şiddetli hava uyarıları açık"))
        }
        // Widget ilk favori ili gösteriyor, favoriler değişince o da güncellensin
        viewModelScope.launch { widgetUpdater.refresh() }
    }

    private fun toggleAmbient() {
        val content = (state.value.content as? LoadState.Success)?.data ?: return
        if (ambientPlayer.playing.value != null) {
            ambientPlayer.stop()
        } else {
            ambientPlayer.play(content.soundscape.noiseColor)
        }
    }

    private fun openPlaylist() {
        val content = (state.value.content as? LoadState.Success)?.data ?: return
        val query = URLEncoder.encode(content.soundscape.spotifySearchQuery, "UTF-8").replace("+", "%20")
        _effects.trySend(
            WeatherSideEffect.OpenUri(
                uri = "spotify:search:$query",
                fallbackUri = "https://open.spotify.com/search/$query"
            )
        )
    }

    private fun buildContent(data: WeatherData, interests: Set<UserInterest>): WeatherContent {
        val snapshot = data.snapshot
        val now = clock.millis() / 1000
        val lifestyle = calculateLifestyle(snapshot, interests, now)
        val today = snapshot.today(now)
        val hourly = snapshot.upcomingHours(now).take(25).mapIndexed { index, hour ->
            ChartPoint(
                epochSeconds = hour.epochSeconds,
                label = if (index == 0) "Şimdi" else formatClock(hour.epochSeconds, snapshot.utcOffsetSeconds),
                // İlk nokta "şimdi": panelle aynı değeri göstersin
                temperature = if (index == 0) snapshot.current.temperature else hour.temperature,
                rainChance = hour.rainChance,
                weatherCode = if (index == 0) snapshot.current.weatherCode else hour.weatherCode,
                isDay = if (index == 0) snapshot.current.isDay else hour.isDay,
                isExtrapolated = hour.isExtrapolated
            )
        }
        return WeatherContent(
            snapshot = snapshot,
            freshness = data.freshness,
            hourly = hourly,
            lifestyle = lifestyle,
            soundscape = selectSoundscape(snapshot, lifestyle.pressureTrend, now),
            outfit = generateOutfit(snapshot, now),
            todayMin = today?.temperatureMin,
            todayMax = today?.temperatureMax,
            sunrise = today?.let { formatClock(it.sunriseEpochSeconds, snapshot.utcOffsetSeconds) },
            sunset = today?.let { formatClock(it.sunsetEpochSeconds, snapshot.utcOffsetSeconds) }
        )
    }

    // Ekrandan çıkılınca (geri tuşu) ViewModel silinir; ses de onunla birlikte durur
    override fun onCleared() {
        ambientPlayer.stop()
    }

    companion object {
        const val ARG_PROVINCE = "province"
        const val ARG_DEVICE = "device"
    }
}
