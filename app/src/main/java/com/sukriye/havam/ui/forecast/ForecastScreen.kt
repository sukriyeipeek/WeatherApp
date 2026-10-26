package com.sukriye.havam.ui.forecast

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sukriye.havam.model.Advice
import com.sukriye.havam.model.AirQuality
import com.sukriye.havam.model.CurrentConditions
import com.sukriye.havam.model.DataFreshness
import com.sukriye.havam.model.LifestyleIndex
import com.sukriye.havam.model.NoiseColor
import com.sukriye.havam.model.Province
import com.sukriye.havam.model.Soundscape
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.model.WeatherCode
import com.sukriye.havam.model.airQualityLabel
import com.sukriye.havam.model.formatWindow
import com.sukriye.havam.model.oneDecimal
import com.sukriye.havam.model.todayText
import com.sukriye.havam.model.weatherDescription
import com.sukriye.havam.model.weatherEmoji
import com.sukriye.havam.ui.LoadState
import com.sukriye.havam.ui.UiError
import com.sukriye.havam.ui.component.AtmosphericBackground
import com.sukriye.havam.ui.component.DynamicWeatherChart
import com.sukriye.havam.ui.component.EmptyView
import com.sukriye.havam.ui.component.ErrorView
import com.sukriye.havam.ui.component.GlassCard
import com.sukriye.havam.ui.component.ScoreRing
import com.sukriye.havam.ui.component.ShimmerList
import com.sukriye.havam.ui.component.levelColor
import com.sukriye.havam.ui.theme.Night
import com.sukriye.havam.ui.theme.SoftWhite
import com.sukriye.havam.ui.theme.airQualityColor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val Accent = Color(0xFFFFD54F)

/**
 * ViewModel'e bağlanan dış katman: durumu toplar, yan etkileri (mesaj, izin, link) işler.
 * Asıl çizim durumsuz [ForecastScreen]'de; böylece önizlemede ve testte kolayca kullanılır.
 */
@Composable
fun ForecastRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onIntent(WeatherIntent.LocationPermissionResult(result.values.any { it }))
    }
    // Bildirim izni reddedilirse uygulama yine çalışır, sadece fırtına uyarıları gelmez
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Yan etkiler sadece ekran görünürken işlenir; arka plandayken kanalda bekler
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is WeatherSideEffect.ShowMessage -> launch { snackbarHostState.showSnackbar(effect.message) }
                    is WeatherSideEffect.OpenUri -> {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.uri)))
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.fallbackUri)))
                        }
                    }
                    WeatherSideEffect.RequestLocationPermission -> locationLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                    )
                    WeatherSideEffect.RequestNotificationPermission -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
            }
        }
    }

    ForecastScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@Composable
fun ForecastScreen(
    state: WeatherUiState,
    onIntent: (WeatherIntent) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Night)
    ) {
        when (val content = state.content) {
            LoadState.Loading -> ForecastLoading(isLocating = state.isLocating)
            LoadState.Empty -> EmptyView(
                title = "Bu konum için tahmin bulunamadı",
                message = "Biraz sonra aşağı çekip yenilemeyi dene.",
                modifier = Modifier.align(Alignment.Center)
            )
            is LoadState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ErrorView(
                    message = content.error.message,
                    icon = errorIcon(content.error),
                    onRetry = if (content.error.canRetry) ({ onIntent(WeatherIntent.Retry) }) else null
                )
                TextButton(onClick = onBack) { Text("Listeye dön", color = SoftWhite) }
            }
            is LoadState.Success -> DashboardContent(
                state = state,
                weather = content.data,
                onIntent = onIntent,
                onBack = onBack
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(state: WeatherUiState, weather: WeatherContent, onIntent: (WeatherIntent) -> Unit, onBack: () -> Unit) {
    val snapshot = weather.snapshot
    val current = snapshot.current

    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphericBackground(
            weatherCode = current.weatherCode,
            isDay = current.isDay,
            intensity = atmosphereIntensity(current),
            wind = windStrength(current),
            modifier = Modifier.fillMaxSize()
        )
        // Aşağı kaydırdıkça kartların okunabilirliği için koyulaşan perde
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Night.copy(alpha = 0.25f),
                        1f to Night.copy(alpha = 0.85f)
                    )
                )
        )

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(WeatherIntent.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "top") {
                    TopBar(
                        name = snapshot.location.name,
                        isFavorite = state.isFavorite,
                        canFavorite = state.canFavorite,
                        onBack = onBack,
                        onToggleFavorite = { onIntent(WeatherIntent.ToggleFavorite) }
                    )
                }
                item(key = "hero") { Hero(current, weather) }
                item(key = "insight") { InsightCard(weather.lifestyle.insight) }
                // Önce günün seyri: indeks kartlarındaki "en iyi saat" önerileri bu grafiğe dayanır
                item(key = "chart") {
                    ChartCard(
                        points = weather.hourly,
                        selectedIndex = state.selectedHourIndex,
                        onSelect = { onIntent(WeatherIntent.SelectHour(it)) }
                    )
                }
                item(key = "chips") {
                    InterestChips(selected = state.interests, onToggle = { onIntent(WeatherIntent.ToggleInterest(it)) })
                }
                if (weather.lifestyle.indices.isEmpty()) {
                    item(key = "no_interest") {
                        EmptyView(
                            title = "İlgi alanı seçilmedi",
                            message = "Yukarıdan en az bir ilgi alanı seç, sana özel öneriler burada görünsün.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                items(weather.lifestyle.indices, key = { "index_${it.interest.name}" }) { index ->
                    LifestyleIndexCard(index, snapshot.utcOffsetSeconds, Modifier.animateItem())
                }
                item(key = "conditions") {
                    ConditionsCard(current, weather.sunrise, weather.sunset)
                }
                snapshot.airQuality?.let { air -> item(key = "air") { AirQualityCard(air) } }
                if (weather.outfit.isNotEmpty()) item(key = "outfit") { OutfitCard(weather.outfit) }
                item(key = "sound") {
                    SoundscapeCard(
                        soundscape = weather.soundscape,
                        playing = state.playingNoise,
                        onToggle = { onIntent(WeatherIntent.ToggleAmbientSound) },
                        onOpenPlaylist = { onIntent(WeatherIntent.OpenPlaylist) }
                    )
                }
                item(key = "source") {
                    Text(
                        text = "Veri: Open-Meteo · Hava kalitesi: Copernicus CAMS\n" +
                            "Yaşam indeksleri cihazında hesaplanır, hiçbir veri dışarı gönderilmez",
                        color = SoftWhite,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }
}

// Shader'ın yağış/bulut yoğunluğu: 0 sakin, 1 en şiddetli
private fun atmosphereIntensity(current: CurrentConditions): Float = when {
    WeatherCode.isThunder(current.weatherCode) || WeatherCode.isRain(current.weatherCode) ->
        (current.precipitation / 5.0).toFloat()
    WeatherCode.isSnow(current.weatherCode) -> (current.precipitation / 3.0).toFloat()
    WeatherCode.isFog(current.weatherCode) -> 1f
    current.weatherCode in 2..3 -> current.cloudCover / 100f
    else -> (current.uvIndex / 10.0).toFloat()
}.coerceIn(0f, 1f)

// Arka plandaki rüzgar şiddeti: 15 km/sa altı sakin sayılır, 50 km/sa ve üstü tam şiddet.
// Hamleler de hesaba katılır; ani esintiler ortalama rüzgardan daha çok hissedilir.
private fun windStrength(current: CurrentConditions): Float {
    val felt = maxOf(current.windSpeed, current.windGusts * 0.7)
    return ((felt - 15.0) / 35.0).toFloat().coerceIn(0f, 1f)
}

// ---------------------------------------------------------------- Üst kısım

@Composable
private fun TopBar(name: String, isFavorite: Boolean, canFavorite: Boolean, onBack: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleButton(text = "‹", onClick = onBack)
        Text(
            text = name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (canFavorite) {
            CircleButton(
                text = if (isFavorite) "★" else "☆",
                textColor = if (isFavorite) Accent else Color.White,
                onClick = onToggleFavorite
            )
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun CircleButton(text: String, onClick: () -> Unit, textColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = if (text == "‹") 28.sp else 22.sp)
    }
}

@Composable
private fun Hero(current: CurrentConditions, weather: WeatherContent) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FreshnessBadge(weather.freshness)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = weatherEmoji(current.weatherCode, current.isDay), fontSize = 72.sp)

        // Sıcaklık değişince rakam yukarı (ısınma) ya da aşağı (soğuma) kayarak değişir
        AnimatedContent(
            targetState = current.temperature.roundToInt(),
            transitionSpec = {
                val warmer = targetState > initialState
                (slideInVertically { height -> if (warmer) height else -height } + fadeIn()) togetherWith
                    (slideOutVertically { height -> if (warmer) -height else height } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "temperature"
        ) { temperature ->
            Text(text = "$temperature°", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light)
        }
        AnimatedContent(
            targetState = weatherDescription(current.weatherCode),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "description"
        ) { description ->
            Text(text = description, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        val range = if (weather.todayMax != null && weather.todayMin != null) {
            "  ·  ↑${weather.todayMax.roundToInt()}° ↓${weather.todayMin.roundToInt()}°"
        } else {
            ""
        }
        Text(
            text = "Hissedilen ${current.apparentTemperature.roundToInt()}°$range",
            color = SoftWhite,
            fontSize = 14.sp
        )
        Text(text = todayText(), color = SoftWhite, fontSize = 13.sp)
    }
}

@Composable
private fun FreshnessBadge(freshness: DataFreshness) {
    val (text, color) = when (freshness) {
        DataFreshness.Live -> "● Canlı" to Color(0xFF7ED957)
        is DataFreshness.Cached -> "📴 Çevrimdışı · ${ageText(freshness.ageMinutes)}" to Accent
        is DataFreshness.Extrapolated ->
            "🔮 Çevrimdışı tahmin · ${freshness.extrapolatedHours} saat trendden" to Color(0xFFB39DDB)
    }
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Night.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

private fun ageText(minutes: Long): String = when {
    minutes < 60 -> "$minutes dk önceki veri"
    minutes < 48 * 60 -> "${minutes / 60} sa önceki veri"
    else -> "${minutes / (24 * 60)} gün önceki veri"
}

// ---------------------------------------------------------------- Yaşam asistanı

@Composable
private fun InsightCard(insight: String) {
    GlassCard {
        Text(text = "✨ Hava Durumu Asistanı", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedContent(targetState = insight, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "insight") {
            Text(text = it, color = Color.White, fontSize = 16.sp, lineHeight = 22.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestChips(selected: Set<UserInterest>, onToggle: (UserInterest) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(text = "İlgi alanların", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserInterest.entries.forEach { interest ->
                val isSelected = interest in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(interest) },
                    label = { Text("${interest.emoji} ${interest.title}") },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        labelColor = Color.White.copy(alpha = 0.85f),
                        selectedContainerColor = Color.White.copy(alpha = 0.28f),
                        selectedLabelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.White.copy(alpha = 0.25f),
                        selectedBorderColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LifestyleIndexCard(index: LifestyleIndex, utcOffsetSeconds: Int, modifier: Modifier = Modifier) {
    val color = levelColor(index.level)
    GlassCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score = index.score, color = color)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index.interest.emoji} ${index.interest.title} · ${index.level.label}",
                    color = color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                AnimatedContent(targetState = index.headline, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "headline") {
                    Text(text = it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = index.advice, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)

        if (index.bestWindow != null || index.factors.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                index.bestWindow?.let { window ->
                    SmallChip(text = "⏱ ${formatWindow(window, utcOffsetSeconds)}", color = Accent, emphasized = true)
                }
                // "Neden bu skor?" sorusunun cevabı: en etkili faktörler
                index.factors.forEach { factor ->
                    val positive = factor.impact > 0
                    SmallChip(
                        text = "${if (positive) "+" else "−"}${abs(factor.impact)} ${factor.label}",
                        color = if (positive) Color(0xFF7ED957) else Color(0xFFFF8A80)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallChip(text: String, color: Color, emphasized: Boolean = false) {
    Text(
        text = text,
        color = if (emphasized) Night else color,
        fontSize = 11.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (emphasized) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// ---------------------------------------------------------------- Grafik ve koşullar

@Composable
private fun ChartCard(points: List<ChartPoint>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    GlassCard(contentPadding = 16.dp) {
        Text(
            text = "Önümüzdeki 24 saat",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp)
        )
        Text(
            text = "Dokun ya da kaydır: saat seç",
            color = SoftWhite,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        DynamicWeatherChart(points = points, selectedIndex = selectedIndex, onSelect = onSelect)
        AnimatedVisibility(visible = points.any { it.isExtrapolated }) {
            Text(
                text = "Kesikli çizgi: ağ yokken geçmiş verinin trendinden yapılan tahmin",
                color = Color(0xFFB39DDB),
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun ConditionsCard(current: CurrentConditions, sunrise: String?, sunset: String?) {
    GlassCard {
        Text(text = "Anlık koşullar", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(14.dp))
        Row {
            Stat("💨", "${current.windSpeed.roundToInt()} km/sa", "Rüzgar · hamle ${current.windGusts.roundToInt()}")
            Stat("💧", "%${current.humidity}", "Nem")
            Stat("☀️", current.uvIndex.oneDecimal(), "UV indeksi")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Stat("🌅", sunrise ?: "–", "Gün doğumu")
            Stat("🌇", sunset ?: "–", "Gün batımı")
        }
    }
}

@Composable
private fun RowScope.Stat(icon: String, value: String, label: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text = label, color = SoftWhite, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AirQualityCard(air: AirQuality) {
    val color = airQualityColor(air.aqi)
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // İndeksi renkli bir yuvarlağın içinde gösteriyoruz: yeşil iyi, kırmızı kötü
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = air.aqi.toString(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hava kalitesi · ${airQualityLabel(air.aqi)}",
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "PM2.5 ${air.pm25.roundToInt()} µg/m³ · PM10 ${air.pm10.roundToInt()} µg/m³",
                    color = SoftWhite,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun OutfitCard(advice: List<Advice>) {
    GlassCard {
        Text(text = "Ne giysem?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        advice.forEach { item ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.emoji, fontSize = 22.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = item.text, color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SoundscapeCard(soundscape: Soundscape, playing: NoiseColor?, onToggle: () -> Unit, onOpenPlaylist: () -> Unit) {
    GlassCard {
        Text(
            text = "🎧 Günün atmosferi · ${soundscape.phase.title}",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = soundscape.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(text = soundscape.description, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "♩ Önerilen tempo ${soundscape.suggestedBpm} BPM", color = SoftWhite, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.9f), contentColor = Night)
            ) {
                Text(if (playing != null) "■ Durdur" else "▶ ${soundscape.noiseColor.title}")
            }
            OutlinedButton(onClick = onOpenPlaylist) {
                Text("Spotify'da bul", color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------- Yükleniyor / hata

@Composable
private fun ForecastLoading(isLocating: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        AtmosphericBackground(weatherCode = 2, isDay = false, intensity = 0.4f, modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.statusBarsPadding()) {
            Text(
                text = if (isLocating) "Konumun bulunuyor…" else "Atmosfer verisi yükleniyor…",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )
            // İçerik gelmeden önce kartların iskeleti görünür
            ShimmerList(itemCount = 4)
        }
    }
}

private fun errorIcon(error: UiError): String = when (error) {
    UiError.NoConnection -> "📡"
    UiError.LocationUnavailable, UiError.LocationPermissionDenied -> "📍"
    else -> "⚠️"
}
