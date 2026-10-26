package com.sukriye.havam.model

// Ayarlardan seçilen görünüm. Hava paneli her temada gökyüzü arka planıyla çizilir;
// tema liste, harita, favoriler ve ayarlar ekranlarını etkiler.
enum class ThemeMode(val title: String, val description: String, val emoji: String) {
    Light("Açık tema", "Aydınlık zemin, koyu yazılar", "☀️"),
    Dark("Koyu tema", "Gece mavisi zemin, açık yazılar", "🌙"),
    System("Telefonla aynı", "Telefonun açık/koyu ayarını izler", "📱");

    companion object {
        val default: ThemeMode = Dark
    }
}
