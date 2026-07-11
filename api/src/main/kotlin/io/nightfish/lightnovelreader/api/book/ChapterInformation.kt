package io.nightfish.lightnovelreader.api.book

/**
 * 章节基础信息
 *
 * @param id 章节id
 * @param title 章节标题
 *
 * @since Api 2
 */
data class ChapterInformation(
    val id: String,
    val title: String
): CanBeEmpty {
    /**
     * 判断章节信息是否为空
     * 章节id为空字符串时判断为空
     *
     * @return 章节信息是否为空
     *
     * @since Api 2
     */
    override fun isEmpty(): Boolean = id.isEmpty()
}