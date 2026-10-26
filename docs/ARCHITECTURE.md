# Mimari

Tek `:app` modülü, üç katman: `model/` · `data/` · `ui/`.

```
UI (Composable)  →  ViewModel (StateFlow)  →  Repository  →  Room  ⇄  Retrofit → Open-Meteo
```

Kurallar:

- **UI internete gitmez.** Composable yalnızca aldığı state'i çizer ve olayları (intent) yukarı bildirir.
- **ViewModel çizim yapmaz.** Renk veya Composable bilmez; yalnızca state ve tek seferlik olay üretir.
- **Repository ekranı bilmez.** Veriyi getirir ve saklar; nasıl gösterileceği onu ilgilendirmez.
- **`model/` saf Kotlin'dir.** Android, Room veya Retrofit sınıfı içermez; JVM'de test edilir.

## Katmanlar

| Katman | İçerik | Bağımlı olduğu |
|---|---|---|
| `model/` | Veri sınıfları, WMO kodları, 81 il listesi, biçimlendirme | hiçbir şey |
| `data/` | Retrofit arayüzleri, DTO'lar, Room, repository'ler, use case'ler, sensörler, worker, Hilt modülleri | `model/` |
| `ui/` | Ekranlar, ViewModel'ler, ortak bileşenler, tema, widget | `model/`, `data/` |

Use case'ler (`*UseCase.kt`) ayrı bir katman değil, `data/` içindeki iş kurallarıdır: yaşam
tarzı motoru, trend tahmini, ses manzarası, kıyafet önerisi ve fırtına tespiti. Repository'ye
bağımlı olmayanlar (indeks motoru, tahmin) saf fonksiyondur ve doğrudan birim testiyle denenir.

Repository'lerin hepsi **arayüz + uygulama** olarak aynı dosyada durur (`WeatherRepository` +
`WeatherRepositoryImpl`). Arayüzün sebebi testlerdir: `testing/FakeRepositories.kt` içindeki
sahteler ViewModel'i internete ve veritabanına çıkmadan çalıştırır. Eşleştirme `ApiClient.kt`
içindeki Hilt modülündedir.

## Veri akışı (offline-first)

```
Retrofit → Repository.refresh() → Room (delta: yalnızca değişen saatler yazılır)
Room Flow → ObserveWeatherUseCase (tazelik + gerekirse trend tahmini)
         → ForecastViewModel (combine: hava + ilgi alanları + favoriler + ses)
         → ForecastRoute (collectAsStateWithLifecycle) → ForecastScreen (stateless)
```

1. Panel açılır → `ForecastViewModel` navigasyon argümanından ili (ya da cihaz konumunu) okur.
2. `refresh()` ağdan veriyi çeker, Room'a yazar. Ekran ağı değil **Room'u** dinler.
3. Room değişince akış yeni `WeatherSnapshot` üretir; indeksler, ses ve grafik noktaları
   arka planda (`@DefaultDispatcher`) hesaplanır.
4. Ağ yoksa son kayıt gösterilir; önbellekteki saatler bittiyse kalanlar trendden tahmin edilir
   ve grafikte kesikli çizilir.
5. State `WhileSubscribed(5_000)` ile paylaşılır: uygulama arka plandayken sensör ve dakika
   sayacı durur.

## MVI

`ui/forecast/ForecastContract.kt`:

| Parça | Görev |
|---|---|
| `WeatherUiState` | Ekranın tamamı; `content: LoadState<WeatherContent>` |
| `WeatherIntent` | Kullanıcı eylemleri: yenile, favori, ilgi alanı, saat seçimi, ses, Spotify |
| `WeatherSideEffect` | Tek seferlik olaylar: mesaj, izin isteği, link açma (`Channel`) |

## Hata yönetimi

Ağ hataları `data/ApiClient.kt` içindeki `mapErrors` ile `WeatherError`'a çevrilir; ViewModel
bunu `UiError`'a ve insan diline çevirir. Kayıtlı veri ekrandaysa hata tam ekran yerine kısa bir
mesaj olarak gösterilir.

| Ne oldu | Kotlin'de | Kullanıcıya |
|---|---|---|
| İnternet yok, zaman aşımı | `IOException` → `NoConnection` | "İnternet bağlantısı yok…" |
| Sunucu 4xx/5xx | `HttpException` → `Server(code)` | "Sunucu şu an cevap vermiyor" |
| Beklenmeyen JSON | `SerializationException` → `InvalidData` | "Bir şeyler ters gitti. Tekrar dene." |
| Konum yok | `LocationUnavailable` | "Konum bulunamadı…" |

## Dört durum

| Durum | Liste ekranı | Panel |
|---|---|---|
| `Loading` | İl adları + "yükleniyor" durumu | Atmosfer + `ShimmerList` |
| `Success` | `LazyColumn` + `CityCard` | Kartlar, grafik, indeksler |
| `Empty` | — | `EmptyView` (tahmin yok / ilgi alanı seçilmedi) |
| `Error` | Durum satırında mesaj, aşağı çekip yenile | `ErrorView` + "Tekrar dene" |

## Route / Screen ayrımı

- **`XRoute`** — ViewModel'i `hiltViewModel()` ile edinir, state'i toplar, yan etkileri işler.
- **`XScreen`** — ViewModel tanımaz; aldığı değerleri çizer, olayları yukarı bildirir.

## Navigasyon

Üç rota: `city` (başlangıç), `map` ve `forecast?province=..&device=..`. Harita, liste ekranının
`CityViewModel`'ini paylaşır. Panelin her açılışı kendi ViewModel'ini alır; geri dönülünce
ViewModel silinir ve çalan ses durur.

## Testler

```
app/src/test/
├── data/       Open-Meteo eşlemesi (MockWebServer + gerçek JSON), yaşam motoru, trend tahmini
├── model/      WMO kodları, hava kalitesi aralıkları, en yakın il
├── testing/    Sahte repository'ler, MainDispatcherRule, test verisi
└── ui/         CityViewModel ve ForecastViewModel davranışları
```
