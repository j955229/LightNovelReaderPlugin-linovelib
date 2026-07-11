package io.nightfish.lightnovelreader.api.web.explore.filter

import io.nightfish.lightnovelreader.api.book.BookInformation

/**
 * 本地过滤器接口
 * 实现此接口的过滤器可对本地书云中的书本进行过滤，降低网络请求
 *
 * @since Api 2
 */
interface LocalFilter {
    /**
     * 对一本书进行本地过滤
     *
     * @param bookInformation 要过滤的书本信息
     *
     * @return 如果书本满足过滤条件则返回true
     *
     * @since Api 2
     */
    fun filter(bookInformation: BookInformation): Boolean
}