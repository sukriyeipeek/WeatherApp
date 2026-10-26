package com.sukriye.havam.ui

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sukriye.havam.ui.city.CityRoute
import com.sukriye.havam.ui.city.CityViewModel
import com.sukriye.havam.ui.city.FavoritesRoute
import com.sukriye.havam.ui.city.MapRoute
import com.sukriye.havam.ui.forecast.ForecastRoute
import com.sukriye.havam.ui.forecast.ForecastViewModel
import com.sukriye.havam.ui.settings.SettingsRoute
import com.sukriye.havam.ui.theme.HavamTheme

/**
 * Uygulamanın beş ekranı ve aralarındaki geçiş: il listesi, Türkiye haritası, favoriler,
 * ayarlar ve hava paneli.
 *
 * Her ekran bir "rota" (metin adresi) ile tanımlanır. Bir ile dokunulduğunda adresi üretip
 * [NavHostController.navigate] ile oraya gideriz; geri tuşu yığından bir önceki ekrana döner.
 * Panelin her açılışı kendi ViewModel'ini alır; geri dönülünce ViewModel silinir ve ses durur.
 */
@Composable
fun HavamNavHost(modifier: Modifier = Modifier, navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = CITY_ROUTE,
        modifier = modifier
    ) {
        composable(CITY_ROUTE) {
            CityRoute(
                onCityClick = { name -> navController.navigate(provinceRoute(name)) },
                onLocate = { navController.navigate(DEVICE_ROUTE) },
                onOpenMap = { navController.navigate(MAP_ROUTE) },
                onOpenFavorites = { navController.navigate(FAVORITES_ROUTE) },
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
            )
        }
        composable(FAVORITES_ROUTE) { entry ->
            // Liste ekranının ViewModel'i paylaşılır: favoriler ve sıcaklıklar aynı kaynaktan gelir
            val cityEntry = remember(entry) { navController.getBackStackEntry(CITY_ROUTE) }
            FavoritesRoute(
                viewModel = hiltViewModel<CityViewModel>(cityEntry),
                onCityClick = { name -> navController.navigate(provinceRoute(name)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(SETTINGS_ROUTE) {
            SettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(MAP_ROUTE) { entry ->
            // Liste ekranının ViewModel'i paylaşılır: 81 ilin verisi ikinci kez indirilmez
            val cityEntry = remember(entry) { navController.getBackStackEntry(CITY_ROUTE) }
            MapRoute(
                viewModel = hiltViewModel<CityViewModel>(cityEntry),
                onCityClick = { name -> navController.navigate(provinceRoute(name)) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = FORECAST_ROUTE,
            arguments = listOf(
                navArgument(ForecastViewModel.ARG_PROVINCE) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(ForecastViewModel.ARG_DEVICE) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) {
            // Panel gökyüzü arka planıyla çizilir: seçilen temadan bağımsız hep koyu tema ve beyaz durum çubuğu ikonları
            HavamTheme(darkTheme = true) {
                LightSystemBarIcons()
                ForecastRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}

// Açık temada da panel açıkken saat/pil ikonları beyaz olsun; panelden çıkınca önceki hâline döner
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val lightStatus = controller.isAppearanceLightStatusBars
        val lightNavigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = lightStatus
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
}

/** Favoriler ekranının adresi. */
private const val FAVORITES_ROUTE = "favorites"

/** Ayarlar ekranının adresi. */
private const val SETTINGS_ROUTE = "settings"

/** Liste ekranının adresi. */
private const val CITY_ROUTE = "city"

/** Harita ekranının adresi. */
private const val MAP_ROUTE = "map"

/** Panelin adres şablonu; süslü parantezler çalışma anında doldurulur. */
private const val FORECAST_ROUTE = "forecast?province={province}&device={device}"

/** Cihazın konumu için panel adresi. */
private const val DEVICE_ROUTE = "forecast?device=true"

/**
 * Bir il için panel adresi. `Uri.encode`: il adındaki Türkçe karakterler adreste sorun çıkarmasın
 * diye kaçışlanır ("Ağrı" → "A%C4%9Fr%C4%B1").
 */
private fun provinceRoute(name: String): String = "forecast?province=${Uri.encode(name)}"
