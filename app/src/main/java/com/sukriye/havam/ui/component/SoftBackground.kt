package com.sukriye.havam.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sukriye.havam.ui.theme.LocalAppPalette

/**
 * Liste, harita, favoriler ve ayarlar ekranlarının ortak zemini. Düz renk yerine yukarıdan
 * aşağı yumuşak bir geçiş ve sağ üstte gökyüzü mavisi, sol altta mor hafif birer ışık.
 * Renkler açık/koyu temaya göre [LocalAppPalette]'ten gelir.
 */
@Composable
fun SoftBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalAppPalette.current
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundMiddle, palette.backgroundBottom))
            )
            .drawBehind {
                drawGlow(palette.glow, Offset(size.width * 0.95f, size.height * 0.02f), size.width * 0.95f)
                drawGlow(palette.glowSecondary, Offset(size.width * 0.05f, size.height * 0.85f), size.width * 0.85f)
            },
        content = content
    )
}

private fun DrawScope.drawGlow(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}
