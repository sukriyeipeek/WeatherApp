package com.sukriye.havam.model

import java.util.Locale

/**
 * İl araması. Türkçe karakterler aranırken fark etmez: "agri" Ağrı'yı, "izmir" İzmir'i bulur.
 * Sadece rakam yazılırsa plaka numarasıyla eşleşir: "6" ya da "06" Ankara.
 * Plaka numarası listedeki sıradan gelir, bu yüzden liste plaka sırasında olmalıdır.
 */
fun searchProvinces(cities: List<Province>, query: String): List<Province> {
    val text = query.trim()
    if (text.isEmpty()) return cities
    val plate = text.toIntOrNull()
    if (plate != null) return listOfNotNull(cities.getOrNull(plate - 1))
    val key = searchKey(text)
    return cities.filter { searchKey(it.name).contains(key) }
}

private val turkishLocale = Locale.forLanguageTag("tr")

// Küçük harfe çevirip Türkçe harfleri Latin karşılıklarına indirger
private fun searchKey(text: String): String = text.lowercase(turkishLocale)
    .replace('ç', 'c')
    .replace('ğ', 'g')
    .replace('ı', 'i')
    .replace('ö', 'o')
    .replace('ş', 's')
    .replace('ü', 'u')
