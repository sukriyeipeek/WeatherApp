package com.sukriye.havam.ui.forecast

import androidx.lifecycle.SavedStateHandle
import com.sukriye.havam.data.CalculateLifestyleIndicesUseCase
import com.sukriye.havam.data.ExtrapolateForecastUseCase
import com.sukriye.havam.data.GenerateOutfitAdviceUseCase
import com.sukriye.havam.data.ObserveWeatherUseCase
import com.sukriye.havam.data.RefreshWeatherUseCase
import com.sukriye.havam.data.ResolveDeviceLocationUseCase
import com.sukriye.havam.data.SelectSoundscapeUseCase
import com.sukriye.havam.data.Ticker
import com.sukriye.havam.model.NoiseColor
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.testing.FakeAmbientSoundPlayer
import com.sukriye.havam.testing.FakeHomeWidgetUpdater
import com.sukriye.havam.testing.FakeLocationRepository
import com.sukriye.havam.testing.FakePreferencesRepository
import com.sukriye.havam.testing.FakeWeatherRepository
import com.sukriye.havam.testing.MainDispatcherRule
import com.sukriye.havam.testing.WeatherTestData
import com.sukriye.havam.ui.LoadState
import com.sukriye.havam.ui.UiError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochSecond(WeatherTestData.NOW), ZoneOffset.UTC)
    private val weather = FakeWeatherRepository()
    private val preferences = FakePreferencesRepository()
    private val location = FakeLocationRepository()
    private val player = FakeAmbientSoundPlayer()
    private val widget = FakeHomeWidgetUpdater()

    private fun viewModel(vararg args: Pair<String, Any?>) = ForecastViewModel(
        savedStateHandle = SavedStateHandle(mapOf(*args)),
        // Dakika sayacı yerine tek sinyal: test sonsuz bir zaman döngüsüne girmesin
        observeWeather = ObserveWeatherUseCase(weather, ExtrapolateForecastUseCase(), clock, Ticker { flowOf(Unit) }),
        refreshWeather = RefreshWeatherUseCase(weather),
        resolveDeviceLocation = ResolveDeviceLocationUseCase(location, preferences),
        calculateLifestyle = CalculateLifestyleIndicesUseCase(),
        selectSoundscape = SelectSoundscapeUseCase(),
        generateOutfit = GenerateOutfitAdviceUseCase(),
        preferences = preferences,
        ambientPlayer = player,
        widgetUpdater = widget,
        clock = clock,
        computeDispatcher = mainDispatcherRule.testDispatcher
    )

    @Test
    fun `province from navigation is refreshed and shown`() = runTest {
        val viewModel = viewModel(ForecastViewModel.ARG_PROVINCE to "Ankara")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertEquals(listOf("province:Ankara"), weather.refreshed.map { it.key })
        assertEquals("Ankara", state.location?.name)
        val content = state.content as LoadState.Success
        assertEquals(25, content.data.hourly.size)
        assertEquals("Şimdi", content.data.hourly.first().label)
        assertEquals(UserInterest.defaults, content.data.lifestyle.indices.map { it.interest }.toSet())
    }

    @Test
    fun `network error without saved data shows the error state`() = runTest {
        weather.error = WeatherError.NoConnection()
        val viewModel = viewModel(ForecastViewModel.ARG_PROVINCE to "Ankara")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        assertEquals(LoadState.Error(UiError.NoConnection), viewModel.state.value.content)
    }

    @Test
    fun `toggling an interest recalculates the lifestyle indices`() = runTest {
        val viewModel = viewModel(ForecastViewModel.ARG_PROVINCE to "Ankara")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onIntent(WeatherIntent.ToggleInterest(UserInterest.Gardening))

        val content = viewModel.state.value.content as LoadState.Success
        assertTrue(content.data.lifestyle.indices.any { it.interest == UserInterest.Gardening })
    }

    @Test
    fun `adding a favorite asks for notification permission and updates the widget`() = runTest {
        val viewModel = viewModel(ForecastViewModel.ARG_PROVINCE to "Ankara")
        val effects = mutableListOf<WeatherSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onIntent(WeatherIntent.ToggleFavorite)

        assertTrue(viewModel.state.value.isFavorite)
        assertTrue(effects.contains(WeatherSideEffect.RequestNotificationPermission))
        assertEquals(1, widget.refreshCount)
    }

    @Test
    fun `device location without permission asks for it`() = runTest {
        location.permission = false
        val viewModel = viewModel(ForecastViewModel.ARG_DEVICE to true)
        val effects = mutableListOf<WeatherSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        assertEquals(listOf(WeatherSideEffect.RequestLocationPermission), effects)
    }

    @Test
    fun `device location is resolved to the nearest province`() = runTest {
        val viewModel = viewModel(ForecastViewModel.ARG_DEVICE to true)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        val state = viewModel.state.value
        assertEquals("Ankara · Konumun", state.location?.name)
        assertTrue(state.location?.isDeviceLocation == true)
        assertTrue(state.content is LoadState.Success)
        // Konum favorilere eklenemez
        assertEquals(false, state.canFavorite)
    }

    @Test
    fun `ambient sound plays the noise colour chosen for the weather`() = runTest {
        val viewModel = viewModel(ForecastViewModel.ARG_PROVINCE to "Ankara")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onIntent(WeatherIntent.ToggleAmbientSound)
        val expected = (viewModel.state.value.content as LoadState.Success).data.soundscape.noiseColor
        assertEquals(expected, player.playing.value)

        viewModel.onIntent(WeatherIntent.ToggleAmbientSound)
        assertEquals(null as NoiseColor?, player.playing.value)
    }
}
