package com.sukriye.havam.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import com.sukriye.havam.model.WeatherError
import com.sukriye.havam.ui.widget.GlanceWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * İnternete ve veritabanına giden her şeyin kurulumu.
 *
 * Hilt bu modüllerdeki tarifleri okuyup Retrofit, OkHttp ve Room nesnelerini uygulama boyunca
 * tek örnek olarak üretir; ViewModel'ler ve repository'ler bunları kurucularında ister.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiClient {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context, network: NetworkMonitor): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .addInterceptor(OfflineCacheInterceptor(network))
        .addNetworkInterceptor(CacheHeaderInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideForecastApi(client: OkHttpClient, json: Json): ForecastApi = createApi(ForecastApi.BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideCityApi(client: OkHttpClient, json: Json): CityApi = createApi(ForecastApi.BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideAirQualityApi(client: OkHttpClient, json: Json): AirQualityApi = createApi(AirQualityApi.BASE_URL, client, json)

    // Verilen adres için bir Retrofit arayüzü üretir; testler de aynı fonksiyonu sahte sunucuyla kullanır
    inline fun <reified T> createApi(baseUrl: String, client: OkHttpClient, json: Json): T = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(T::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WeatherDatabase =
        Room.databaseBuilder(context, WeatherDatabase::class.java, "havam.db")
            // Önbellek verisi: şema değişirse silinip ağdan yeniden doldurulabilir
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideWeatherDao(database: WeatherDatabase): WeatherDao = database.weatherDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

// Arayüz -> uygulama eşleşmeleri: kim WeatherRepository isterse WeatherRepositoryImpl alır
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    abstract fun bindCityRepository(impl: CityRepositoryImpl): CityRepository

    @Binds
    abstract fun bindPreferences(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository

    @Binds
    abstract fun bindLocation(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindAmbientPlayer(impl: NoisePlayer): AmbientSoundPlayer

    @Binds
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): HomeWidgetUpdater

    @Binds
    abstract fun bindTicker(impl: MinuteTicker): Ticker
}

// Testlerde gerçek thread'ler yerine test dispatcher'ı verebilmek için
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

// Hesaplama işleri (indeks motoru, trend tahmini) için
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Singleton
class NetworkMonitor @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val connectivity get() = context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val manager = connectivity ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// Ağ yokken isteği OkHttp disk önbelleğinden karşılar (7 güne kadar eski cevap kabul)
class OfflineCacheInterceptor(private val network: NetworkMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = if (network.isOnline()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(7, TimeUnit.DAYS).build())
                .build()
        }
        return chain.proceed(request)
    }
}

// Open-Meteo önbellek başlığı göndermiyor; cevapları 5 dakika taze sayıyoruz
class CacheHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request()).newBuilder()
        .header("Cache-Control", "public, max-age=300")
        .removeHeader("Pragma")
        .build()
}

// Retrofit/IO/serileştirme hatalarını uygulamanın kendi hata türlerine çevirir
internal inline fun <T> NetworkMonitor.mapErrors(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: WeatherError) {
    throw e
} catch (e: IOException) {
    throw WeatherError.NoConnection()
} catch (e: HttpException) {
    // Çevrimdışıyken önbellekte cevap yoksa OkHttp 504 döner
    throw if (e.code() == 504 && !isOnline()) WeatherError.NoConnection() else WeatherError.Server(e.code())
} catch (e: SerializationException) {
    throw WeatherError.InvalidData()
} catch (e: IllegalArgumentException) {
    throw WeatherError.InvalidData()
}
