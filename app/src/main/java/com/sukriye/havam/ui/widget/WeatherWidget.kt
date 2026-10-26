package com.sukriye.havam.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sukriye.havam.MainActivity
import com.sukriye.havam.data.HomeWidgetUpdater
import com.sukriye.havam.data.UserPreferencesRepository
import com.sukriye.havam.data.WeatherRepository
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.model.WeatherSnapshot
import com.sukriye.havam.model.provinces
import com.sukriye.havam.model.weatherDescription
import com.sukriye.havam.model.weatherEmoji
import com.sukriye.havam.ui.theme.Night
import com.sukriye.havam.ui.theme.SoftWhite
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

// Widget Hilt'in doğrudan enjekte edebildiği bir sınıf değil; bağımlılıkları bu kapıdan alır
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun weatherRepository(): WeatherRepository
    fun preferences(): UserPreferencesRepository
}

// Ana ekran widget'ı. Glance, widget'ları Compose'a benzer şekilde yazmamızı sağlar
// ama içindeki Text, Column gibi parçalar Compose'unkiler değil, Glance'inkiler.
class WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val repository = entryPoint.weatherRepository()
        val favorites = entryPoint.preferences().favorites.value
        // Plaka sırasına göre ilk favori il; favori yoksa İstanbul
        val province = provinces.firstOrNull { it.name in favorites }
            ?: provinces.first { it.name == "İstanbul" }
        val location = WeatherLocation.of(province)
        try {
            repository.refresh(location)
        } catch (e: WeatherError) {
            // İnternet yoksa Room'daki son veri gösterilir
        }
        val snapshot = repository.observeWeather(location).first()
        provideContent {
            WidgetContent(name = province.name, snapshot = snapshot)
        }
    }
}

@Composable
private fun WidgetContent(name: String, snapshot: WeatherSnapshot?) {
    val white = ColorProvider(Color.White)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Night)
            .cornerRadius(24.dp)
            .padding(12.dp)
            // Widget'a dokununca uygulama açılsın
            .clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, style = TextStyle(color = white, fontSize = 15.sp, fontWeight = FontWeight.Medium))
        if (snapshot == null) {
            Text(text = "Veri yok", style = TextStyle(color = ColorProvider(SoftWhite), fontSize = 13.sp))
        } else {
            val current = snapshot.current
            Text(text = weatherEmoji(current.weatherCode, current.isDay), style = TextStyle(fontSize = 30.sp))
            Text(
                text = "${current.temperature.roundToInt()}°",
                style = TextStyle(color = white, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = weatherDescription(current.weatherCode),
                style = TextStyle(color = ColorProvider(SoftWhite), fontSize = 12.sp)
            )
        }
    }
}

// Android widget'ı bu sınıf üzerinden bulur (AndroidManifest.xml'deki <receiver>)
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

// Favoriler değişince ve arka plan senkronundan sonra çağrılır. Ana ekranda widget yoksa hiçbir şey yapmaz.
suspend fun refreshWeatherWidgets(context: Context) {
    WeatherWidget().updateAll(context)
}

class GlanceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HomeWidgetUpdater {
    override suspend fun refresh() = refreshWeatherWidgets(context)
}
