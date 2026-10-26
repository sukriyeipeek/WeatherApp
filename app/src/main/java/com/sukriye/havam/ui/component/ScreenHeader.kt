package com.sukriye.havam.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.ui.theme.LocalAppPalette

// Alt ekranların (harita, favoriler, ayarlar) başlığı: geri düğmesi, başlık ve isteğe bağlı alt satır
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val palette = LocalAppPalette.current
    Row(
        modifier = modifier.padding(start = 16.dp, end = 24.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, palette.textSoft.copy(alpha = 0.5f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "‹", color = palette.text, fontSize = 28.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(text = subtitle, color = palette.textSoft, fontSize = 13.sp)
        }
    }
}
