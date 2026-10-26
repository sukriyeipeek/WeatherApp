package com.sukriye.havam.ui.city

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.provinces
import com.sukriye.havam.ui.component.ScreenHeader
import com.sukriye.havam.ui.component.SoftBackground
import com.sukriye.havam.ui.theme.AppPalette
import com.sukriye.havam.ui.theme.LocalAppPalette
import com.sukriye.havam.ui.theme.Night
import com.sukriye.havam.ui.theme.SkyLight
import com.sukriye.havam.ui.theme.temperatureColor
import com.sukriye.havam.ui.theme.temperatureStops
import kotlin.math.cos
import kotlin.math.roundToInt

// Haritanın kapsadığı alan (Türkiye'nin biraz dışı dahil)
private const val MIN_LON = 25.6
private const val MAX_LON = 45.0
private const val MIN_LAT = 35.7
private const val MAX_LAT = 42.2

// Enlem 39° civarında 1 derece boylam, 1 derece enlemden kısadır. Harita basık görünmesin diye
// genişlik/yükseklik oranını buna göre ayarlıyoruz.
private val MAP_RATIO =
    ((MAX_LON - MIN_LON) * cos(Math.toRadians((MIN_LAT + MAX_LAT) / 2)) / (MAX_LAT - MIN_LAT)).toFloat()

// Haritanın ekrandaki yüksekliği; genişliği bu yükseklikten ve MAP_RATIO'dan hesaplanır
private val MAP_HEIGHT = 380.dp

// Harita, liste ekranıyla aynı CityViewModel'i kullanır: 81 ilin verisi ikinci kez indirilmez
@Composable
fun MapRoute(viewModel: CityViewModel, onCityClick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MapScreen(
        summaries = viewModel.summaries,
        statusText = viewModel.statusText,
        onCityClick = onCityClick,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun MapScreen(
    summaries: Map<String, CitySummary>,
    statusText: String,
    onCityClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalAppPalette.current
    SoftBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            ScreenHeader(title = "Türkiye Haritası", subtitle = statusText, onBack = onBack)
            Text(
                text = "Haritayı yana kaydır, bir ile dokunup detayını aç",
                color = palette.textSoft,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )

            // Harita telefon ekranından yaklaşık iki kat geniş; yana kaydırılarak gezilir
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(MAP_HEIGHT)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    TurkeyMap(
                        summaries = summaries,
                        palette = palette,
                        onCityClick = onCityClick,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(MAP_RATIO, matchHeightConstraintsFirst = true)
                            .padding(16.dp)
                    )
                }
            }

            TemperatureLegend(palette = palette, modifier = Modifier.padding(24.dp))
        }
    }
}

@Composable
private fun TurkeyMap(
    summaries: Map<String, CitySummary>,
    palette: AppPalette,
    onCityClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

    Canvas(
        modifier = modifier.pointerInput(summaries) {
            // Dokunulan noktaya en yakın il, yeterince yakınsa açılır
            detectTapGestures { tap ->
                val nearest = provinces.minBy { p ->
                    val point = project(p.longitude, p.latitude, size.width.toFloat(), size.height.toFloat())
                    (point - tap).getDistanceSquared()
                }
                val point = project(nearest.longitude, nearest.latitude, size.width.toFloat(), size.height.toFloat())
                if ((point - tap).getDistance() < 28.dp.toPx()) onCityClick(nearest.name)
            }
        }
    ) {
        // Önce Türkiye'nin sınırını çiziyoruz
        turkeyOutline.forEach { ring ->
            val path = Path()
            ring.forEachIndexed { i, (lon, lat) ->
                val point = project(lon, lat, size.width, size.height)
                if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            path.close()
            drawPath(path, color = palette.mapLand)
            drawPath(path, color = SkyLight.copy(alpha = 0.5f), style = Stroke(width = 1.5.dp.toPx()))
        }

        // Sonra her il için sıcaklığına göre renklenmiş bir yuvarlak ve içinde derece
        val radius = 12.dp.toPx()
        provinces.forEach { province ->
            val center = project(province.longitude, province.latitude, size.width, size.height)
            val summary = summaries[province.name]
            if (summary == null) {
                drawCircle(color = palette.textSoft.copy(alpha = 0.4f), radius = 4.dp.toPx(), center = center)
            } else {
                drawCircle(color = temperatureColor(summary.temperature), radius = radius, center = center)
                drawCircle(color = Night.copy(alpha = 0.6f), radius = radius, center = center, style = Stroke(1.dp.toPx()))
                val label = textMeasurer.measure("${summary.temperature.roundToInt()}°", labelStyle)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f)
                )
            }
        }
    }
}

// Enlem/boylamı ekrandaki piksel konumuna çevirir (basit eşit dikdörtgen izdüşüm)
private fun project(longitude: Double, latitude: Double, width: Float, height: Float): Offset = Offset(
    x = ((longitude - MIN_LON) / (MAX_LON - MIN_LON)).toFloat() * width,
    y = ((MAX_LAT - latitude) / (MAX_LAT - MIN_LAT)).toFloat() * height
)

@Composable
private fun TemperatureLegend(palette: AppPalette, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(temperatureStops.map { it.second }))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            temperatureStops.forEach { (t, _) ->
                Text(text = "${t.roundToInt()}°", color = palette.textSoft, fontSize = 12.sp)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun MapScreenPreview() {
    MapScreen(
        summaries = provinces.associate { it.name to CitySummary(it.latitude - 20, 0, true) },
        statusText = "81 il · 14:30'da güncellendi",
        onCityClick = {},
        onBack = {}
    )
}
