package io.nightfish.lightnovelreader.api.web.explore

import io.nightfish.lightnovelreader.api.web.explore.filter.Filter
import io.nightfish.lightnovelreader.api.web.search.SearchResult
import kotlinx.coroutines.flow.Flow

/**
 * 探索展开页的数据源接口
 * 展开页支持过滤器、切换不同内容以及分页加载
 *
 * @since Api 2
 */
interface ExploreExpandedPageDataSource {
    /**
     * 展开页的显示标题
     *
     * @since Api 2
     */
    val title: String

    /**
     * 展开页支持的过滤器列表
     *
     * @since Api 2
     */
    val filters: List<Filter<*>>

    /**
     * 加载更多搜索结果
     * 应谓发对[getResultFlow]的数据流进行更新
     *
     * @since Api 2
     */
    fun loadMore()

    /**
     * 探索展开页的数据流，提供[SearchResult]
     * 应谓为热数据流，永远保持活跃
     *
     * @return 探索展开页的数据流
     *
     * @since Api 2
     */
    fun getResultFlow(): Flow<SearchResult>
}