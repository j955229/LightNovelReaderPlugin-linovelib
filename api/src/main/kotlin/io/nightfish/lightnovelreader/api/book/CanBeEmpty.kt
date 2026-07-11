package io.nightfish.lightnovelreader.api.book

/**
 * 可判断是否为空的对象接口
 *
 * @since Api 2
 */
interface CanBeEmpty {
    /**
     * 判断对象是否为空
     *
     * @return 对象是否为空
     *
     * @since Api 2
     */
    fun isEmpty(): Boolean

    /**
     * 判断对象是否不为空
     *
     * @return 对象是否不为空
     *
     * @since Api 2
     */
    fun isNotEmpty(): Boolean = !isEmpty()
}

/**
 * 判断可空的[CanBeEmpty]对象是否为null或空
 *
 * @return 对象是否为null或空
 *
 * @since Api 2
 */
fun CanBeEmpty?.isNullOrEmpty() = this == null || this.isEmpty()