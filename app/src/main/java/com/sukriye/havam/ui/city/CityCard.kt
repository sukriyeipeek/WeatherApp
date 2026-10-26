package com.sukriye.havam.ui.city

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.weatherEmoji
import com.sukriye.havam.ui.theme.LocalAppPalette
import com.sukriye.havam.ui.theme.SkyDark
import com.sukriye.havam.ui.theme.SkyLight
import kotlin.math.roundToInt

internal val FavoriteYellow = Color(0xFFFFD54F)

// Listede bir il satırı: plaka, ad, anlık hava ve favori yıldızı
@Composable
fun CityCard(
    plate: Int,
    name: String,
    summary: CitySummary?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val palette = LocalAppPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Plaka numarası, detay ekranındaki panelle aynı mavi geçişli yuvarlakta
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Brush.verticalGradient(listOf(SkyLight, SkyDark)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = plate.toString().padStart(2, '0'),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            color = palette.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        // Sıcaklık henüz gelmediyse bu kısım boş kalır
        if (summary != null) {
            Text(text = weatherEmoji(summary.weatherCode, summary.isDay), fontSize = 20.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${summary.temperature.roundToInt()}°",
                color = palette.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(40.dp)
            )
        }
        // Yıldıza dokunmak satırı açmaz, sadece favoriye ekler/çıkarır
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFavorite) "★" else "☆",
                color = if (isFavorite) FavoriteYellow else palette.textSoft,
                fontSize = 22.sp
            )
        }
    }
}

@Preview
@Composable
private fun CityCardPreview() {
    CityCard(
        plate = 6,
        name = "Ankara",
        summary = CitySummary(15.0, 2, true),
        isFavorite = true,
        onClick = {},
        onToggleFavorite = {}
    )
}
