package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.net.Uri
import io.nightfish.lightnovelreader.api.explore.ExploreBooksRow
import io.nightfish.lightnovelreader.api.explore.ExploreDisplayBook
import io.nightfish.lightnovelreader.api.web.explore.AbstractDefaultExplorePageProvider
import io.nightfish.lightnovelreader.api.web.explore.ExploreTapPageDataSource
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LinovelibExplorePageProvider(
    private val htmlLoader: suspend (String) -> String,
    private val parser: LinovelibHtmlParser,
    private val searchProvider: SearchProvider
) : AbstractDefaultExplorePageProvider() {
    init {
        registerTapPage(
            LinovelibHomeExploreTapPage(
                htmlLoader = htmlLoader,
                parser = parser
            )
        )
        registerTapPage(
            LinovelibRowsExploreTapPage(
                title = "Ranking",
                htmlLoader = htmlLoader,
                parser = parser,
                rowsLoader = { loader, rowParser -> rowParser.parseRankingRows(loader(LinovelibUrls.TOP)) }
            )
        )
        registerTapPage(
            LinovelibRowsExploreTapPage(
                title = "Complete",
                htmlLoader = htmlLoader,
                parser = parser,
                rowsLoader = { loader, rowParser ->
                    listOf(rowParser.parseListRow("Complete", loader(LinovelibUrls.COMPLETE)))
                }
            )
        )
    }

    fun registerRelatedPage(tag: String): String {
        val displayTag = tag.trim().ifEmpty { "Related" }
        val baseId = "$RELATED_PAGE_ID-${Integer.toHexString(displayTag.hashCode())}"
        val targetUrl = parser.relatedTarget(displayTag)
        var pageId = baseId
        var suffix = 2
        while (true) {
            val existing = exploreExpandedPageDataSourceMap[pageId]
            if (existing == null) {
                registerExpandedPageDataSource(pageId, relatedPage(displayTag, targetUrl))
                return pageId
            }
            if (existing.title == displayTag) {
                if (targetUrl != null && existing !is LinovelibLinkedExpandedPageDataSource) {
                    registerExpandedPageDataSource(pageId, relatedPage(displayTag, targetUrl))
                }
                return pageId
            }
            pageId = "$baseId-${suffix++}"
        }
    }

    private fun relatedPage(displayTag: String, targetUrl: String?) =
        if (targetUrl == null) {
            LinovelibRelatedExpandedPageDataSource(searchProvider, displayTag)
        } else {
            LinovelibLinkedExpandedPageDataSource(
                displayTag = displayTag,
                targetUrl = targetUrl,
                htmlLoader = htmlLoader,
                parser = parser
            )
        }

    companion object {
        const val RELATED_PAGE_ID = "linovelib-related"
    }
}

private class LinovelibHomeExploreTapPage(
    private val htmlLoader: suspend (String) -> String,
    private val parser: LinovelibHtmlParser
) : ExploreTapPageDataSource {
    override val title: String = "Home"

    override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = flow {
        val rows = parser.parseExploreRows(htmlLoader(LinovelibUrls.HOST))
            .map { row -> row.toExploreBooksRow() }
        emit(rows)
    }
}

private class LinovelibRowsExploreTapPage(
    override val title: String,
    private val htmlLoader: suspend (String) -> String,
    private val parser: LinovelibHtmlParser,
    private val rowsLoader: suspend (suspend (String) -> String, LinovelibHtmlParser) -> List<ParsedExploreRow>
) : ExploreTapPageDataSource {
    override fun getRowsFlow(): Flow<List<ExploreBooksRow>> = flow {
        emit(rowsLoader(htmlLoader, parser).map { it.toExploreBooksRow() })
    }
}

private fun ParsedExploreRow.toExploreBooksRow(): ExploreBooksRow =
    ExploreBooksRow(
        title = title,
        bookList = books.map { book ->
            ExploreDisplayBook(
                id = book.id,
                title = book.title,
                author = book.author,
                coverUri = book.coverUrl.takeIf(String::isNotEmpty)?.let(Uri::parse) ?: Uri.EMPTY
            )
        }
    )
