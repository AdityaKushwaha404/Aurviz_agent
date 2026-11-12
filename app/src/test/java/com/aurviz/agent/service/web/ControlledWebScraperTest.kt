package com.aurviz.agent.service.web

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledWebScraperTest {
    private val scraper = ControlledWebScraper()

    @Test
    fun blocksLoopbackAddressesBeforeNetworkRequest() = runBlocking {
        val result = scraper.browseAndExtract("http://127.0.0.1/admin")
        assertTrue(result.startsWith("Error: Requests to private or local"))
    }

    @Test
    fun blocksPrivateIpv4AddressesBeforeNetworkRequest() = runBlocking {
        val result = scraper.browseAndExtract("http://192.168.1.1/")
        assertTrue(result.startsWith("Error: Requests to private or local"))
    }

    @Test
    fun blocksCarrierGradeNatAddresses() = runBlocking {
        val result = scraper.browseAndExtract("https://100.64.0.1/")
        assertTrue(result.startsWith("Error: Requests to private or local"))
    }
}
