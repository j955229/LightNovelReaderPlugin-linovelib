package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Test

class LinovelibParagraphOrderTest {
    @Test
    fun restoreReversesWebsiteShuffleAfterFirstTwentyParagraphs() {
        val shuffled = (0..19).toList() + listOf(22, 24, 25, 21, 29, 27, 20, 28, 23, 26)

        val restored = LinovelibParagraphOrder.restore("61960", shuffled)

        assertEquals((0..29).toList(), restored)
    }

    @Test
    fun restoreKeepsShortChaptersUnchanged() {
        val paragraphs = (0..19).toList()

        assertEquals(paragraphs, LinovelibParagraphOrder.restore("61960", paragraphs))
    }
}
