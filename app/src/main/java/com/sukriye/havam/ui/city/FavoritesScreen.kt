package com.sukriye.havam.ui.city

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sukriye.havam.model.CitySummary
import com.sukriye.havam.model.Province
import com.sukriye.havam.model.provinces
import com.sukriye.havam.ui.component.ScreenHeader
import com.sukriye.havam.ui.component.SoftBackground
import com.sukriye.havam.ui.theme.LocalAppPalette

// Favoriler, liste ekranıyla aynı CityViewModel'i kullanır: sıcaklıklar ikinci kez indirilmez
@Composable
fun FavoritesRoute(viewModel: CityViewModel, onCityClick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    FavoritesScreen(
        cities = provinces,
        summaries = viewModel.summaries,
        favorites = favorites,
        onCityClick = onCityClick,
        onToggleFavorite = viewModel::toggleFavorite,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
fun FavoritesScreen(
    cities: List<Province>,
    summaries: Map<String, CitySummary>,
    favorites: Set<String>,
    onCityClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Plaka numarası 81 il listesindeki sıradan gelir; favoriler de plaka sırasıyla dizilir
    val plates = remember(cities) { cities.withIndex().associate { (i, p) -> p.name to i + 1 } }
    val favoriteCities = cities.filter { it.name in favorites }

    SoftBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.statusBarsPadding()) {
            ScreenHeader(
                title = "Favoriler",
                subtitle = if (favoriteCities.isEmpty()) null else "${favoriteCities.size} il",
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (favoriteCities.isEmpty()) {
                EmptyFavorites(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Yıldıza dokunulunca il favorilerden çıkar ve listeden kaybolur
                    items(favoriteCities, key = { it.name }) { city ->
                        CityCard(
                            plate = plates.getValue(city.name),
                            name = city.name,
                            summary = summaries[city.name],
                            isFavorite = true,
                            onClick = { onCityClick(city.name) },
                            onToggleFavorite = { onToggleFavorite(city.name) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "☆", color = FavoriteYellow, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Henüz favori ilin yok",
                color = palette.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Listede bir ilin yanındaki ☆ yıldızına dokun, burada görünsün.",
                color = palette.textSoft,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun FavoritesScreenPreview() {
    FavoritesScreen(
        cities = provinces,
        summaries = mapOf("Ankara" to CitySummary(15.0, 2, true), "İzmir" to CitySummary(22.0, 0, true)),
        favorites = setOf("Ankara", "İzmir"),
        onCityClick = {},
        onToggleFavorite = {},
        onBack = {}
    )
}
