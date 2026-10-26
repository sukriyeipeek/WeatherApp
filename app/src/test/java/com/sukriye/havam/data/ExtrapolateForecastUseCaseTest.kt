package com.sukriye.havam.data

import com.sukriye.havam.data.ExtrapolateForecastUseCase
import com.sukriye.havam.testing.WeatherTestData
import com.sukriye.havam.testing.WeatherTestData.NOW
import com.sukriye.havam.testing.WeatherTestData.hour
import com.sukriye.havam.testing.WeatherTestData.snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrapolateForecastUseCaseTest {
    private val useCase = ExtrapolateForecastUseCase()

    @Test
    fun `fresh forecast is left untouched`() {
        val snap = snapshot()
        val result = useCase(snap, NOW)
        assertEquals(0, result.extrapolatedHours)
        assertEquals(snap, result.snapshot)
    }

    @Test
    fun `missing hours are filled up to the horizon and marked`() {
        // Önbellekte sadece şu ana kadar veri var (internet 0. saatte kesildi)
        val cached = (-24..0).map { hour(it, pressure = 1015.0 - (it + 24) * 0.2) }
        val result = useCase(snapshot(cached), NOW)

        assertEquals(24, result.extrapolatedHours)
        val generated = result.snapshot.hourly.filter { it.isExtrapolated }
        assertEquals(NOW + 3600, generated.first().epochSeconds)
        assertEquals(NOW + 24 * 3600, generated.last().epochSeconds)
        // Düşen basınç trendi devam etmeli ama sönümlenerek
        assertTrue(generated.first().pressureMsl < cached.last().pressureMsl)
        val firstStep = cached.last().pressureMsl - generated[0].pressureMsl
        val lastStep = generated[22].pressureMsl - generated[23].pressureMsl
        assertTrue("trend should damp: $firstStep vs $lastStep", lastStep < firstStep)
    }

    @Test
    fun `temperature follows yesterday's diurnal curve`() {
        // Dün öğleden sonra 20°, gece 10°; bugün dünden 2° daha sıcak
        val cached = (-24..0).map { offset ->
            val base = if (offset in -24..-21 || offset in -3..0) 20.0 else 10.0
            hour(offset, temperature = if (offset >= -3) base + 2 else base)
        }
        val result = useCase(snapshot(cached), NOW)
        val in6Hours = result.snapshot.hourly.first { it.epochSeconds == NOW + 6 * 3600 }
        // Dünün aynı saati 10° + azalan 2° fark -> 10 ile 12 arası
        assertTrue("got ${in6Hours.temperature}", in6Hours.temperature in 10.0..12.0)
    }

    @Test
    fun `stale current conditions are replaced with the matching hour`() {
        val cached = (-24..0).map { hour(it) }
        val staleCurrent = WeatherTestData.currentFrom(hour(-10, temperature = 5.0))
        val later = NOW + 5 * 3600
        val result = useCase(snapshot(cached, staleCurrent), later)
        assertTrue(result.snapshot.current.epochSeconds > staleCurrent.epochSeconds)
        assertTrue(result.snapshot.current.epochSeconds <= later)
    }
}
