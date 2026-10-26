package com.sukriye.havam.data

import com.sukriye.havam.model.WeatherLocation
import com.sukriye.havam.testing.createApi
import com.sukriye.havam.testing.readResource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Gerçek bir Open-Meteo cevabı (src/test/resources/forecast_single.json) üzerinden eşleme testi
class ForecastApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ForecastApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = server.createApi()
        server.enqueue(MockResponse().setBody(readResource("forecast_single.json")))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `request asks for unix time and past hours`() = runTest {
        api.forecast(39.92, 32.85)

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.startsWith("/v1/forecast?"))
        assertTrue(path, path.contains("timeformat=unixtime"))
        assertTrue(path, path.contains("past_hours=24"))
        assertTrue(path, path.contains("forecast_hours=48"))
    }

    @Test
    fun `response is parsed into columns`() = runTest {
        val dto = api.forecast(39.92, 32.85)

        assertEquals(10_800, dto.utcOffsetSeconds)
        assertEquals(8.6, dto.current.temperature, 0.001)
        assertNotNull(dto.current.pressureMsl)
        assertEquals(72, dto.hourly.time.size)
        assertEquals(9.1, dto.hourly.temperature.first()!!, 0.001)
        assertEquals(3, dto.daily.time.size)
    }

    @Test
    fun `columns are turned into hourly rows and a snapshot`() = runTest {
        val dto = api.forecast(39.92, 32.85)
        val location = WeatherLocation("province:Ankara", "Ankara", 39.92, 32.85)

        val hourly = dto.toHourlyEntities(location.key)
        val daily = dto.toDailyEntities(location.key)
        val snapshot = dto.toLocationEntity(location, air = null, fetchedAtMillis = 1L).toSnapshot(hourly, daily)

        assertEquals(72, hourly.size)
        // Saatler sıralı ve birer saat arayla gelir
        assertTrue(hourly.zipWithNext().all { (a, b) -> b.epochSeconds - a.epochSeconds == 3600L })
        assertEquals("Ankara", snapshot.location.name)
        assertEquals(3, snapshot.daily.size)
        assertTrue(snapshot.daily.all { it.sunriseEpochSeconds < it.sunsetEpochSeconds })
    }
}
