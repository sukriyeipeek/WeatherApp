package com.sukriye.havam.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.model.weatherEmoji
import com.sukriye.havam.ui.forecast.ChartPoint
import com.sukriye.havam.ui.theme.Night
import com.sukriye.havam.ui.theme.temperatureColor
import kotlin.math.floor
import kotlin.math.roundToInt

private val ChartHorizontalPadding = 20.dp
private val ChartTopSpace = 40.dp
private val ChartBottomSpace = 52.dp
private val RainBlue = Color(0xFF64B5F6)

/**
 * Saatlik sıcaklık grafiği: yumuşak Bezier eğrisi, sıcaklığa göre renk değiştiren çizgi,
 * dikey degrade dolgu, en yüksek/en düşük vurgu noktaları ve alt kısımda yağış olasılığı çubukları.
 * Dokunarak ya da parmağı kaydırarak saat seçilir; seçim noktası yay animasyonuyla kayar.
 * Çevrimdışı trend tahmininden gelen saatler kesikli çizgiyle çizilir.
 */
@Composable
fun DynamicWeatherChart(points: List<ChartPoint>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()

    // Veri değişince eğri soldan sağa "çizilerek" gelir
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    val selection by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "selection"
    )

    // Sürüklerken aynı saat için tekrar tekrar intent göndermemek için
    var lastDragIndex by remember { mutableIntStateOf(-1) }

    val description = remember(points) {
        if (points.isEmpty()) {
            "Saatlik veri yok"
        } else {
            val max = points.maxBy { it.temperature }
            val min = points.minBy { it.temperature }
            "Saatlik sıcaklık grafiği. En yüksek ${max.temperature.roundToInt()} derece saat ${max.label}, " +
                "en düşük ${min.temperature.roundToInt()} derece saat ${min.label}"
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .semantics { contentDescription = description }
            .pointerInput(points.size) {
                detectTapGestures { offset -> onSelect(indexAt(offset.x, size.width.toFloat(), points.size)) }
            }
            .pointerInput(points.size) {
                detectHorizontalDragGestures(
                    onDragEnd = { lastDragIndex = -1 },
                    onDragCancel = { lastDragIndex = -1 }
                ) { change, _ ->
                    change.consume()
                    val index = indexAt(change.position.x, size.width.toFloat(), points.size)
                    if (index != lastDragIndex) {
                        lastDragIndex = index
                        onSelect(index)
                    }
                }
            }
    ) {
        if (points.size < 2) return@Canvas
        val geometry = ChartGeometry(this, points)
        val revealX = geometry.left + (geometry.right - geometry.left) * reveal.value

        clipRect(right = revealX + 2.dp.toPx()) {
            drawRainBars(geometry, points)
            drawCurve(geometry, points)
        }
        drawHourLabels(geometry, points, textMeasurer)
        drawExtremes(geometry, points, textMeasurer, revealX, selectedIndex)
        drawSelection(geometry, points, textMeasurer, selection)
    }
}

// Ekran koordinatları: her nokta için x/y ve çizim alanının sınırları
private class ChartGeometry(scope: DrawScope, points: List<ChartPoint>) {
    val left = with(scope) { ChartHorizontalPadding.toPx() }
    val right = scope.size.width - left
    val top = with(scope) { ChartTopSpace.toPx() }
    val bottom = scope.size.height - with(scope) { ChartBottomSpace.toPx() }
    private val minTemp = points.minOf { it.temperature }
    private val span = (points.maxOf { it.temperature } - minTemp).coerceAtLeast(1.0)
    val offsets: List<Offset> = points.mapIndexed { i, p ->
        Offset(x(i.toFloat(), points.size), bottom - ((p.temperature - minTemp) / span).toFloat() * (bottom - top))
    }

    fun x(index: Float, count: Int) = left + (right - left) * index / (count - 1)

    // Eğri üzerinde iki nokta arasındaki ara konum (seçim animasyonu için)
    fun pointAt(position: Float): Offset {
        val i = floor(position).toInt().coerceIn(0, offsets.size - 2)
        val t = (position - i).coerceIn(0f, 1f)
        val a = offsets[i]
        val b = offsets[i + 1]
        // Bezier kontrol noktaları x ortasında olduğu için y'yi smoothstep ile yaklaşık takip ediyoruz
        val eased = t * t * (3 - 2 * t)
        return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * eased)
    }
}

// Komşu noktalar arasında kübik Bezier: kontrol noktaları x ortasında, böylece eğri aşırı salınmaz
private fun smoothPath(offsets: List<Offset>) = Path().apply {
    moveTo(offsets.first().x, offsets.first().y)
    for (i in 1 until offsets.size) {
        val p0 = offsets[i - 1]
        val p1 = offsets[i]
        val midX = (p0.x + p1.x) / 2
        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
}

private fun DrawScope.drawCurve(geometry: ChartGeometry, points: List<ChartPoint>) {
    val line = smoothPath(geometry.offsets)
    val fill = Path().apply {
        addPath(line)
        lineTo(geometry.offsets.last().x, geometry.bottom)
        lineTo(geometry.offsets.first().x, geometry.bottom)
        close()
    }
    val colors = points.map { temperatureColor(it.temperature) }
    val average = temperatureColor(points.map { it.temperature }.average())
    drawPath(
        path = fill,
        brush = Brush.verticalGradient(
            colors = listOf(average.copy(alpha = 0.45f), average.copy(alpha = 0.08f), Color.Transparent),
            startY = geometry.top,
            endY = geometry.bottom
        )
    )

    // Noktalar eşit aralıklı olduğu için yatay degradenin renkleri tam noktaların üstüne düşer
    val lineBrush = Brush.horizontalGradient(colors = colors, startX = geometry.left, endX = geometry.right)
    val solid = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    val dashed = Stroke(
        width = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
    )
    val firstEstimated = points.indexOfFirst { it.isExtrapolated }
    when {
        firstEstimated < 0 -> drawPath(line, lineBrush, style = solid)
        firstEstimated == 0 -> drawPath(line, lineBrush, style = dashed)
        else -> {
            val splitX = geometry.offsets[firstEstimated - 1].x
            clipRect(right = splitX) { drawPath(line, lineBrush, style = solid) }
            clipRect(left = splitX) { drawPath(line, lineBrush, style = dashed) }
        }
    }
}

private fun DrawScope.drawRainBars(geometry: ChartGeometry, points: List<ChartPoint>) {
    val barWidth = 5.dp.toPx()
    val maxHeight = 26.dp.toPx()
    points.forEachIndexed { i, p ->
        if (p.rainChance <= 0) return@forEachIndexed
        val height = maxHeight * p.rainChance / 100f
        drawRoundRect(
            color = RainBlue.copy(alpha = 0.25f + 0.45f * p.rainChance / 100f),
            topLeft = Offset(geometry.offsets[i].x - barWidth / 2, geometry.bottom - height),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(barWidth / 2)
        )
    }
}

private fun DrawScope.drawHourLabels(geometry: ChartGeometry, points: List<ChartPoint>, textMeasurer: TextMeasurer) {
    val style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
    val baseline = size.height - 18.dp.toPx()
    points.forEachIndexed { i, p ->
        if (i % 3 != 0) return@forEachIndexed
        val layout = textMeasurer.measure(p.label, style)
        val x = (geometry.offsets[i].x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        drawText(layout, topLeft = Offset(x, baseline))
    }
}

private fun DrawScope.drawExtremes(
    geometry: ChartGeometry,
    points: List<ChartPoint>,
    textMeasurer: TextMeasurer,
    revealX: Float,
    selectedIndex: Int
) {
    val maxIndex = points.indices.maxBy { points[it].temperature }
    val minIndex = points.indices.minBy { points[it].temperature }
    val style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

    listOf(maxIndex to true, minIndex to false).forEach { (index, isMax) ->
        val point = geometry.offsets[index]
        // Eğri henüz bu noktaya gelmediyse ya da seçim balonu buradaysa çizme
        if (point.x > revealX || index == selectedIndex) return@forEach
        val color = temperatureColor(points[index].temperature)
        drawCircle(color = color.copy(alpha = 0.25f), radius = 11.dp.toPx(), center = point)
        drawCircle(color = color, radius = 5.5.dp.toPx(), center = point)
        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = point, style = Stroke(1.5.dp.toPx()))

        val text = "${if (isMax) "↑" else "↓"} ${points[index].temperature.roundToInt()}°"
        val layout = textMeasurer.measure(text, style)
        val x = (point.x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
        val y = if (isMax) point.y - layout.size.height - 12.dp.toPx() else point.y + 12.dp.toPx()
        drawText(layout, topLeft = Offset(x, y))
    }
}

private fun DrawScope.drawSelection(geometry: ChartGeometry, points: List<ChartPoint>, textMeasurer: TextMeasurer, position: Float) {
    val clamped = position.coerceIn(0f, (points.size - 1).toFloat())
    val point = geometry.pointAt(clamped)
    val nearest = points[clamped.roundToInt()]

    drawLine(
        color = Color.White.copy(alpha = 0.35f),
        start = Offset(point.x, geometry.top - 8.dp.toPx()),
        end = Offset(point.x, geometry.bottom),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
    )
    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = point)
    drawCircle(color = temperatureColor(nearest.temperature), radius = 4.5.dp.toPx(), center = point)

    // Seçili saatin bilgi balonu
    val text = "${weatherEmoji(nearest.weatherCode, nearest.isDay)} ${nearest.label} · ${nearest.temperature.roundToInt()}°" +
        if (nearest.rainChance > 0) " · ☔%${nearest.rainChance}" else ""
    val layout = textMeasurer.measure(text, TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium))
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val bubbleSize = Size(layout.size.width + padH * 2, layout.size.height + padV * 2)
    val bubbleX = (point.x - bubbleSize.width / 2).coerceIn(0f, size.width - bubbleSize.width)
    val bubbleY = (point.y - bubbleSize.height - 16.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(
        color = Night.copy(alpha = 0.85f),
        topLeft = Offset(bubbleX, bubbleY),
        size = bubbleSize,
        cornerRadius = CornerRadius(bubbleSize.height / 2)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.3f),
        topLeft = Offset(bubbleX, bubbleY),
        size = bubbleSize,
        cornerRadius = CornerRadius(bubbleSize.height / 2),
        style = Stroke(1.dp.toPx())
    )
    drawText(layout, topLeft = Offset(bubbleX + padH, bubbleY + padV))
}

// Dokunulan x konumuna en yakın saat
private fun Density.indexAt(x: Float, width: Float, count: Int): Int {
    if (count < 2) return 0
    val left = ChartHorizontalPadding.toPx()
    val fraction = ((x - left) / (width - 2 * left)).coerceIn(0f, 1f)
    return (fraction * (count - 1)).roundToInt()
}

@Preview(widthDp = 380, heightDp = 260)
@Composable
private fun DynamicWeatherChartPreview() {
    val temps = listOf(14.0, 13.5, 13.0, 13.2, 14.8, 17.0, 19.5, 21.0, 22.4, 23.0, 22.1, 20.0, 18.2, 16.9, 16.0)
    DynamicWeatherChart(
        points = temps.mapIndexed { i, t ->
            ChartPoint(
                epochSeconds = i * 3600L,
                label = if (i == 0) "Şimdi" else "%02d:00".format((6 + i) % 24),
                temperature = t,
                rainChance = if (i in 9..12) 20 + i * 5 else 0,
                weatherCode = if (i in 9..12) 61 else 1,
                isDay = i in 1..11,
                isExtrapolated = i >= 11
            )
        },
        selectedIndex = 6,
        onSelect = {},
        modifier = Modifier
            .background(Night)
            .padding(vertical = 8.dp)
    )
}
