package io.nightfish.lightnovelreader.api.book

import androidx.navigation.NavController
import io.nightfish.lightnovelreader.api.web.WebDataSourcePriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * 书本相关的Api
 *
 * @since Api 2
 */
interface BookRepositoryApi {
    /**
     * 获取书本详情
     *
     * @param id 需要获取的书本id
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return 书本详情对象
     *
     * @since Api 3
     */
    suspend fun getBookInformation(id: String, priority: WebDataSourcePriority): BookInformation

    /**
     * 获取可观测的书本详情
     * 需要传入[CoroutineScope]用于主动更新内容
     * 调用此函数后会启动一个协程来更新其内容
     * 遵照先本地后远程的顺序更新数据
     *
     * @param id 需要获取的书本id
     * @param coroutineScope 详情数据主动更新的协程作用域
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return 可观测的书本详情, 本质上是一个[MutableBookInformation]对象, 如果远程和本地都没有成功获取则返回保留书本id的空详情
     *
     * @since Api 2
     */
    fun getStateBookInformation(
        id: String,
        coroutineScope: CoroutineScope,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): BookInformation

    /**
     * 获取书本详情的流
     * 流遵照先本地后远程的顺序发射数据
     *
     * @param id 需要获取的书本id
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return [BookInformation]对象的流, 如果远程和本地都没有成功获取则发射保留书本id的空详情
     *
     * @since Api 2
     */
    fun getBookInformationFlow(
        id: String,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): Flow<BookInformation>

    /**
     * 获取书本卷目录的流
     * 流遵照先本地后远程的顺序发射数据
     *
     * @param id 需要获取的书本id
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return [BookVolumes]对象的流, 如果远程和本地都没有成功获取则发射保留书本id的空目录
     *
     * @since Api 2
     */
    fun getBookVolumesFlow(
        id: String,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): Flow<BookVolumes>

    /**
     * 获取章节内容
     *
     * @param chapterId 需要获取的章节id
     * @param bookId 需要获取章节所属的书本id
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return 章节内容对象
     *
     * @since Api 3
     */
    suspend fun getChapterContent(
        chapterId: String,
        bookId: String,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): ChapterContent


    /**
     * 获取可观测的章节内容
     * 需要传入[CoroutineScope]用于主动更新内容
     * 调用此函数后会启动一个协程来更新其内容
     * 遵照先本地后远程的顺序更新数据
     *
     * @param chapterId 需要获取的章节id
     * @param bookId 需要获取章节所属的书本id
     * @param coroutineScope 详情数据主动更新的协程作用域
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return 可观测的书本章节内容, 本质上是一个[MutableChapterContent]对象, 如果远程和本地都没有成功获取则返回保留id的空章节内容
     *
     * @since Api 2
     */
    fun getStateChapterContent(
        chapterId: String,
        bookId: String,
        coroutineScope: CoroutineScope,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): ChapterContent

    /**
     * 获取章节内容的流
     * 流遵照先本地后远程的顺序发射数据
     *
     * @param chapterId 需要获取的章节id
     * @param bookId 需要获取章节所属的书本id
     * @param priority 此请求的优先级, 后端可能会依据优先级处理网络请求
     *
     * @return [ChapterContent]对象的流, 如果远程和本地都没有成功获取则发射保留书本id的空章节内容
     *
     * @since Api 2
     */
    fun getChapterContentFlow(
        chapterId: String,
        bookId: String,
        priority: WebDataSourcePriority = WebDataSourcePriority.Default
    ): Flow<ChapterContent>

    /**
     * 获取阅读数据
     *
     * @param bookId 请求的书本阅读数据所属的书本id
     *
     * @return 书本阅读数据
     *
     * @since Api 2
     */
    fun getUserReadingData(bookId: String): UserReadingData

    /**
     * 获取可观测的阅读数据
     * 需要传入[CoroutineScope]用于主动更新内容
     * 调用此函数后会启动一个协程来更新其内容
     *
     * @param bookId 需要获取章节所属的书本id
     * @param coroutineScope 详情数据主动更新的协程作用域
     *
     * @return 可观测的阅读数据, 本质上是一个[MutableUserReadingData]对象
     *
     * @since Api 2
     */
    fun getStateUserReadingData(bookId: String, coroutineScope: CoroutineScope): UserReadingData

    /**
     * 获取阅读数据的流
     *
     * @param bookId 请求的书本阅读数据所属的书本id
     *
     * @return [UserReadingData]对象的流
     *
     * @since Api 2
     */
    fun getUserReadingDataFlow(bookId: String): Flow<UserReadingData>

    /**
     * 获取全部存在的书本阅读数据
     *
     * @return 书本阅读数据的列表
     *
     * @since Api 2
     */
    fun getAllUserReadingData(): List<UserReadingData>

    /**
     * 更新用户书本阅读数据
     *
     * @param id 需要更新的书本id
     *
     * @sample io.nightfish.lightnovelreader.api.sample.updateUserReadingData
     *
     * @since Api 2
     */
    fun updateUserReadingData(id: String, update: (MutableUserReadingData) -> UserReadingData)

    /**
     * 获取书本是缓存状态
     *
     * @param bookId 查询的书本id
     *
     * @return 是否缓存
     *
     * @since Api 2
     */
    suspend fun getIsBookCached(bookId: String): Boolean

    /**
     * 将书本标签点击事件交于数据源处处理
     *
     * @param tag 书本标签名称
     * @param navController 导航控制器
     *
     */
    fun progressBookTagClick
                (tag: String, navController: NavController)
}