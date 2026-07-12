package io.nightfish.lightnovelreader.plugin.linovelib.source

internal class LinovelibSearchDetails(
    private val htmlLoader: suspend (String) -> String,
    private val parser: LinovelibHtmlParser,
    private val diagnostics: LinovelibDiagnostics
) {
    suspend fun load(books: List<ParsedExploreBook>): List<ParsedBookInformation> =
        books.mapNotNull { book ->
            runCatching {
                parser.parseBookInformation(book.id, htmlLoader(LinovelibUrls.book(book.id)))
                    .takeUnless { LinovelibDates.isUnknown(it.lastUpdated) }
            }.onFailure {
                it.rethrowIfCancellation()
                diagnostics.error("SEARCH_DETAIL_ERROR", it, mapOf("bookId" to book.id))
            }.getOrNull()
        }
}
