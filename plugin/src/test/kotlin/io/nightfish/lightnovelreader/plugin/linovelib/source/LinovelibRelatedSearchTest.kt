package io.nightfish.lightnovelreader.plugin.linovelib.source

import io.nightfish.lightnovelreader.api.util.local
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import io.nightfish.lightnovelreader.api.web.search.SearchType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinovelibRelatedSearchTest {
    @Test
    fun `adds clickable author chip without duplicating tags`() {
        assertEquals(
            listOf("\u96fb\u64ca\u6587\u5eab", "\u4f5c\u8005\uff1a\u5ddd\u539f\u792b", "\u5947\u5e7b", "\u6821\u5712"),
            LinovelibRelatedSearch.displayTags(
                "\u5ddd\u539f\u792b",
                listOf("\u5947\u5e7b", "\u6821\u5712", "\u5947\u5e7b"),
                "\u96fb\u64ca\u6587\u5eab"
            )
        )
        assertEquals("\u5ddd\u539f\u792b", LinovelibRelatedSearch.keyword("\u4f5c\u8005\uff1a\u5ddd\u539f\u792b"))
        assertEquals("\u5947\u5e7b", LinovelibRelatedSearch.keyword("\u5947\u5e7b"))
    }

    @Test
    fun `expanded related page searches current author or tag`() = runBlocking {
        val requested = mutableListOf<String>()
        val searchProvider = object : SearchProvider {
            override val searchTypes = listOf(SearchType("book", "Book".local(), "Search".local()))
            override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> {
                requested += keyword
                return flowOf(SearchResult.SingleBook("3247"), SearchResult.End())
            }
        }
        val related = LinovelibRelatedExpandedPageDataSource(
            searchProvider,
            "\u4f5c\u8005\uff1a\u5ddd\u539f\u792b"
        )
        val results = related.getResultFlow().toList()

        assertEquals("\u4f5c\u8005\uff1a\u5ddd\u539f\u792b", related.title)
        assertEquals(listOf("\u5ddd\u539f\u792b"), requested)
        assertEquals(2, results.size)
    }

    @Test
    fun `resolves expanded route class from current host api`() {
        val route = LinovelibRelatedNavigation.createExpandedRoute(javaClass.classLoader, "related-id")

        assertEquals("related-id", route.javaClass.getMethod("getExpandedPageDataSourceId").invoke(route))
    }

    @Test
    fun `linked related page loads books from website tag url`() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val page = LinovelibLinkedExpandedPageDataSource(
            displayTag = "\u79d1\u5e7b",
            targetUrl = "https://tw.linovelib.com/wenku/lastupdate_56_0_0_0_0_0_0_1_0.html",
            htmlLoader = { url ->
                requestedUrls += url
                """
                    <ol class="book-ol">
                      <li class="book-li"><a class="book-layout" href="/novel/3247.html"><span class="book-title">SAO</span></a></li>
                      <li class="book-li"><a class="book-layout" href="/novel/3768.html"><span class="book-title">Other</span></a></li>
                    </ol>
                """.trimIndent()
            },
            parser = LinovelibHtmlParser()
        )

        val results = page.getResultFlow().toList()

        assertEquals(listOf("https://tw.linovelib.com/wenku/lastupdate_56_0_0_0_0_0_0_1_0.html"), requestedUrls)
        assertEquals("\u79d1\u5e7b", page.title)
        assertEquals(listOf("3247", "3768"), results.filterIsInstance<SearchResult.SingleBook>().map { it.bookId })
        assertTrue(results.last() is SearchResult.End)
    }

    @Test
    fun `explore provider uses linked page when parser knows tag url`() {
        val parser = LinovelibHtmlParser()
        parser.parseBookInformation(
            "3247",
            """
                <h1 class="book-title">SAO</h1>
                <span class="tag-small-group">
                  <em class="tag-small red"><a href="/wenku/lastupdate_56_0_0_0_0_0_0_1_0.html">${"\u79d1\u5e7b"}</a></em>
                </span>
            """.trimIndent()
        )
        val searchProvider = object : SearchProvider {
            override val searchTypes = listOf(SearchType("book", "Book".local(), "Search".local()))
            override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flowOf(SearchResult.End())
        }
        val provider = LinovelibExplorePageProvider({ "" }, parser, searchProvider)

        val pageId = provider.registerRelatedPage("\u79d1\u5e7b")

        assertTrue(provider.exploreExpandedPageDataSourceMap[pageId] is LinovelibLinkedExpandedPageDataSource)
    }

    @Test
    fun `known tag url replaces fallback page registered before book details load`() {
        val parser = LinovelibHtmlParser()
        val searchProvider = object : SearchProvider {
            override val searchTypes = listOf(SearchType("book", "Book".local(), "Search".local()))
            override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flowOf(SearchResult.End())
        }
        val provider = LinovelibExplorePageProvider({ "" }, parser, searchProvider)
        val pageId = provider.registerRelatedPage("\u79d1\u5e7b")
        assertTrue(provider.exploreExpandedPageDataSourceMap[pageId] is LinovelibRelatedExpandedPageDataSource)

        parser.parseBookInformation(
            "3247",
            """
                <h1 class="book-title">SAO</h1>
                <span class="tag-small-group">
                  <em class="tag-small red"><a href="/wenku/lastupdate_56_0_0_0_0_0_0_1_0.html">${"\u79d1\u5e7b"}</a></em>
                </span>
            """.trimIndent()
        )
        provider.registerRelatedPage("\u79d1\u5e7b")

        assertTrue(provider.exploreExpandedPageDataSourceMap[pageId] is LinovelibLinkedExpandedPageDataSource)
    }
}
