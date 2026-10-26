package com.sukriye.havam.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvinceSearchTest {

    private fun names(query: String) = searchProvinces(provinces, query).map { it.name }

    @Test
    fun `empty query returns all provinces`() {
        assertEquals(81, searchProvinces(provinces, "  ").size)
    }

    @Test
    fun `search ignores turkish characters and case`() {
        assertEquals(listOf("Ağrı"), names("agri"))
        assertEquals(listOf("İzmir"), names("IZMIR"))
        assertEquals(listOf("Şanlıurfa"), names("sanliurfa"))
    }

    @Test
    fun `partial name matches every province containing it`() {
        val result = names("an")
        assertTrue("Ankara" in result)
        assertTrue("Adana" in result)
        assertTrue("Ağrı" !in result)
    }

    @Test
    fun `number finds the province by plate`() {
        assertEquals(listOf("Ankara"), names("06"))
        assertEquals(listOf("İstanbul"), names("34"))
        assertTrue(names("99").isEmpty())
    }
}
