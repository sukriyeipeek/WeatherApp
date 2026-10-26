package com.sukriye.havam.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukriye.havam.model.ThemeMode
import com.sukriye.havam.ui.component.ScreenHeader
import com.sukriye.havam.ui.component.SoftBackground
import com.sukriye.havam.ui.theme.LocalAppPalette
import com.sukriye.havam.ui.theme.SkyDark
import com.sukriye.havam.ui.theme.SkyLight

@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    SettingsScreen(
        themeMode = themeMode,
        onSelectTheme = viewModel::selectTheme,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(themeMode: ThemeMode, onSelectTheme: (ThemeMode) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    SoftBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(title = "Ayarlar", onBack = onBack)
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Görünüm",
                    color = palette.textSoft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                ThemeMode.entries.forEach { mode ->
                    ThemeOption(mode = mode, selected = mode == themeMode, onClick = { onSelectTheme(mode) })
                }
                Text(
                    text = "Hava paneli her temada o anki gökyüzüyle çizilir.",
                    color = palette.textSoft,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
        }
    }
}

// Seçili tema mavi çerçeve ve dolu yuvarlakla gösterilir
@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) SkyLight else palette.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = mode.emoji, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = mode.title, color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(text = mode.description, color = palette.textSoft, fontSize = 13.sp)
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, if (selected) SkyDark else palette.textSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(SkyDark, CircleShape)
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 600)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(themeMode = ThemeMode.Dark, onSelectTheme = {}, onBack = {})
}
