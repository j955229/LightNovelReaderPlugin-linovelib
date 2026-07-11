package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Test

class LinovelibContentFormatterTest {
    @Test
    fun `joins consecutive text as indented paragraphs`() {
        val blocks = listOf(
            ParsedContentBlock.Text("First paragraph."),
            ParsedContentBlock.Text("Second paragraph.\nIntentional line break.")
        )

        val result = LinovelibContentFormatter.format(blocks)

        assertEquals(
            listOf(
                ParsedContentBlock.Text(
                    "\u3000\u3000First paragraph.\n\n" +
                        "\u3000\u3000Second paragraph.\nIntentional line break."
                )
            ),
            result
        )
    }

    @Test
    fun `keeps images between formatted text groups`() {
        val image = ParsedContentBlock.Image("https://example.com/illustration.jpg")
        val blocks = listOf(
            ParsedContentBlock.Text("Before image."),
            image,
            ParsedContentBlock.Text("After image one."),
            ParsedContentBlock.Text("After image two.")
        )

        val result = LinovelibContentFormatter.format(blocks)

        assertEquals(
            listOf(
                ParsedContentBlock.Text("\u3000\u3000Before image."),
                image,
                ParsedContentBlock.Text(
                    "\u3000\u3000After image one.\n\n\u3000\u3000After image two."
                )
            ),
            result
        )
    }
}
