package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class LinovelibImageProxyTest {
    @Test
    fun imageProxyDoesNotLinkDesugaredBase64ApisMissingFromHostApp() {
        val classBytes = checkNotNull(
            LinovelibImageProxy::class.java.getResourceAsStream("LinovelibImageProxy.class")
        ).use { it.readBytes() }
        val bytecode = String(classBytes, Charsets.ISO_8859_1)

        assertFalse(bytecode.contains("java/util/Base64"))
        assertFalse(bytecode.contains("withoutPadding"))
    }

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

    @Test
    fun routesAllowedImagesThroughInstalledProviderWithoutDiscovery() {
        val original = "https://img3.readpai.com/2/2013/235372/256528.jpg"

        assertTrue(LinovelibImageProxy.route(original).startsWith("content://"))
        assertEquals(original, LinovelibImageProxy.decodeUriString(LinovelibImageProxy.route(original)))
        assertEquals("https://example.com/image.jpg", LinovelibImageProxy.route("https://example.com/image.jpg"))
    }

    @Test
    fun decodesLegacyBase64ProxyUrisFromExistingCaches() {
        val legacy = "content://${LinovelibImageProxy.authority}/image/" +
            "aHR0cHM6Ly9pbWczLnJlYWRwYWkuY29tLzIvMjAxMy8yMzUzNzIvMjU2NTI4LmpwZw"

        assertEquals(
            "https://img3.readpai.com/2/2013/235372/256528.jpg",
            LinovelibImageProxy.decodeUriString(legacy)
        )
    }

    @Test
    fun rejectsNonCanonicalProxyPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            LinovelibImageProxy.decodeUriString(
                "content://${LinovelibImageProxy.authority}/other/image/h/00"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinovelibImageProxy.decodeUriString(
                "content://${LinovelibImageProxy.authority}/image/h/00/11"
            )
        }
    }

    @Test
    fun rejectsMalformedLegacyBase64ProxyUris() {
        assertThrows(IllegalArgumentException::class.java) {
            LinovelibImageProxy.decodeUriString(
                "content://${LinovelibImageProxy.authority}/image/a"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinovelibImageProxy.decodeUriString(
                "content://${LinovelibImageProxy.authority}/image/YQ=A"
            )
        }
    }

    @Test
    fun rejectsImageUrlsTooLongForAContentUri() {
        val original = "https://img3.readpai.com/" + "a".repeat(2_100)

        assertThrows(IllegalArgumentException::class.java) {
            LinovelibImageProxy.uriString(original)
        }
    }
}
