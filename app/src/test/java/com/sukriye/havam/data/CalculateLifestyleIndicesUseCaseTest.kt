package com.sukriye.havam.data

import com.sukriye.havam.data.CalculateLifestyleIndicesUseCase
import com.sukriye.havam.model.PressureTendency
import com.sukriye.havam.model.RiskLevel
import com.sukriye.havam.model.UserInterest
import com.sukriye.havam.testing.WeatherTestData
import com.sukriye.havam.testing.WeatherTestData.NOW
import com.sukriye.havam.testing.WeatherTestData.hour
import com.sukriye.havam.testing.WeatherTestData.snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateLifestyleIndicesUseCaseTest {
    private val useCase = CalculateLifestyleIndicesUseCase()

    private fun index(snapshot: com.sukriye.havam.model.WeatherSnapshot, interest: UserInterest) =
        useCase(snapshot, setOf(interest), NOW).indices.single()

    @Test
    fun `mild calm afternoon is excellent for running`() {
        val result = index(snapshot(), UserInterest.Running)
        assertTrue("score ${result.score}", result.score >= 80)
        assertEquals(RiskLevel.Excellent, result.level)
    }

    @Test
    fun `thunderstorm caps running score`() {
        val hours = (-24..47).map { if (it in 0..2) hour(it, code = 95, rainChance = 90, precipitation = 6.0) else hour(it) }
        val result = index(snapshot(hours), UserInterest.Running)
        assertTrue("score ${result.score}", result.score <= 5)
        assertEquals(RiskLevel.Hazardous, result.level)
    }

    @Test
    fun `fast barometric drop is detected as falling fast`() {
        // Son 3 saatte 1015'ten 1010'a düşüş, önümüzdeki saatlerde devam ediyor
        val hours = (-24..47).map { offset ->
            val pressure = when {
                offset <= -3 -> 1015.0
                offset <= 6 -> 1010.0 - offset.coerceAtLeast(0) * 0.6
                else -> 1006.0
            }
            hour(offset, pressure = pressure)
        }
        val report = useCase(snapshot(hours), setOf(UserInterest.Running), NOW)

        assertEquals(PressureTendency.FallingFast, report.pressureTrend.tendency)
        assertTrue(report.insight.contains("Basınç"))
    }

    @Test
    fun `photography finds the evening golden hour`() {
        val result = index(snapshot(), UserInterest.Photography)
        assertNotNull(result.bestWindow)
        val window = result.bestWindow!!
        // Gün batımı 17:20 -> altın saat 16:35–17:20
        assertEquals(WeatherTestData.MIDNIGHT + 16 * 3600 + 35 * 60, window.startEpochSeconds)
        assertTrue(result.headline.contains("16:35"))
    }

    @Test
    fun `rain forecast ruins laundry drying`() {
        val rainy = (-24..47).map { hour(it, rainChance = 80, precipitation = 1.2, humidity = 92, code = 63) }
        val dry = index(snapshot(), UserInterest.Laundry)
        val wet = index(snapshot(rainy), UserInterest.Laundry)
        assertTrue("dry ${dry.score} wet ${wet.score}", dry.score > wet.score + 30)
        assertTrue(wet.score <= 15)
    }

    @Test
    fun `fog and freezing conditions are hazardous for driving`() {
        val icy = (-24..47).map { hour(it, temperature = -1.0, humidity = 95, visibility = 150.0, code = 48) }
        val result = index(snapshot(icy), UserInterest.Driving)
        assertEquals(RiskLevel.Hazardous, result.level)
        assertTrue(result.factors.isNotEmpty())
    }

    @Test
    fun `frost forecast lowers gardening score`() {
        val frosty = (-24..47).map { if (it in 8..14) hour(it, temperature = -2.0) else hour(it) }
        val normal = index(snapshot(), UserInterest.Gardening)
        val frost = index(snapshot(frosty), UserInterest.Gardening)
        assertTrue(normal.score - frost.score >= 30)
    }

    @Test
    fun `only selected interests are calculated`() {
        val report = useCase(snapshot(), setOf(UserInterest.Driving, UserInterest.Running), NOW)
        assertEquals(listOf(UserInterest.Running, UserInterest.Driving), report.indices.map { it.interest })
    }
}
