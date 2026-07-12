package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinovelibDataSourceConfigurationTest {
    @Test
    fun `session cache retains book 3768 chapter count for six hours`() {
        assertEquals(256, LinovelibDataSourceConfiguration.cacheEntriesPerType)
        assertEquals(21_600_000, LinovelibDataSourceConfiguration.cacheTimeoutMillis)
    }

    @Test
    fun `offline monitor starts only when no active monitor exists`() {
        assertTrue(LinovelibDataSourceConfiguration.shouldStartOfflineMonitor(false))
        assertFalse(LinovelibDataSourceConfiguration.shouldStartOfflineMonitor(true))
    }
}
