package com.sukriye.havam.ui.city

import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.Loaded
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.testing.FakeCityRepository
import com.sukriye.havam.testing.FakeHomeWidgetUpdater
import com.sukriye.havam.testing.FakePreferencesRepository
import com.sukriye.havam.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ankara = mapOf("Ankara" to CitySummary(15.0, 2, true))

    @Test
    fun `summaries are loaded when the screen opens`() {
        val repository = FakeCityRepository(result = Loaded(ankara, updatedAt = 0L, isOffline = false))

        val viewModel = CityViewModel(repository, FakePreferencesRepository(), FakeHomeWidgetUpdater())

        assertEquals(1, repository.callCount)
        assertEquals(15.0, viewModel.summaries.getValue("Ankara").temperature, 0.001)
        assertTrue(viewModel.statusText, viewModel.statusText.startsWith("81 il · Güncelleme"))
    }

    @Test
    fun `offline data is labelled as offline`() {
        val repository = FakeCityRepository(result = Loaded(ankara, updatedAt = 0L, isOffline = true))

        val viewModel = CityViewModel(repository, FakePreferencesRepository(), FakeHomeWidgetUpdater())

        assertTrue(viewModel.statusText, viewModel.statusText.contains("Çevrimdışı"))
    }

    @Test
    fun `error without cache asks the user to pull to refresh`() {
        val repository = FakeCityRepository(error = WeatherError.NoConnection())

        val viewModel = CityViewModel(repository, FakePreferencesRepository(), FakeHomeWidgetUpdater())

        assertEquals("İnternet bağlantısı yok · aşağı çekip yenile", viewModel.statusText)
        assertTrue(viewModel.summaries.isEmpty())
    }

    @Test
    fun `toggling a favorite refreshes the home screen widget`() {
        val preferences = FakePreferencesRepository()
        val widget = FakeHomeWidgetUpdater()
        val viewModel = CityViewModel(FakeCityRepository(), preferences, widget)

        viewModel.toggleFavorite("Ankara")

        assertEquals(setOf("Ankara"), viewModel.favorites.value)
        assertEquals(1, widget.refreshCount)
    }
}
