package io.nightfish.lightnovelreader.plugin.linovelib.source

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class LinovelibSearchDetails(
    private val htmlLoader: suspend (String) -> String,
    private val parser: LinovelibHtmlParser,
    private val diagnostics: LinovelibDiagnostics
) {
    suspend fun load(books: List<ParsedExploreBook>): List<ParsedBookInformation> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        books.map { book ->
            async {
                semaphore.withPermit {
                    runCatching {
                        parser.parseBookInformation(book.id, htmlLoader(LinovelibUrls.book(book.id)))
                            .takeUnless { LinovelibDates.isUnknown(it.lastUpdated) }
                    }.onFailure {
                        it.rethrowIfCancellation()
                        diagnostics.error("SEARCH_DETAIL_ERROR", it, mapOf("bookId" to book.id))
                    }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
    }

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}
