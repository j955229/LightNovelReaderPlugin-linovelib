package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Test

class LinovelibChapterIdsTest {
    @Test
    fun appIdVersionsNumericWebsiteChapterId() {
        assertEquals("linovelib-v3:61960", LinovelibChapterIds.forApp("61960"))
        assertEquals("linovelib-v3:61960", LinovelibChapterIds.forApp("linovelib-v3:61960"))
        assertEquals("linovelib-v3:61960", LinovelibChapterIds.forApp("linovelib-v2:61960"))
    }

    @Test
    fun websiteIdRemovesInternalVersionPrefix() {
        assertEquals("61960", LinovelibChapterIds.forWebsite("linovelib-v3:61960"))
        assertEquals("61960", LinovelibChapterIds.forWebsite("linovelib-v2:61960"))
        assertEquals("61960", LinovelibChapterIds.forWebsite("61960"))
    }
}
