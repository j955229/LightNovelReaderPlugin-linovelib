package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.net.Uri
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.lightnovelreader.api.book.WordCount
import io.nightfish.lightnovelreader.api.util.local
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import io.nightfish.lightnovelreader.api.web.search.SearchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class LinovelibSearchProvider(
    private val htmlLoader: suspend (String) -> String,
    private val searchHtmlLoader: suspend (String) -> LinovelibSearchResponse,
    private val parser: LinovelibHtmlParser,
    private val diagnostics: LinovelibDiagnostics
) : SearchProvider {
    private val searchDetails = LinovelibSearchDetails(htmlLoader, parser, diagnostics)

    override val searchTypes: List<SearchType> = listOf(
        SearchType(
            type = "book",
            name = "Book".local(),
            tip = "Enter a book name, book ID, or book page URL".local()
        )
    )

    override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
        val query = keyword.trim()
        val bookId = parser.bookIdFromKeyword(keyword)
        diagnostics.info(
            "SEARCH_START",
            linkedMapOf("type" to searchType.type, "keyword" to query, "directBookId" to bookId)
        )
        if (bookId != null) {
            runCatching {
                parser.parseBookInformation(bookId, htmlLoader(LinovelibUrls.book(bookId))).toBookInformation()
            }.onFailure {
                it.rethrowIfCancellation()
                diagnostics.error("SEARCH_DIRECT_ERROR", it, mapOf("bookId" to bookId))
            }.getOrNull()
                ?.takeUnless { it.isEmpty() }
                ?.let { emit(SearchResult.MultipleBook(it)) }
                ?: emit(SearchResult.SingleBook(bookId))
            diagnostics.info("SEARCH_DONE", mapOf("mode" to "direct", "results" to 1, "bookId" to bookId))
            emit(SearchResult.End())
            return@flow
        }

        if (query.isEmpty()) {
            emit(SearchResult.Empty())
            emit(SearchResult.End())
            return@flow
        }

        val searchResponse = runCatching {
            searchHtmlLoader(query)
        }.onFailure {
            it.rethrowIfCancellation()
            diagnostics.error("SEARCH_REQUEST_ERROR", it, mapOf("keyword" to query))
        }.getOrNull()

        if (searchResponse == null) {
            emit(SearchResult.Error("Failed to request Linovelib search data"))
            emit(SearchResult.End())
            return@flow
        }

        val redirectedBookId = searchResponse.directBookId(parser)
        if (redirectedBookId != null) {
            val book = runCatching {
                parser.parseBookInformation(redirectedBookId, searchResponse.html).toBookInformation()
            }.onFailure {
                it.rethrowIfCancellation()
                diagnostics.error("SEARCH_REDIRECT_ERROR", it, mapOf("bookId" to redirectedBookId))
            }.getOrNull()
            if (book != null && !book.isEmpty()) {
                emit(SearchResult.MultipleBook(book))
            } else {
                emit(SearchResult.SingleBook(redirectedBookId))
            }
            diagnostics.info(
                "SEARCH_DONE",
                mapOf("mode" to "redirect", "results" to 1, "bookId" to redirectedBookId)
            )
            emit(SearchResult.End())
            return@flow
        }

        val searchRow = parser.parseListRow("Search", searchResponse.html)

        val books = searchRow.books.distinctBy { it.id }.take(MAX_SEARCH_RESULTS)
        diagnostics.info(
            "SEARCH_SOURCE",
            linkedMapOf(
                "source" to "guarded-search",
                "keyword" to query,
                "books" to books.size
            )
        )
        val detailedBooks = searchDetails.load(books)
        detailedBooks.forEach { emit(SearchResult.MultipleBook(it.toBookInformation())) }
        if (detailedBooks.isEmpty()) emit(SearchResult.Empty())
        diagnostics.info(
            "SEARCH_DONE",
            linkedMapOf("mode" to "text", "keyword" to query, "results" to detailedBooks.size)
        )
        emit(SearchResult.End())
    }

    private fun ParsedBookInformation.toBookInformation() =
        MutableBookInformation(
            id = id,
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl.takeIf(String::isNotEmpty)?.let(Uri::parse) ?: Uri.EMPTY,
            author = author,
            description = description,
            tags = LinovelibRelatedSearch.displayTags(author, tags, publishingHouse),
            publishingHouse = "",
            wordCount = WordCount(wordCount),
            lastUpdated = lastUpdated.atStartOfDay(),
            isComplete = isComplete
        )

    private companion object {
        const val MAX_SEARCH_RESULTS = 20
    }
}
