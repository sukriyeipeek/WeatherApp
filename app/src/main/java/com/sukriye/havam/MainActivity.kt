package com.sukriye.havam

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.model.ThemeMode
import com.sukriye.havam.ui.HavamNavHost
import com.sukriye.havam.ui.theme.HavamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Uygulamanın kapısı: ayarlardaki temayı kurar ve ekranlar arası geçişi HavamNavHost'a bırakır
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> isSystemInDarkTheme()
            }
            // Saat/pil ikonları temaya uysun: koyu zeminde beyaz, açık zeminde koyu
            LaunchedEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HavamTheme(darkTheme = darkTheme) {
                // Scaffold kullanmıyoruz: ekranlar arka planını durum çubuğunun arkasına kadar
                // uzatıyor ve boşluklarını kendisi ayarlıyor
                HavamNavHost()
            }
        }
    }
}
