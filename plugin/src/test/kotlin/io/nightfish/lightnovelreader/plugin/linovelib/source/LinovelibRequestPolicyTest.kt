package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinovelibRequestPolicyTest {
    @Test
    fun requestDelayKeepsMinimumInterval() {
        assertEquals(800L, LinovelibRequestPolicy.requestDelayMillis(1_000L, 1_400L))
        assertEquals(0L, LinovelibRequestPolicy.requestDelayMillis(1_000L, 2_200L))
    }

    @Test
    fun retryAfterHeaderTakesPriorityForRateLimit() {
        assertEquals(7_000L, LinovelibRequestPolicy.retryDelayMillis(429, "7", 0))
    }

    @Test
    fun rateLimitUsesProgressiveDelayWithoutHeader() {
        assertEquals(3_000L, LinovelibRequestPolicy.retryDelayMillis(429, null, 0))
        assertEquals(6_000L, LinovelibRequestPolicy.retryDelayMillis(429, null, 1))
        assertEquals(12_000L, LinovelibRequestPolicy.retryDelayMillis(429, null, 2))
    }

    @Test
    fun transientServerErrorsCanRetryButClientErrorsCannot() {
        assertEquals(1_000L, LinovelibRequestPolicy.retryDelayMillis(503, null, 0))
        assertNull(LinovelibRequestPolicy.retryDelayMillis(404, null, 0))
    }
}
