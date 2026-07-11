package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinovelibDiagnosticsTest {
    @Test
    fun infoFormatsFieldsAndEscapesLineBreaks() {
        val entries = mutableListOf<DiagnosticEntry>()
        val diagnostics = LinovelibDiagnostics { level, tag, message, throwable ->
            entries += DiagnosticEntry(level, tag, message, throwable)
        }

        diagnostics.info(
            "HTTP_OK",
            linkedMapOf(
                "status" to 200,
                "url" to "https://tw.linovelib.com/a\nb",
                "empty" to null
            )
        )

        assertEquals(1, entries.size)
        assertEquals("LinovelibDiagnostic", entries.single().tag)
        assertEquals("HTTP_OK status=200 url=https://tw.linovelib.com/a\\nb empty=null", entries.single().message)
        assertEquals(null, entries.single().throwable)
    }

    @Test
    fun inspectHtmlDetectsTraditionalAndSimplifiedFailureMarkers() {
        val diagnostics = LinovelibDiagnostics { _, _, _, _ -> }

        assertTrue(diagnostics.inspectHtml("前文內容加載失敗後文").hasLoadFailure)
        assertTrue(diagnostics.inspectHtml("前文内容加载失败后文").hasLoadFailure)
        assertFalse(diagnostics.inspectHtml("完整內容").hasLoadFailure)
        assertEquals(4, diagnostics.inspectHtml("完整內容").characters)
    }

    @Test
    fun errorIncludesFieldsAndThrowable() {
        val entries = mutableListOf<DiagnosticEntry>()
        val diagnostics = LinovelibDiagnostics { level, tag, message, throwable ->
            entries += DiagnosticEntry(level, tag, message, throwable)
        }
        val failure = IllegalStateException("network failed")

        diagnostics.error("HTTP_ERROR", failure, mapOf("url" to "https://tw.linovelib.com"))

        assertEquals("HTTP_ERROR url=https://tw.linovelib.com", entries.single().message)
        assertEquals(failure, entries.single().throwable)
    }

    @Test
    fun successfulHttpStatusAcceptsOnlyTwoAndThreeHundreds() {
        val diagnostics = LinovelibDiagnostics { _, _, _, _ -> }

        assertTrue(diagnostics.isSuccessfulHttpStatus(200))
        assertTrue(diagnostics.isSuccessfulHttpStatus(302))
        assertFalse(diagnostics.isSuccessfulHttpStatus(403))
        assertFalse(diagnostics.isSuccessfulHttpStatus(500))
    }

    private data class DiagnosticEntry(
        val level: Int,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )
}
