package com.sukriye.havam.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherConditionTest {

    @Test
    fun `wmo codes are translated to Turkish`() {
        assertEquals("Açık", weatherDescription(0))
        assertEquals("Yağmurlu", weatherDescription(63))
        assertEquals("Gök gürültülü fırtına", weatherDescription(95))
        assertEquals("Bilinmiyor", weatherDescription(42))
    }

    @Test
    fun `clear night shows the moon instead of the sun`() {
        assertEquals("☀️", weatherEmoji(0, isDay = true))
        assertEquals("🌙", weatherEmoji(0, isDay = false))
    }

    @Test
    fun `code groups used by the lifestyle engine`() {
        assertTrue(WeatherCode.isThunder(96))
        assertTrue(WeatherCode.isSnow(75))
        assertTrue(WeatherCode.isFreezing(67))
        assertTrue(WeatherCode.isFog(48))
        assertTrue(WeatherCode.isRain(81))
        assertFalse(WeatherCode.isRain(3))
    }

    @Test
    fun `air quality index bands`() {
        assertEquals("Çok iyi", airQualityLabel(15))
        assertEquals("İyi", airQualityLabel(35))
        assertEquals("Kötü", airQualityLabel(75))
        assertEquals("Son derece kötü", airQualityLabel(120))
    }

    @Test
    fun `nearest province finds the city and rejects far away points`() {
        assertEquals("Ankara", nearestProvince(39.93, 32.86)?.name)
        assertEquals(null, nearestProvince(48.85, 2.35))
    }
}
