package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class LinovelibSearchDetailsTest {
    @Test
    fun `loads complete information for lightweight search rows`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val details = LinovelibSearchDetails(
            htmlLoader = { url ->
                requestedUrls += url
                bookHtml(id = url.substringAfterLast('/').substringBefore('.'), date = "2026-06-30")
            },
            parser = LinovelibHtmlParser(),
            diagnostics = silentDiagnostics()
        )

        val result = details.load(
            listOf(ParsedExploreBook("101", "Search title", "Search author", ""))
        )

        assertEquals(listOf(LinovelibUrls.book("101")), requestedUrls)
        assertEquals(1, result.size)
        assertEquals("101", result.single().id)
        assertEquals(LocalDate.of(2026, 6, 30), result.single().lastUpdated)
        assertEquals(123_000, result.single().wordCount)
        assertTrue(result.single().isComplete)
    }

    @Test
    fun `limits concurrent detail requests to four`() = runBlocking {
        val active = AtomicInteger(0)
        val maximumActive = AtomicInteger(0)
        val details = LinovelibSearchDetails(
            htmlLoader = { url ->
                val nowActive = active.incrementAndGet()
                maximumActive.updateAndGet { current -> maxOf(current, nowActive) }
                delay(20)
                active.decrementAndGet()
                bookHtml(url.substringAfterLast('/').substringBefore('.'), "2026-06-30")
            },
            parser = LinovelibHtmlParser(),
            diagnostics = silentDiagnostics()
        )
        val books = (1..9).map { id -> ParsedExploreBook(id.toString(), "Book $id", "Author", "") }

        val result = details.load(books)

        assertEquals(9, result.size)
        assertTrue("Expected concurrent requests", maximumActive.get() > 1)
        assertTrue("Maximum was ${maximumActive.get()}", maximumActive.get() <= 4)
    }

    @Test
    fun `omits detail pages without an update date`() = runBlocking {
        val details = LinovelibSearchDetails(
            htmlLoader = { bookHtml(id = "404", date = "") },
            parser = LinovelibHtmlParser(),
            diagnostics = silentDiagnostics()
        )

        val result = details.load(
            listOf(ParsedExploreBook("404", "No date", "Author", ""))
        )

        assertTrue(result.isEmpty())
    }

    private fun bookHtml(id: String, date: String) = """
        <html><body>
          <h1 class="book-title">Book $id</h1>
          <span class="authorname"><a>Author $id</a></span>
          <p class="book-meta book-layout-inline">12.3 ${"\u842c\u5b57"} | ${"\u5b8c\u7d50"}</p>
          <a class="book-meta book-status"><div>last update $date</div></a>
        </body></html>
    """.trimIndent()

    private fun silentDiagnostics() = LinovelibDiagnostics { _, _, _, _ -> }
}
