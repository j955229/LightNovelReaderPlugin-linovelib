package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinovelibImageProxyTest {
    @Test
    fun proxyUriRoundTripsOriginalImageUrl() {
        val original = "https://img3.readpai.com/3/3768/244517/265773.jpg"
        val proxyUri = LinovelibImageProxy.uriString(original)

        assertTrue(proxyUri.startsWith("content://${LinovelibImageProxy.authority}/image/"))
        assertEquals(original, LinovelibImageProxy.decodeUriString(proxyUri))
    }

    @Test
    fun onlyKnownImageHostsAreAllowed() {
        assertTrue(LinovelibImageProxy.isAllowed("https://img3.readpai.com/3/3768/image.jpg"))
        assertTrue(LinovelibImageProxy.isAllowed("https://tw.linovelib.com/files/image.jpg"))
        assertEquals(false, LinovelibImageProxy.isAllowed("https://example.com/private"))
        assertEquals(false, LinovelibImageProxy.isAllowed("http://img3.readpai.com/image.jpg"))
    }
}
