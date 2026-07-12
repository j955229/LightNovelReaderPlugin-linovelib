package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.content.Context
import android.net.Uri
import android.util.Log
import io.nightfish.lightnovelreader.api.book.BookInformation
import io.nightfish.lightnovelreader.api.book.BookVolumes
import io.nightfish.lightnovelreader.api.book.ChapterContent
import io.nightfish.lightnovelreader.api.book.ChapterInformation
import io.nightfish.lightnovelreader.api.book.MutableBookInformation
import io.nightfish.lightnovelreader.api.book.MutableChapterContent
import io.nightfish.lightnovelreader.api.book.Volume
import io.nightfish.lightnovelreader.api.book.WordCount
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.builder.image
import io.nightfish.lightnovelreader.api.content.builder.simpleText
import io.nightfish.lightnovelreader.api.util.Cache
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.explore.ExplorePageProvider
import io.nightfish.lightnovelreader.api.web.search.SearchProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.Connection
import java.io.IOException

@Suppress("unused")
@WebDataSource(
    name = "Linovelib TW",
    provider = "tw.linovelib.com"
)
class LinovelibWebDataSource(
    private val context: Context
) : WebBookDataSource {
    private val parser = LinovelibHtmlParser(LinovelibChineseConverter::toTraditional)
    private val diagnostics = LinovelibDiagnostics()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val offlineStateFlow = MutableStateFlow(true)
    private val requestCoordinator = LinovelibRequestCoordinator()
    private val sessionCookies = mutableMapOf<String, String>()
    private var offlineMonitorJob: Job? = null
    override val permits: Int = 1
    override val cache: Cache = Cache(
        maxCountEachType = LinovelibDataSourceConfiguration.cacheEntriesPerType,
        timeout = LinovelibDataSourceConfiguration.cacheTimeoutMillis
    )
    override val id: Int = "linovelib_tw".hashCode()
    override val offLine: Boolean get() = offlineStateFlow.value
    override val isOffLineFlow: StateFlow<Boolean> = offlineStateFlow
    override val searchProvider: SearchProvider = LinovelibSearchProvider(
        ::getHtml,
        ::getSearchHtml,
        parser,
        diagnostics
    )
    override val explorePageProvider: ExplorePageProvider = LinovelibExplorePageProvider(::getHtml, parser)
    override val imageHeader: Map<String, String> = mapOf(
        "User-Agent" to CONTENT_USER_AGENT,
        "Referer" to LinovelibUrls.CONTENT_HOST,
        "Accept" to "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    )

    @Synchronized
    override fun onLoad() {
        if (!LinovelibDataSourceConfiguration.shouldStartOfflineMonitor(offlineMonitorJob?.isActive == true)) return
        offlineMonitorJob = scope.launch {
            offlineStateFlow.value = isOffLine()
        }
    }

    override suspend fun isOffLine(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(
                url = LinovelibUrls.HOST,
                userAgent = USER_AGENT,
                referrer = LinovelibUrls.HOST,
                acceptLanguage = "zh-TW,zh;q=0.9,en;q=0.7"
            ).statusCode() !in 200..399
        }.getOrElse { true }
    }

    override suspend fun getBookInformation(id: String): BookInformation = withContext(Dispatchers.IO) {
        runCatching {
            parser.parseBookInformation(id, getHtml(LinovelibUrls.book(id))).toBookInformation()
                .takeUnless(BookInformation::isEmpty)
                ?: fallbackBookInformation(id)
        }.getOrElse {
            it.rethrowIfCancellation()
            Log.e(TAG, "Failed to get book information: $id", it)
            diagnostics.error("BOOK_ERROR", it, mapOf("bookId" to id))
            fallbackBookInformation(id)
        }
    }

    override suspend fun getBookVolumes(id: String): BookVolumes = withContext(Dispatchers.IO) {
        runCatching {
            parser.parseCatalog(id, getHtml(LinovelibUrls.catalog(id))).toBookVolumes()
        }.getOrElse {
            it.rethrowIfCancellation()
            Log.e(TAG, "Failed to get book volumes: $id", it)
            diagnostics.error("CATALOG_ERROR", it, mapOf("bookId" to id))
            BookVolumes.empty(id)
        }
    }

    override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent = withContext(Dispatchers.IO) {
        runCatching {
            getChapterContentPages(chapterId, bookId).toChapterContent()
        }.getOrElse {
            it.rethrowIfCancellation()
            Log.e(TAG, "Failed to get chapter content: bookId=$bookId, chapterId=$chapterId", it)
            diagnostics.error(
                "CHAPTER_ERROR",
                it,
                mapOf("bookId" to bookId, "chapterId" to chapterId)
            )
            ChapterContent.empty(chapterId)
        }
    }

    private suspend fun getHtml(url: String): String = executeRequest(
        url = url,
        userAgent = USER_AGENT,
        referrer = LinovelibUrls.HOST,
        acceptLanguage = "zh-TW,zh;q=0.9,en;q=0.7"
    ).body()

    private suspend fun getContentHtml(url: String): String = executeRequest(
        url = url,
        userAgent = CONTENT_USER_AGENT,
        referrer = "${LinovelibUrls.CONTENT_HOST}/",
        acceptLanguage = CONTENT_ACCEPT_LANGUAGE,
        cookies = mapOf("night" to "0"),
        cacheControl = true
    ).body()

    private suspend fun getSearchHtml(keyword: String): LinovelibSearchResponse {
        val searchKeyword = LinovelibChineseConverter.toSimplified(keyword)
        val guardJs = executeRequest(
            url = "${LinovelibUrls.CONTENT_HOST}/search.html?search_guard=js",
            userAgent = CONTENT_USER_AGENT,
            referrer = "${LinovelibUrls.CONTENT_HOST}/",
            acceptLanguage = CONTENT_ACCEPT_LANGUAGE,
            cacheControl = true,
            includeSessionCookies = false
        )
        val jsToken = LinovelibSearchGuard.extractCookieValue(guardJs.body(), "jieqiSearchJs")
        require(jsToken.isNotEmpty()) { "Missing jieqiSearchJs search guard cookie" }

        val guardCss = executeRequest(
            url = "${LinovelibUrls.CONTENT_HOST}/search.html?search_guard=css",
            userAgent = CONTENT_USER_AGENT,
            referrer = "${LinovelibUrls.CONTENT_HOST}/",
            acceptLanguage = CONTENT_ACCEPT_LANGUAGE,
            cacheControl = true,
            includeSessionCookies = false
        )
        val cssToken = guardCss.cookies()["jieqiSearchCss"].orEmpty()
        require(cssToken.isNotEmpty()) { "Missing jieqiSearchCss search guard cookie" }

        val redeem = executeRequest(
            url = "${LinovelibUrls.CONTENT_HOST}/search.html?search_guard=redeem&r=${System.currentTimeMillis()}",
            userAgent = CONTENT_USER_AGENT,
            referrer = "${LinovelibUrls.CONTENT_HOST}/",
            acceptLanguage = CONTENT_ACCEPT_LANGUAGE,
            cookies = LinovelibSearchGuard.guardCookies(jsToken, cssToken),
            cacheControl = true,
            includeSessionCookies = false
        )
        val ticketToken = redeem.cookies()["jieqiSearchTicket"].orEmpty()
        require(ticketToken.isNotEmpty()) { "Missing jieqiSearchTicket search cookie" }
        diagnostics.info(
            "SEARCH_GUARD_OK",
            mapOf("keyword" to keyword, "convertedKeyword" to searchKeyword)
        )

        val encodedKeyword = java.net.URLEncoder.encode(searchKeyword, "UTF-8")
        val response = executeRequest(
            url = "${LinovelibUrls.CONTENT_HOST}/search.html?searchkey=$encodedKeyword",
            userAgent = CONTENT_USER_AGENT,
            referrer = "${LinovelibUrls.CONTENT_HOST}/",
            acceptLanguage = CONTENT_ACCEPT_LANGUAGE,
            cookies = LinovelibSearchGuard.ticketCookies(ticketToken),
            cacheControl = true,
            includeSessionCookies = false
        )
        return LinovelibSearchResponse(
            finalUrl = response.url().toString(),
            html = response.body()
        )
    }

    private suspend fun executeRequest(
        url: String,
        userAgent: String,
        referrer: String,
        acceptLanguage: String,
        cookies: Map<String, String> = emptyMap(),
        cacheControl: Boolean = false,
        includeSessionCookies: Boolean = true
    ): Connection.Response {
        val startedAt = System.nanoTime()
        return try {
            var lastNetworkError: Throwable? = null
            for (attempt in 0 until LinovelibRequestPolicy.maxAttempts) {
                val response = try {
                    executeSingleRequest(
                        url = url,
                        userAgent = userAgent,
                        referrer = referrer,
                        acceptLanguage = acceptLanguage,
                        cookies = cookies,
                        cacheControl = cacheControl,
                        includeSessionCookies = includeSessionCookies
                    )
                } catch (throwable: Throwable) {
                    throwable.rethrowIfCancellation()
                    lastNetworkError = throwable
                    if (throwable !is IOException || attempt == LinovelibRequestPolicy.maxAttempts - 1) {
                        throw throwable
                    }
                    val retryDelay = LinovelibRequestPolicy.networkRetryDelayMillis(attempt)
                    requestCoordinator.scheduleCooldown(retryDelay)
                    diagnostics.info(
                        "HTTP_RETRY",
                        mapOf(
                            "requested" to url,
                            "attempt" to attempt + 1,
                            "delayMs" to retryDelay,
                            "reason" to throwable.javaClass.simpleName
                        )
                    )
                    continue
                }

                val html = response.body()
                val inspection = diagnostics.inspectHtml(html)
                val successful = diagnostics.isSuccessfulHttpStatus(response.statusCode())
                diagnostics.info(
                    if (successful) "HTTP_OK" else "HTTP_STATUS_ERROR",
                    linkedMapOf(
                        "requested" to url,
                        "final" to response.url().toString(),
                        "status" to response.statusCode(),
                        "chars" to inspection.characters,
                        "elapsedMs" to elapsedMilliseconds(startedAt),
                        "loadFailure" to inspection.hasLoadFailure
                    )
                )
                if (successful) return response

                val retryDelay = LinovelibRequestPolicy.retryDelayMillis(
                    statusCode = response.statusCode(),
                    retryAfter = response.header("Retry-After"),
                    attempt = attempt
                )
                if (retryDelay == null || attempt == LinovelibRequestPolicy.maxAttempts - 1) {
                    throw IOException("HTTP ${response.statusCode()} for ${response.url()}")
                }
                requestCoordinator.scheduleCooldown(retryDelay)
                diagnostics.info(
                    "HTTP_RETRY",
                    mapOf(
                        "requested" to url,
                        "status" to response.statusCode(),
                        "attempt" to attempt + 1,
                        "delayMs" to retryDelay
                    )
                )
            }
            throw lastNetworkError ?: IOException("Request attempts exhausted for $url")
        } catch (throwable: Throwable) {
            throwable.rethrowIfCancellation()
            diagnostics.error(
                "HTTP_ERROR",
                throwable,
                linkedMapOf(
                    "requested" to url,
                    "elapsedMs" to elapsedMilliseconds(startedAt)
                )
            )
            throw throwable
        }
    }

    private suspend fun executeSingleRequest(
        url: String,
        userAgent: String,
        referrer: String,
        acceptLanguage: String,
        cookies: Map<String, String>,
        cacheControl: Boolean,
        includeSessionCookies: Boolean
    ): Connection.Response = requestCoordinator.execute {
        val requestCookies = if (includeSessionCookies) {
            sessionCookies.toMutableMap().apply { putAll(cookies) }
        } else {
            cookies
        }
        val connection = Jsoup.connect(url)
            .userAgent(userAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", acceptLanguage)
            .referrer(referrer)
            .cookies(requestCookies)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .acceptLinovelibContentTypes()
            .timeout(12_000)
        if (cacheControl) connection.header("Cache-Control", "no-cache")
        val response = withContext(Dispatchers.IO) { connection.execute() }
        currentCoroutineContext().ensureActive()
        response.also {
            if (includeSessionCookies) sessionCookies.putAll(response.cookies())
        }
    }

    private suspend fun getChapterContentPages(chapterId: String, bookId: String): ParsedChapterContent {
        val websiteChapterId = LinovelibChapterIds.forWebsite(chapterId)
        val pages = mutableListOf<ParsedChapterContent>()
        val visitedUrls = mutableSetOf<String>()
        var nextUrl = LinovelibUrls.fullChapter(bookId, websiteChapterId)
        var pageCount = 0
        diagnostics.info(
            "CHAPTER_START",
            linkedMapOf(
                "bookId" to bookId,
                "chapterId" to chapterId,
                "websiteChapterId" to websiteChapterId,
                "url" to nextUrl
            )
        )

        while (true) {
            if (nextUrl.isBlank()) {
                diagnostics.info("CHAPTER_STOP", mapOf("reason" to "blank-url", "pages" to pageCount))
                break
            }
            if (pageCount >= MAX_CHAPTER_PAGES) {
                diagnostics.info("CHAPTER_STOP", mapOf("reason" to "page-limit", "pages" to pageCount))
                break
            }
            if (!visitedUrls.add(nextUrl)) {
                diagnostics.info(
                    "CHAPTER_STOP",
                    mapOf("reason" to "repeated-url", "pages" to pageCount, "url" to nextUrl)
                )
                break
            }
            pageCount++
            val page = parser.parseChapterContent(
                chapterId = websiteChapterId,
                html = getContentHtml(nextUrl),
                baseUrl = LinovelibUrls.CONTENT_HOST,
                restoreParagraphOrder = true
            )
            pages.add(page)
            val candidate = absoluteUrl(page.nextPageUrl, LinovelibUrls.CONTENT_HOST)
            val candidateChapterId = parser.chapterIdFromHref(candidate)
            val isSameChapterPage = candidate.isNotBlank() && candidateChapterId == websiteChapterId
            val textBlocks = page.blocks.filterIsInstance<ParsedContentBlock.Text>()
            diagnostics.info(
                "CHAPTER_PAGE",
                linkedMapOf(
                    "page" to pageCount,
                    "url" to nextUrl,
                    "blocks" to page.blocks.size,
                    "textBlocks" to textBlocks.size,
                    "textChars" to textBlocks.sumOf { it.text.length },
                    "images" to page.blocks.count { it is ParsedContentBlock.Image },
                    "nextUrl" to candidate,
                    "nextId" to candidateChapterId,
                    "sameChapterPage" to isSameChapterPage
                )
            )
            if (!isSameChapterPage) {
                diagnostics.info(
                    "CHAPTER_STOP",
                    mapOf(
                        "reason" to if (candidate.isBlank()) "no-next-url" else "next-chapter",
                        "pages" to pageCount,
                        "nextUrl" to candidate,
                        "nextId" to candidateChapterId
                    )
                )
                break
            }
            nextUrl = candidate
        }

        val first = pages.firstOrNull() ?: return ParsedChapterContent(
            id = chapterId,
            title = "",
            previousChapterId = "",
            nextChapterId = "",
            nextPageUrl = "",
            blocks = emptyList()
        )
        val last = pages.last()
        diagnostics.info(
            "CHAPTER_DONE",
            linkedMapOf(
                "bookId" to bookId,
                "chapterId" to chapterId,
                "websiteChapterId" to websiteChapterId,
                "pages" to pages.size,
                "blocks" to pages.sumOf { it.blocks.size },
                "textChars" to pages.sumOf { page ->
                    page.blocks.filterIsInstance<ParsedContentBlock.Text>().sumOf { it.text.length }
                },
                "images" to pages.sumOf { page -> page.blocks.count { it is ParsedContentBlock.Image } },
                "previousId" to first.previousChapterId,
                "nextId" to last.nextChapterId
            )
        )
        return ParsedChapterContent(
            id = chapterId,
            title = first.title,
            previousChapterId = LinovelibChapterIds.forApp(first.previousChapterId),
            nextChapterId = LinovelibChapterIds.forApp(last.nextChapterId),
            nextPageUrl = last.nextPageUrl,
            blocks = pages.flatMap { it.blocks }
        )
    }

    private fun absoluteUrl(url: String, host: String = LinovelibUrls.HOST): String {
        if (url.isBlank()) return ""
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> host + url
            else -> "$host/$url"
        }
    }

    private fun elapsedMilliseconds(startedAt: Long): Long =
        (System.nanoTime() - startedAt) / 1_000_000

    private fun ParsedBookInformation.toBookInformation(): BookInformation {
        if (title.isEmpty()) return BookInformation.empty(id)
        return MutableBookInformation(
            id = id,
            title = title,
            subtitle = subtitle,
            coverUrl = coverUrl.takeIf(String::isNotEmpty)?.let(Uri::parse) ?: Uri.EMPTY,
            author = author,
            description = description,
            tags = tags,
            publishingHouse = publishingHouse,
            wordCount = WordCount(wordCount),
            lastUpdated = lastUpdated.atStartOfDay(),
            isComplete = isComplete
        )
    }

    private fun fallbackBookInformation(id: String): BookInformation =
        parser.cachedBookInformation(id)?.toBookInformation()
            ?: parser.cachedExploreBook(id)?.toBookInformation()
            ?: BookInformation.empty(id)

    private fun ParsedExploreBook.toBookInformation(): BookInformation =
        MutableBookInformation(
            id = id,
            title = title,
            subtitle = "",
            coverUrl = coverUrl.takeIf(String::isNotEmpty)?.let(Uri::parse) ?: Uri.EMPTY,
            author = author,
            description = "",
            tags = emptyList(),
            publishingHouse = "",
            wordCount = WordCount(0),
            lastUpdated = LinovelibDates.unknownDateTime(),
            isComplete = false
        )

    private fun ParsedCatalog.toBookVolumes(): BookVolumes =
        BookVolumes(
            bookId = bookId,
            volumes = volumes.mapIndexed { index, volume ->
                Volume(
                    volumeId = "$bookId-${index + 1}",
                    volumeTitle = volume.title,
                    chapters = volume.chapters.map {
                        ChapterInformation(
                            id = LinovelibChapterIds.forApp(it.id),
                            title = it.title
                        )
                    }
                )
            }
        )

    private fun ParsedChapterContent.toChapterContent(): ChapterContent {
        val content = ContentBuilder().apply {
            LinovelibContentFormatter.format(blocks).forEach { block ->
                when (block) {
                    is ParsedContentBlock.Text -> simpleText(block.text)
                    is ParsedContentBlock.Image -> image(Uri.parse(imageUriString(block.url)))
                }
            }
        }.build()

        return MutableChapterContent(
            id = id,
            title = title,
            content = content,
            lastChapter = previousChapterId,
            nextChapter = nextChapterId
        )
    }

    private fun imageUriString(url: String): String = LinovelibImageProxy.route(url)

    private companion object {
        const val TAG = "LinovelibWebDataSource"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Mobile Safari/537.36"
        const val CONTENT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36 EdgA/135.0.0.0"
        const val CONTENT_ACCEPT_LANGUAGE = "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6"
        const val MAX_CHAPTER_PAGES = 100
    }
}
