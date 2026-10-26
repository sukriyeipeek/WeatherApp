package com.sukriye.havam.data

import com.sukriye.havam.testing.createApi
import com.sukriye.havam.testing.readResource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Birden çok koordinat sorulunca servis JSON dizisi döndürür; sıra istekteki sırayla aynıdır
class CityApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CityApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        api = server.createApi()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `summaries for many cities come back in request order`() = runTest {
        server.enqueue(MockResponse().setBody(readResource("city_summaries.json")))

        val result = api.summaries(latitudes = "39.9199,41.0082", longitudes = "32.8543,28.9784")

        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("latitude=39.9199%2C41.0082") || path.contains("latitude=39.9199,41.0082"))
        assertEquals(2, result.size)
        assertEquals(8.6, result[0].current.temperature, 0.001)
        assertEquals(15.8, result[1].current.temperature, 0.001)
    }
}
