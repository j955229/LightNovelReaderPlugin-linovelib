package io.nightfish.lightnovelreader.plugin.linovelib.source

import io.nightfish.lightnovelreader.api.web.explore.ExploreExpandedPageDataSource
import io.nightfish.lightnovelreader.api.web.explore.filter.Filter
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import kotlinx.coroutines.flow.Flow

internal object LinovelibRelatedSearch {
    private const val AUTHOR_PREFIX = "\u4f5c\u8005\uff1a"

    fun displayTags(author: String, tags: List<String>): List<String> = buildList {
        if (author.isNotBlank()) add("$AUTHOR_PREFIX$author")
        addAll(tags.filter(String::isNotBlank))
    }.distinct()

    fun keyword(displayTag: String): String = displayTag.removePrefix(AUTHOR_PREFIX).trim()
}

internal class LinovelibRelatedExpandedPageDataSource(
    private val searchProvider: SearchProvider,
    private val displayTag: String
) : ExploreExpandedPageDataSource {
    override val title: String = displayTag
    override val filters: List<Filter<*>> = emptyList()

    override fun loadMore() = Unit

    override fun getResultFlow(): Flow<SearchResult> {
        val searchType = searchProvider.searchTypes.first()
        return searchProvider.search(searchType, LinovelibRelatedSearch.keyword(displayTag))
    }
}

internal object LinovelibRelatedNavigation {
    private val expandedRouteClassNames = listOf(
        "io.nightfish.lightnovelreader.api.Route\$Main\$Explore\$Expanded",
        "indi.dmzz_yyhyy.lightnovelreader.ui.navigation.Route\$Main\$Explore\$Expanded"
    )

    fun createExpandedRoute(classLoader: ClassLoader?, pageId: String): Any {
        val loader = classLoader ?: ClassLoader.getSystemClassLoader()
        expandedRouteClassNames.forEach { className ->
            val route = runCatching {
                Class.forName(className, true, loader)
                    .getConstructor(String::class.java)
                    .newInstance(pageId)
            }.getOrNull()
            if (route != null) return route
        }
        error("No compatible LightNovelReader expanded-page route was found")
    }
}
