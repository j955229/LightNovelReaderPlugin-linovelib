package io.nightfish.lightnovelreader.api.web

/**
 * 网络数据源的元数据描述
 * 用于在软件中展示数据源的基本信息
 *
 * @property id 数据源的唯一整数标识
 * @property name 数据源的显示名称
 * @property provider 数据源的提供方名称
 *
 * @since Api 2
 */
data class WebDataSourceItem(
    val id: Int,
    val name: String,
    val provider: String,
)