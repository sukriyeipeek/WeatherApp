package com.sukriye.havam.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.sukriye.havam.ui.theme.SkyDark
import com.sukriye.havam.ui.theme.SkyLight
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

// Panelin üstünde oynayan animasyon türü
enum class WeatherEffect { Sun, Stars, Clouds, Rain, Storm, Snow, Fog }

// Bir hava durumunun arka plan renkleri ve animasyonu
data class WeatherScene(val colors: List<Color>, val effect: WeatherEffect)

fun sceneFor(weatherCode: Int, isDay: Boolean): WeatherScene {
    val nightColors = listOf(Color(0xFF3B4A8C), Color(0xFF151D45))
    return when (weatherCode) {
        0, 1 -> if (isDay) {
            WeatherScene(listOf(SkyLight, SkyDark), WeatherEffect.Sun)
        } else {
            WeatherScene(nightColors, WeatherEffect.Stars)
        }
        2 -> WeatherScene(if (isDay) listOf(SkyLight, SkyDark) else nightColors, WeatherEffect.Clouds)
        3 -> WeatherScene(listOf(Color(0xFF6C86AD), Color(0xFF3D5276)), WeatherEffect.Clouds)
        45, 48 -> WeatherScene(listOf(Color(0xFF8A9BB0), Color(0xFF56677D)), WeatherEffect.Fog)
        71, 73, 75, 77, 85, 86 -> WeatherScene(listOf(Color(0xFF8FB2D9), Color(0xFF4F78A8)), WeatherEffect.Snow)
        95, 96, 99 -> WeatherScene(listOf(Color(0xFF4A4F7A), Color(0xFF1E2140)), WeatherEffect.Storm)
        in 51..67, in 80..82 -> WeatherScene(listOf(Color(0xFF4E6A8F), Color(0xFF26395A)), WeatherEffect.Rain)
        else -> WeatherScene(listOf(SkyLight, SkyDark), WeatherEffect.Clouds)
    }
}

// Her parçacığın (damla, kar tanesi, yıldız) sabit rastgele özellikleri.
// Konumu her karede zamana göre bu değerlerden hesaplanır.
private data class Particle(val x: Float, val offset: Float, val speed: Float, val size: Float)

@Composable
fun WeatherEffects(effect: WeatherEffect, wind: Float = 0f, modifier: Modifier = Modifier) {
    val particles = remember(effect) {
        val random = Random(effect.ordinal)
        List(if (effect == WeatherEffect.Storm) 90 else 60) {
            Particle(
                x = random.nextFloat(),
                offset = random.nextFloat(),
                speed = 0.7f + random.nextFloat() * 0.6f,
                size = random.nextFloat()
            )
        }
    }
    // 0'dan 1'e sürekli dönen zaman sayacı; animasyonun hızı türe göre değişiyor
    val duration = when (effect) {
        WeatherEffect.Rain, WeatherEffect.Storm -> 900
        WeatherEffect.Snow -> 9000
        WeatherEffect.Clouds, WeatherEffect.Fog -> 40000
        WeatherEffect.Stars -> 3000
        WeatherEffect.Sun -> 4000
    }
    val transition = rememberInfiniteTransition(label = "weather")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing)),
        label = "time"
    )
    // Fırtınada şimşek için ayrı, daha yavaş sayaç
    val flashTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "flash"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "pulse"
    )

    // Rüzgar çizgileri için kendi sayacı: tür ne olursa olsun aynı hızda eser
    val windTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "wind"
    )
    val windStrength = wind.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        when (effect) {
            WeatherEffect.Sun -> drawSun(pulse)
            WeatherEffect.Stars -> drawStars(particles, time)
            WeatherEffect.Clouds -> drawClouds(particles, time, alpha = 0.12f)
            WeatherEffect.Fog -> drawFog(time)
            WeatherEffect.Rain -> drawRain(particles, time, windStrength)
            WeatherEffect.Storm -> {
                drawRain(particles, time, windStrength)
                // Şimşek: döngünün belli anlarında tüm panel kısa süre beyazlaşır
                if (flashTime in 0.70f..0.72f || flashTime in 0.75f..0.76f) {
                    drawRect(Color.White.copy(alpha = 0.35f))
                }
            }
            WeatherEffect.Snow -> drawSnow(particles, time)
        }
        if (windStrength > 0f) drawWind(particles, windTime, windStrength)
    }
}

// Soldan sağa hızla geçen ince rüzgar çizgileri; rüzgar arttıkça çoğalır ve belirginleşir
private fun DrawScope.drawWind(particles: List<Particle>, time: Float, strength: Float) {
    val count = (6 + strength * 14).toInt()
    particles.take(count).forEach { p ->
        val lineLength = size.width * (0.15f + p.size * 0.2f)
        val x = ((p.offset + time * p.speed) % 1f) * (size.width + lineLength) - lineLength
        val y = p.x * size.height
        drawLine(
            color = Color.White.copy(alpha = 0.10f + strength * 0.15f),
            start = Offset(x, y),
            end = Offset(x + lineLength, y - lineLength * 0.04f),
            strokeWidth = 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawSun(pulse: Float) {
    val center = Offset(size.width * 0.85f, size.height * 0.12f)
    val radius = size.width * 0.45f * pulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x66FFE082), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawStars(particles: List<Particle>, time: Float) {
    particles.take(40).forEach { p ->
        // Her yıldız kendi zamanında parlayıp sönüyor
        val twinkle = 0.25f + 0.75f * abs(sin((time + p.offset) * PI.toFloat()))
        drawCircle(
            color = Color.White.copy(alpha = twinkle),
            radius = (1 + p.size * 1.5f).dp.toPx(),
            center = Offset(p.x * size.width, p.offset * size.height * 0.6f)
        )
    }
}

private fun DrawScope.drawClouds(particles: List<Particle>, time: Float, alpha: Float) {
    particles.take(5).forEachIndexed { i, p ->
        val cloudWidth = size.width * (0.5f + p.size * 0.4f)
        // Bulutlar soldan sağa yavaşça kayıyor, ekrandan çıkınca soldan tekrar giriyor
        val x = ((p.offset + time * p.speed) % 1f) * (size.width + cloudWidth) - cloudWidth / 2
        val y = size.height * (0.08f + i * 0.12f)
        drawOval(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(x - cloudWidth / 2, y),
            size = androidx.compose.ui.geometry.Size(cloudWidth, cloudWidth * 0.35f)
        )
    }
}

private fun DrawScope.drawFog(time: Float) {
    repeat(4) { i ->
        val shift = sin((time + i * 0.25f) * 2 * PI.toFloat()) * size.width * 0.1f
        drawRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(shift - size.width * 0.1f, size.height * (0.15f + i * 0.2f)),
            size = androidx.compose.ui.geometry.Size(size.width * 1.2f, size.height * 0.08f)
        )
    }
}

private fun DrawScope.drawRain(particles: List<Particle>, time: Float, wind: Float) {
    val length = 18.dp.toPx()
    // Rüzgar damlaları sağa yatırır
    val slant = (-4f + wind * 14f).dp.toPx()
    particles.forEach { p ->
        val y = ((p.offset + time * p.speed) % 1f) * (size.height + length) - length
        val x = p.x * size.width
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(x, y),
            end = Offset(x + slant, y + length),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

private fun DrawScope.drawSnow(particles: List<Particle>, time: Float) {
    particles.forEach { p ->
        val progress = (p.offset + time * p.speed) % 1f
        // Kar taneleri düşerken sağa sola salınıyor
        val sway = sin((progress * 4 + p.offset) * 2 * PI.toFloat()) * 10.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = (2 + p.size * 2.5f).dp.toPx(),
            center = Offset(p.x * size.width + sway, progress * size.height)
        )
    }
}
