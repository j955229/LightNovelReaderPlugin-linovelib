package io.nightfish.lightnovelreader.api.web.search

import io.nightfish.lightnovelreader.api.util.LocalString

/**
 * 搜索类型的数据类
 * 用于在搜索界面切换不同的搜索类型
 *
 * @property type 搜索类型的唯一标识
 * @property name 搜索类型的显示名称
 * @property tip 该搜索类型的输入提示文字
 *
 * @since Api 2
 */
data class SearchType(
    val type: String,
    val name: LocalString,
    val tip: LocalString
)
