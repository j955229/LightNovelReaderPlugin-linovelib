package io.nightfish.lightnovelreader.api.book

/**
 * 可复制对象接口
 *
 * @param T 复制后的对象类型
 *
 * @since Api 2
 */
interface Copyable<T> {
    /**
     * 深度复制当前对象
     *
     * @return 复制后的对象
     *
     * @since Api 2
     */
    fun copy(): T
}