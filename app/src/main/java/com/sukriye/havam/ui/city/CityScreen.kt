package com.sukriye.havam.ui.city

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.Province
import com.sukriye.havam.model.provinces
import com.sukriye.havam.model.searchProvinces
import com.sukriye.havam.ui.component.SoftBackground
import com.sukriye.havam.ui.theme.LocalAppPalette
import com.sukriye.havam.ui.theme.SkyDark
import com.sukriye.havam.ui.theme.SkyLight
import kotlinx.coroutines.delay

/**
 * ViewModel'e bağlanan dış katman: state'i toplar, olayları ViewModel'e ya da navigasyona iletir.
 * Asıl çizim durumsuz [CityScreen]'de.
 */
@Composable
fun CityRoute(
    onCityClick: (String) -> Unit,
    onLocate: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CityViewModel = hiltViewModel()
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    CityScreen(
        cities = provinces,
        summaries = viewModel.summaries,
        favorites = favorites,
        isRefreshing = viewModel.isRefreshing,
        isLocating = false,
        locationMessage = viewModel.locationMessage,
        onRefresh = viewModel::refresh,
        onCityClick = onCityClick,
        onToggleFavorite = viewModel::toggleFavorite,
        // İzin verildikten sonra çağrılır: tam konumun mikro-iklim paneli açılır
        onLocate = onLocate,
        onLocationDenied = viewModel::onLocationDenied,
        onDismissLocationMessage = viewModel::dismissLocationMessage,
        onOpenMap = onOpenMap,
        onOpenFavorites = onOpenFavorites,
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityScreen(
    cities: List<Province>,
    summaries: Map<String, CitySummary>,
    favorites: Set<String>,
    isRefreshing: Boolean,
    isLocating: Boolean,
    locationMessage: String?,
    onRefresh: () -> Unit,
    onCityClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onLocate: () -> Unit,
    onLocationDenied: () -> Unit,
    onDismissLocationMessage: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Plaka numarası listedeki sıradan geliyor; arama sonuçlarında da doğru numara görünsün diye önceden hesaplanır
    val plates = remember(cities) { cities.withIndex().associate { (i, p) -> p.name to i + 1 } }

    // Arama yalnızca bu ekranın durumu: ViewModel'e gerek yok, ekran döndürülünce de korunur
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val visibleCities = remember(cities, query) { searchProvinces(cities, query) }
    val closeSearch = {
        query = ""
        isSearching = false
    }
    // Arama açıkken geri tuşu önce aramayı kapatır
    BackHandler(enabled = isSearching, onBack = closeSearch)

    SoftBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.statusBarsPadding()) {
            ListHeader(
                isLocating = isLocating,
                locationMessage = locationMessage,
                onLocate = onLocate,
                onLocationDenied = onLocationDenied,
                onDismissLocationMessage = onDismissLocationMessage,
                onOpenMap = onOpenMap,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = { isSearching = true },
                onOpenSettings = onOpenSettings
            )
            AnimatedVisibility(visible = isSearching) {
                SearchField(query = query, onQueryChange = { query = it }, onClose = closeSearch)
            }

            // Listeyi en üstten aşağı çekince sıcaklıklar yenilenir
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                // LazyColumn sadece ekranda görünen satırları çizer, aşağı kaydırdıkça yenilerini oluşturur
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Son kart alttaki gezinme çubuğunun altında kalmasın
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (visibleCities.isEmpty()) {
                        item(key = "no_result") { NoSearchResult(query) }
                    }
                    items(visibleCities, key = { it.name }) { city ->
                        CityCard(
                            plate = plates.getValue(city.name),
                            name = city.name,
                            summary = summaries[city.name],
                            isFavorite = city.name in favorites,
                            onClick = { onCityClick(city.name) },
                            onToggleFavorite = { onToggleFavorite(city.name) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListHeader(
    isLocating: Boolean,
    locationMessage: String?,
    onLocate: () -> Unit,
    onLocationDenied: () -> Unit,
    onDismissLocationMessage: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalAppPalette.current
    // Android konum iznini kullanıcıya bir pencereyle sorar, cevap bu fonksiyona gelir
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) onLocate() else onLocationDenied()
    }

    // Konum mesajı birkaç saniye görünüp kaybolsun
    LaunchedEffect(locationMessage) {
        if (locationMessage != null) {
            delay(4000)
            onDismissLocationMessage()
        }
    }

    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Türkiye", color = palette.textSoft, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Hava Durumu",
                    color = palette.text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            CircleIconButton(icon = "🔍", onClick = onOpenSearch)
            Spacer(modifier = Modifier.width(10.dp))
            // Ayarlar: tema seçimi
            CircleIconButton(icon = "⚙️", onClick = onOpenSettings)
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Dar ekranlarda düğmeler sığmazsa alt satıra geçer
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PillButton(
                text = if (isLocating) "Aranıyor…" else "📍 Konumum",
                onClick = {
                    val granted = listOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                    if (granted) {
                        onLocate()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            )
                        )
                    }
                }
            )
            PillButton(text = "🗺️ Harita", onClick = onOpenMap)
            PillButton(text = "⭐ Favoriler", onClick = onOpenFavorites)
        }
        if (locationMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = locationMessage, color = FavoriteYellow, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CircleIconButton(icon: String, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 20.sp)
    }
}

// Açılınca klavye kendiliğinden gelir; yazdıkça liste süzülür, ✕ aramayı kapatır
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val palette = LocalAppPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.5.dp, SkyLight, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🔍", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 16.sp),
            cursorBrush = SolidColor(SkyLight),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp)
                .focusRequester(focusRequester),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) Text(text = "İl adı ya da plaka ara", color = palette.textSoft, fontSize = 16.sp)
                    field()
                }
            }
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✕", color = palette.textSoft, fontSize = 16.sp)
        }
    }
}

@Composable
private fun NoSearchResult(query: String) {
    val palette = LocalAppPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🔍", fontSize = 36.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "“${query.trim()}” ile eşleşen il yok",
            color = palette.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "İl adını ya da plaka numarasını dene",
            color = palette.textSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(SkyLight, SkyDark)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun CityScreenPreview() {
    CityScreen(
        cities = provinces,
        summaries = mapOf(
            "Adana" to CitySummary(24.0, 0, true),
            "Ankara" to CitySummary(15.0, 2, true),
            "Ağrı" to CitySummary(3.0, 71, true)
        ),
        favorites = setOf("Ankara"),
        isRefreshing = false,
        isLocating = false,
        locationMessage = null,
        onRefresh = {},
        onCityClick = {},
        onToggleFavorite = {},
        onLocate = {},
        onLocationDenied = {},
        onDismissLocationMessage = {},
        onOpenMap = {},
        onOpenFavorites = {},
        onOpenSettings = {}
    )
}
