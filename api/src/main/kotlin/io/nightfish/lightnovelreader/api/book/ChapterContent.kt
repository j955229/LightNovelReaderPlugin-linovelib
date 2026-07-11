package io.nightfish.lightnovelreader.api.book

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.nightfish.lightnovelreader.api.util.empty
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

/**
 * 章节内容接口
 *
 * @property id 章节id
 * @property title 章节标题
 * @property content 章节内容的JSON对象，内含组件列表
 * @property lastChapter 上一章的章节id，如果没有上一章则为空字符串
 * @property nextChapter 下一章的章节id，如果没有下一章则为空字符串
 *
 * @since Api 2
 */
@Stable
interface ChapterContent: CanBeEmpty, Copyable<ChapterContent> {
    val id: String
    val title: String
    val content: JsonObject
    val lastChapter: String
    val nextChapter: String

    /**
     * 判断是否存在上一章
     *
     * @return 是否存在上一章
     *
     * @since Api 2
     */
    fun hasPrevChapter(): Boolean = lastChapter.isNotEmpty()

    /**
     * 判断是否存在下一章
     *
     * @return 是否存在下一章
     *
     * @since Api 2
     */
    fun hasNextChapter(): Boolean = nextChapter.isNotEmpty()

    override fun isEmpty() = this.id.isEmpty()
            || this.content.isEmpty()
            || this.content["components"]?.jsonArray?.isEmpty() ?: true


    override fun copy(): ChapterContent =
        MutableChapterContent(id, title, content, lastChapter, nextChapter)

    /**
     * 章节内容工厂方法集合
     *
     * @since Api 2
     */
    companion object {
        /**
         * 返回一个空的章节内容, 并将章节id设为空
         *
         * @since Api 2
         */
        fun empty(): ChapterContent = empty("")

        /**
         * 返回一个空的章节内容, 并保有章节id
         *
         * @param chapterId 章节id
         *
         * @return 空的章节内容
         *
         * @since Api 2
         */
        fun empty(chapterId: String): ChapterContent = MutableChapterContent(
            chapterId,
            "",
            JsonObject.empty()
        )
    }

    /**
     * 转化为可变对象
     * 如果对象本身就是可变对象, 则直接返回自身
     * 如果对象并非可变对象, 则复制一份可变对象并返回
     *
     * @return 可变的章节内容对象
     *
     * @since Api 2
     */
    fun toMutable(): MutableChapterContent {
        if (this is MutableChapterContent)
            return this
        return MutableChapterContent(id, title, content, lastChapter, nextChapter)
    }
}

/**
 * 可变的章节内容对象
 * 其中每一个成员都是可被UI观测的
 *
 * @property id 章节id
 * @property title 章节标题
 * @property content 章节内容的JSON对象，内含组件列表
 * @property lastChapter 上一章的章节id，默认为空字符串
 * @property nextChapter 下一章的章节id，默认为空字符串
 *
 * @param id 章节id
 * @param title 章节标题
 * @param content 章节内容的JSON对象
 * @param lastChapter 上一章的章节id，默认为空字符串
 * @param nextChapter 下一章的章节id，默认为空字符串
 *
 * @constructor 返回可变章节内容对象
 *
 * @since Api 2
 */
class MutableChapterContent(
    id: String,
    title: String,
    content: JsonObject,
    lastChapter: String = "",
    nextChapter: String = ""
) : ChapterContent {
    override var id by mutableStateOf(id)
    override var title by mutableStateOf(title)
    override var content by mutableStateOf(content)
    override var lastChapter by mutableStateOf(lastChapter)
    override var nextChapter by mutableStateOf(nextChapter)

    /**
     * 可变章节内容工厂方法集合
     *
     * @since Api 2
     */
    companion object {
        /**
         * 返回一个内容为空的可变章节内容对象
         *
         * @return 内容为空的[MutableChapterContent]
         *
         * @since Api 2
         */
        fun empty(): MutableChapterContent = MutableChapterContent("", "", JsonObject.empty() )
    }

    /**
     * 用另一个章节内容对象的数据更新自身
     *
     * @param chapterContent 包含新数据的章节内容对象
     *
     * @since Api 2
     */
    fun update(chapterContent: ChapterContent) {
        this.id = chapterContent.id
        this.title = chapterContent.title
        this.content = chapterContent.content
        this.lastChapter = chapterContent.lastChapter
        this.nextChapter = chapterContent.nextChapter
    }

    /**
     * 判断两个[MutableChapterContent]是否相等
     *
     * @param other 另一个对象
     * @return id、content、lastChapter、nextChapter均相同则返回true
     *
     * @since Api 2
     */
    override fun equals(other: Any?): Boolean {
        if (other is ChapterContent) {
            return this.id == other.id &&
                    this.content == other.content &&
                    this.lastChapter == other.lastChapter &&
                    this.nextChapter == other.nextChapter
        }
        return super.equals(other)
    }

    /**
     * 基于id、title、content、lastChapter、nextChapter计算哈希値
     *
     * @return 哈希値
     *
     * @since Api 2
     */
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + lastChapter.hashCode()
        result = 31 * result + nextChapter.hashCode()
        return result
    }
}