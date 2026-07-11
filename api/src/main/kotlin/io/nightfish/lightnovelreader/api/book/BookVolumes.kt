package io.nightfish.lightnovelreader.api.book

/**
 * 书本卷目录
 *
 * @param bookId 书本id
 * @param volumes 卷列表
 *
 * @since Api 2
 */
data class BookVolumes(
    val bookId: String,
    val volumes: List<Volume>
): CanBeEmpty {
    /**
     * 书本卷目录工厂方法集合
     *
     * @since Api 2
     */
    companion object {
        /**
         * 返回一个空的书本卷目录, 并将书本id设为空
         *
         * @since Api 2
         */
        fun empty() = BookVolumes("", emptyList())

        /**
         * 返回一个空的书本卷目录, 并保有书本id
         *
         * @param bookId 书本id
         *
         * @return 空的书本卷目录
         *
         * @since Api 2
         */
        fun empty(bookId: String) = BookVolumes(bookId, emptyList())
    }

    /**
     * 判断书本卷目录是否为空
     * 如果卷列表为空则判断为空
     *
     * @return 书本卷目录是否为空
     *
     * @since Api 2
     */
    override fun isEmpty(): Boolean = volumes.isEmpty()
}
