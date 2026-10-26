package com.sukriye.havam.model

import kotlin.math.cos
import kotlin.math.sqrt

// Hava durumu API'si şehir adıyla değil, enlem/boylamla çalışıyor
data class Province(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

// Hava durumunu istediğimiz nokta: bir il merkezi ya da kullanıcının tam konumu.
// key, Room'daki satırları bu noktaya bağlar.
data class WeatherLocation(
    val key: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isDeviceLocation: Boolean = false
) {
    companion object {
        fun of(province: Province) = WeatherLocation(
            key = "province:${province.name}",
            name = province.name,
            latitude = province.latitude,
            longitude = province.longitude
        )

        fun device(name: String, latitude: Double, longitude: Double) = WeatherLocation(
            key = DEVICE_KEY,
            name = name,
            latitude = latitude,
            longitude = longitude,
            isDeviceLocation = true
        )

        const val DEVICE_KEY = "device"
    }
}

// Listede ve haritada her il için gösterilen kısa bilgi
data class CitySummary(
    val temperature: Double,
    val weatherCode: Int,
    val isDay: Boolean
)

// Plaka sırasına göre 81 il ve il merkezlerinin koordinatları
val provinces = listOf(
    Province("Adana", 36.9862, 35.3253),
    Province("Adıyaman", 37.7644, 38.2763),
    Province("Afyonkarahisar", 38.7567, 30.5433),
    Province("Ağrı", 39.7147, 43.0401),
    Province("Amasya", 40.6533, 35.8331),
    Province("Ankara", 39.9199, 32.8543),
    Province("Antalya", 36.9081, 30.6956),
    Province("Artvin", 41.1816, 41.8217),
    Province("Aydın", 37.8450, 27.8396),
    Province("Balıkesir", 39.6492, 27.8861),
    Province("Bilecik", 40.1419, 29.9793),
    Province("Bingöl", 38.8847, 40.4939),
    Province("Bitlis", 38.4012, 42.1078),
    Province("Bolu", 40.7358, 31.6061),
    Province("Burdur", 37.7203, 30.2908),
    Province("Bursa", 40.1956, 29.0601),
    Province("Çanakkale", 40.1555, 26.4127),
    Province("Çankırı", 40.5999, 33.6153),
    Province("Çorum", 40.5489, 34.9533),
    Province("Denizli", 37.7742, 29.0875),
    Province("Diyarbakır", 37.9136, 40.2172),
    Province("Edirne", 41.6772, 26.5560),
    Province("Elazığ", 38.6743, 39.2232),
    Province("Erzincan", 39.7392, 39.4901),
    Province("Erzurum", 39.9086, 41.2769),
    Province("Eskişehir", 39.7767, 30.5206),
    Province("Gaziantep", 37.0594, 37.3825),
    Province("Giresun", 40.9170, 38.3874),
    Province("Gümüşhane", 40.4600, 39.4718),
    Province("Hakkari", 37.5744, 43.7408),
    Province("Hatay", 36.2066, 36.1572),
    Province("Isparta", 37.7644, 30.5522),
    Province("Mersin", 36.8120, 34.6389),
    Province("İstanbul", 41.0138, 28.9497),
    Province("İzmir", 38.4127, 27.1384),
    Province("Kars", 40.5983, 43.0855),
    Province("Kastamonu", 41.3781, 33.7753),
    Province("Kayseri", 38.7322, 35.4853),
    Province("Kırklareli", 41.7351, 27.2252),
    Province("Kırşehir", 39.1458, 34.1639),
    Province("Kocaeli", 40.7650, 29.9293),
    Province("Konya", 37.8713, 32.4846),
    Province("Kütahya", 39.4242, 29.9833),
    Province("Malatya", 38.3502, 38.3167),
    Province("Manisa", 38.6120, 27.4265),
    Province("Kahramanmaraş", 37.5847, 36.9264),
    Province("Mardin", 37.3131, 40.7436),
    Province("Muğla", 37.2181, 28.3665),
    Province("Muş", 38.7316, 41.4848),
    Province("Nevşehir", 38.6250, 34.7122),
    Province("Niğde", 37.9658, 34.6793),
    Province("Ordu", 40.9778, 37.8905),
    Province("Rize", 41.0208, 40.5219),
    Province("Sakarya", 40.7806, 30.4033),
    Province("Samsun", 41.2798, 36.3361),
    Province("Siirt", 37.9293, 41.9413),
    Province("Sinop", 42.0268, 35.1625),
    Province("Sivas", 39.7483, 37.0161),
    Province("Tekirdağ", 40.9781, 27.5110),
    Province("Tokat", 40.3139, 36.5544),
    Province("Trabzon", 41.0050, 39.7269),
    Province("Tunceli", 39.0992, 39.5435),
    Province("Şanlıurfa", 37.1671, 38.7939),
    Province("Uşak", 38.6735, 29.4058),
    Province("Van", 38.4946, 43.3832),
    Province("Yozgat", 39.8200, 34.8044),
    Province("Zonguldak", 41.4514, 31.7931),
    Province("Aksaray", 38.3725, 34.0254),
    Province("Bayburt", 40.2563, 40.2229),
    Province("Karaman", 37.1811, 33.2150),
    Province("Kırıkkale", 39.8453, 33.5064),
    Province("Batman", 37.8874, 41.1322),
    Province("Şırnak", 37.5139, 42.4543),
    Province("Bartın", 41.6358, 32.3375),
    Province("Ardahan", 41.1087, 42.7022),
    Province("Iğdır", 39.9237, 44.0450),
    Province("Yalova", 40.6550, 29.2769),
    Province("Karabük", 41.2049, 32.6277),
    Province("Kilis", 36.7161, 37.1150),
    Province("Osmaniye", 37.0742, 36.2478),
    Province("Düzce", 40.8389, 31.1639)
)

// Konuma en yakın il merkezi. Türkiye'den çok uzaktaysa (yaklaşık 250 km'den fazla) null döner.
fun nearestProvince(latitude: Double, longitude: Double): Province? {
    // Boylam dereceleri kuzeye gittikçe kısalır, bu yüzden enleme göre düzeltiyoruz
    val lonScale = cos(Math.toRadians(latitude))
    fun distanceKm(p: Province): Double {
        val dLat = p.latitude - latitude
        val dLon = (p.longitude - longitude) * lonScale
        return sqrt(dLat * dLat + dLon * dLon) * 111.0 // 1 derece yaklaşık 111 km
    }
    val nearest = provinces.minBy { distanceKm(it) }
    return nearest.takeIf { distanceKm(it) < 250 }
}
