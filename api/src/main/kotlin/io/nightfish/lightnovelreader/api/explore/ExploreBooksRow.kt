package io.nightfish.lightnovelreader.api.explore

/**
 * 探索页中的一行书本展示行
 *
 * @param title 该行的标题
 * @param bookList 该行展示的书本列表
 * @param expandable 该行是否支持点击展开查看更多
 * @param expandedPageDataSourceId 展开页的数据源id, 仅当[expandable]为true时有效
 *
 * @since Api 2
 */
data class ExploreBooksRow(
    val title: String,
    val bookList: List<ExploreDisplayBook>,
    val expandable: Boolean = false,
    val expandedPageDataSourceId: String? = null
)
