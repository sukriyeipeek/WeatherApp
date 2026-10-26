package com.sukriye.havam.model

// API hava durumunu yazı olarak değil, WMO sayı kodu olarak veriyor
fun weatherDescription(code: Int): String = when (code) {
    0 -> "Açık"
    1 -> "Çoğunlukla açık"
    2 -> "Parçalı bulutlu"
    3 -> "Kapalı"
    45, 48 -> "Sisli"
    51, 53, 55 -> "Çisenti"
    56, 57 -> "Dondurucu çisenti"
    61, 63, 65 -> "Yağmurlu"
    66, 67 -> "Dondurucu yağmur"
    71, 73, 75, 77 -> "Karlı"
    80, 81, 82 -> "Sağanak yağış"
    85, 86 -> "Kar sağanağı"
    95, 96, 99 -> "Gök gürültülü fırtına"
    else -> "Bilinmiyor"
}

// Aynı kodun ikonu; açık havada gece ise güneş yerine ay gösterilir
fun weatherEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1 -> if (isDay) "🌤️" else "🌙"
    2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

// Kural motorunun kullandığı kod grupları
object WeatherCode {
    fun isThunder(code: Int) = code in 95..99
    fun isSnow(code: Int) = code in 71..77 || code == 85 || code == 86
    fun isFreezing(code: Int) = code == 56 || code == 57 || code == 66 || code == 67
    fun isFog(code: Int) = code == 45 || code == 48
    fun isRain(code: Int) = code in 51..67 || code in 80..82
    fun isHeavyRain(code: Int) = code == 65 || code == 67 || code == 82
}

// Avrupa Hava Kalitesi İndeksi (EAQI) aralıkları
fun airQualityLabel(aqi: Int): String = when {
    aqi <= 20 -> "Çok iyi"
    aqi <= 40 -> "İyi"
    aqi <= 60 -> "Orta"
    aqi <= 80 -> "Kötü"
    aqi <= 100 -> "Çok kötü"
    else -> "Son derece kötü"
}
