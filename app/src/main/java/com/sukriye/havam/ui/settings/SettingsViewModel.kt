package com.sukriye.havam.ui.settings

import androidx.lifecycle.ViewModel
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

// Ayarlar ekranı: seçilen tema kaydedilir, MainActivity aynı akışı dinleyip temayı anında değiştirir
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode

    fun selectTheme(mode: ThemeMode) {
        preferences.setThemeMode(mode)
    }
}
