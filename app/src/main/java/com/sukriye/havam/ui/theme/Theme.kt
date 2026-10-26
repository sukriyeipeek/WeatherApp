package com.sukriye.havam.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HavamDarkColorScheme = darkColorScheme(
    primary = SkyLight,
    secondary = Color(0xFFFFD54F),
    tertiary = Color(0xFFB39DDB),
    background = Night,
    surface = NightCard,
    onPrimary = Night,
    onBackground = Color.White,
    onSurface = Color.White
)

private val HavamLightColorScheme = lightColorScheme(
    primary = SkyDark,
    secondary = Color(0xFFF5A623),
    tertiary = Color(0xFF7E57C2),
    background = LightPalette.backgroundMiddle,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = LightPalette.text,
    onSurface = LightPalette.text
)

// Açık/koyu tema ayarlardan seçilir. Android 12+ cihazlarda vurgu renkleri duvar kağıdından gelir
// (Dynamic Color); zemin ve kartlar her zaman Havam'ın kendi paletinden.
@Composable
fun HavamTheme(darkTheme: Boolean = true, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(LocalContext.current).copy(background = Night, surface = NightCard)
        dynamic -> dynamicLightColorScheme(LocalContext.current).copy(background = palette.backgroundMiddle, surface = Color.White)
        darkTheme -> HavamDarkColorScheme
        else -> HavamLightColorScheme
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
