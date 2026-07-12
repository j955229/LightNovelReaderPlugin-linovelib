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
import org.junit.Test

class LinovelibRelatedSearchTest {
    @Test
    fun `adds clickable author chip without duplicating tags`() {
        assertEquals(
            listOf("\u4f5c\u8005\uff1a\u5ddd\u539f\u792b", "\u5947\u5e7b", "\u6821\u5712"),
            LinovelibRelatedSearch.displayTags(
                "\u5ddd\u539f\u792b",
                listOf("\u5947\u5e7b", "\u6821\u5712", "\u5947\u5e7b")
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
}
