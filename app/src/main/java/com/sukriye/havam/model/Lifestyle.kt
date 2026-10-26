package com.sukriye.havam.model

// Kullanıcının seçebildiği ilgi alanları. Her biri bir yaşam tarzı indeksi üretir.
enum class UserInterest(val title: String, val emoji: String) {
    Running("Koşu & Outdoor", "🏃"),
    Photography("Fotoğraf", "📸"),
    Laundry("Çamaşır", "👕"),
    Gardening("Bahçe", "🌱"),
    Driving("Sürüş", "🚗");

    companion object {
        val defaults: Set<UserInterest> = setOf(Running, Photography, Laundry, Driving)
    }
}

// Skor 0–100: yüksek skor her zaman "senin için iyi" demek (sürüşte düşük risk gibi)
enum class RiskLevel(val label: String) {
    Excellent("Mükemmel"),
    Good("İyi"),
    Moderate("Orta"),
    Poor("Zayıf"),
    Hazardous("Riskli");

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 80 -> Excellent
            score >= 60 -> Good
            score >= 40 -> Moderate
            score >= 20 -> Poor
            else -> Hazardous
        }
    }
}

data class TimeWindow(val startEpochSeconds: Long, val endEpochSeconds: Long)

// Skoru neyin düşürdüğü/yükselttiği. Ekranda "neden?" çipleri olarak gösterilir (açıklanabilir AI).
data class IndexFactor(val label: String, val impact: Int)

data class LifestyleIndex(
    val interest: UserInterest,
    val score: Int,
    val level: RiskLevel,
    val headline: String,
    val advice: String,
    val bestWindow: TimeWindow?,
    val factors: List<IndexFactor>
)

enum class PressureTendency(val label: String, val arrow: String) {
    Rising("Yükseliyor", "↗"),
    Steady("Sabit", "→"),
    Falling("Düşüyor", "↘"),
    FallingFast("Hızla düşüyor", "⇊")
}

data class PressureTrend(
    val currentHpa: Double,
    val change3hHpa: Double,
    val change24hHpa: Double,
    val forecastChange6hHpa: Double,
    val tendency: PressureTendency
)

data class LifestyleReport(
    val indices: List<LifestyleIndex>,
    val pressureTrend: PressureTrend,
    // Ekranın en üstündeki tek cümlelik "asistan" mesajı
    val insight: String
)

data class Advice(val emoji: String, val text: String)

enum class NoiseColor(val title: String) {
    White("Beyaz gürültü"),
    Pink("Pembe gürültü"),
    Brown("Kahverengi gürültü")
}

enum class DayPhase(val title: String) {
    Dawn("Şafak"),
    Morning("Sabah"),
    Noon("Öğle"),
    Afternoon("İkindi"),
    Dusk("Alacakaranlık"),
    Night("Gece")
}

// Günün atmosferine göre seçilen ses manzarası
data class Soundscape(
    val phase: DayPhase,
    val noiseColor: NoiseColor,
    val title: String,
    val description: String,
    // Basınç ve ışığa göre önerilen müzik temposu
    val suggestedBpm: Int,
    val spotifySearchQuery: String
)
