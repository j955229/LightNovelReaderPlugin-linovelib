package io.nightfish.lightnovelreader.api.book

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime

/**
 * 用户书本阅读数据接口
 *
 * @property id 书本id
 * @property lastReadTime 最后阅读时间
 * @property totalReadTime 总阅读时长(单位: 秒), -1表示尚未阅读
 * @property readingProgress 书本整体阅读进度(0.0~1.0)
 * @property lastReadChapterId 最后阅读的章节id
 * @property lastReadChapterTitle 最后阅读的章节标题
 * @property currentChapterReadingProgressMap 各章节的当前阅读进度Map, 以章节id为key
 * @property maxChapterReadingProgressMap 各章节的历史最高阅读进度Map, 以章节id为key
 *
 * @since Api 2
 */
@Stable
interface UserReadingData: CanBeEmpty, Copyable<UserReadingData> {
    val id: String
    val lastReadTime: LocalDateTime
    val totalReadTime: Int
    val readingProgress: Float
    val lastReadChapterId: String
    val lastReadChapterTitle: String
    val currentChapterReadingProgressMap: Map<String, Float>
    val maxChapterReadingProgressMap: Map<String, Float>

    override fun isEmpty(): Boolean = id.isEmpty()

    /**
     * 用户阅读数据工厂方法集合
     *
     * @since Api 2
     */
    companion object {
        /**
         * 返回一个空的用户阅读数据
         *
         * @since Api 2
         */
        fun empty(): UserReadingData = MutableUserReadingData(
            "",
            LocalDateTime.MIN,
            -1,
            0.0f,
            "",
            "",
            emptyMap(),
            emptyMap()
        )
    }

    /**
     * 转化为可变对象
     * 如果对象本身就是可变对象, 则直接返回自身
     * 如果对象并非可变对象, 则复制一份可变对象并返回
     *
     * @return 可变的用户阅读数据对象
     *
     * @since Api 2
     */
    fun toMutable(): MutableUserReadingData {
        if (this is MutableUserReadingData)
            return this
        return MutableUserReadingData(id, lastReadTime, totalReadTime, readingProgress, lastReadChapterId, lastReadChapterTitle, currentChapterReadingProgressMap, maxChapterReadingProgressMap)
    }

    override fun copy(): UserReadingData = MutableUserReadingData(id, lastReadTime, totalReadTime, readingProgress, lastReadChapterId, lastReadChapterTitle, currentChapterReadingProgressMap, maxChapterReadingProgressMap)
}

/**
 * 可变的用户书本阅读数据对象
 * 其中每一个成员都是可被UI观测的
 *
 * @property id 书本id
 * @property lastReadTime 最后阅读时间
 * @property totalReadTime 总阅读时长(单位: 秒), -1表示尚未阅读
 * @property readingProgress 书本整体阅读进度(0.0~1.0)
 * @property lastReadChapterId 最后阅读的章节id
 * @property lastReadChapterTitle 最后阅读的章节标题
 * @property currentChapterReadingProgressMap 各章节的当前阅读进度Map, 以章节id为key
 * @property maxChapterReadingProgressMap 各章节的历史最高阅读进度Map, 以章节id为key
 *
 * @param id 书本id
 * @param lastReadTime 最后阅读时间
 * @param totalReadTime 总阅读时长(单位: 秒), -1表示尚未阅读
 * @param readingProgress 书本整体阅读进度(0.0~1.0)
 * @param lastReadChapterId 最后阅读的章节id
 * @param lastReadChapterTitle 最后阅读的章节标题
 * @param currentChapterReadingProgressMap 各章节的当前阅读进度Map, 以章节id为key
 * @param maxChapterReadingProgressMap 各章节的历史最高阅读进度Map, 以章节id为key
 *
 * @constructor 返回可变用户阅读数据对象
 *
 * @since Api 2
 */
class MutableUserReadingData(
    id: String,
    lastReadTime: LocalDateTime,
    totalReadTime: Int,
    readingProgress: Float,
    lastReadChapterId: String,
    lastReadChapterTitle: String,
    currentChapterReadingProgressMap: Map<String, Float>,
    maxChapterReadingProgressMap: Map<String, Float>
): UserReadingData {
    override var id by mutableStateOf(id)
    override var lastReadTime by mutableStateOf(lastReadTime)
    override var totalReadTime by mutableIntStateOf(totalReadTime)
    override var readingProgress by mutableFloatStateOf(readingProgress)
    override var lastReadChapterId by mutableStateOf(lastReadChapterId)
    override var lastReadChapterTitle by mutableStateOf(lastReadChapterTitle)
    override val currentChapterReadingProgressMap = mutableStateMapOf(*currentChapterReadingProgressMap.map { Pair(it.key, it.value) }.toTypedArray())
    override val maxChapterReadingProgressMap = mutableStateMapOf(*maxChapterReadingProgressMap.map { Pair(it.key, it.value) }.toTypedArray())

    /**
     * 可变用户阅读数据工厂方法集合
     *
     * @since Api 2
     */
    companion object {
        /**
         * 返回一个空的可变用户阅读数据对象
         *
         * @return 空的[MutableUserReadingData]
         *
         * @since Api 2
         */
        fun empty(): MutableUserReadingData = MutableUserReadingData(
            "",
            LocalDateTime.MIN,
            -1,
            0.0f,
            "",
            "",
            emptyMap(),
            emptyMap()
        )
    }

    /**
     * 用另一个用户阅读数据对象的数据更新自身
     *
     * @param userReadingData 包含新数据的用户阅读数据对象
     *
     * @since Api 2
     */
    fun update(userReadingData: UserReadingData) {
        this.id = userReadingData.id
        this.lastReadTime = userReadingData.lastReadTime
        this.totalReadTime = userReadingData.totalReadTime
        this.readingProgress = userReadingData.readingProgress
        this.lastReadChapterId = userReadingData.lastReadChapterId
        this.lastReadChapterTitle = userReadingData.lastReadChapterTitle
        this.currentChapterReadingProgressMap.clear()
        this.currentChapterReadingProgressMap.putAll(userReadingData.currentChapterReadingProgressMap)
        this.maxChapterReadingProgressMap.clear()
        this.maxChapterReadingProgressMap.putAll(userReadingData.maxChapterReadingProgressMap)
    }

    /**
     * 更新指定章节的阅读进度并同时更新历史最高阅读进度
     *
     * @param chapterId 目标章节id
     * @param progress 当前阅读进度値(0.0~1.0)
     *
     * @since Api 2
     */
    fun updateChapterReadingProgress(chapterId: String, progress: Float) {
        val maxProgress = progress.coerceAtLeast(this.maxChapterReadingProgressMap[chapterId] ?: 0f)
        this.currentChapterReadingProgressMap[chapterId] = progress
        this.maxChapterReadingProgressMap[chapterId] = maxProgress
    }
}
