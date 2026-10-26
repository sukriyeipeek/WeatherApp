package com.sukriye.havam.ui.settings

import com.sukriye.havam.model.ThemeMode
import com.sukriye.havam.testing.FakePreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `dark theme is the default`() {
        val viewModel = SettingsViewModel(FakePreferencesRepository())
        assertEquals(ThemeMode.Dark, viewModel.themeMode.value)
    }

    @Test
    fun `selected theme is saved to preferences`() {
        val preferences = FakePreferencesRepository()
        val viewModel = SettingsViewModel(preferences)

        viewModel.selectTheme(ThemeMode.Light)

        assertEquals(ThemeMode.Light, preferences.themeMode.value)
        assertEquals(ThemeMode.Light, viewModel.themeMode.value)
    }
}
