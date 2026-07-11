package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

data class ParsedBookInformation(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val author: String,
    val description: String,
    val tags: List<String>,
    val publishingHouse: String,
    val wordCount: Int,
    val lastUpdated: LocalDate,
    val isComplete: Boolean
)

data class ParsedCatalog(
    val bookId: String,
    val volumes: List<ParsedVolume>
)

data class ParsedVolume(
    val title: String,
    val chapters: List<ParsedChapter>
)

data class ParsedChapter(
    val id: String,
    val title: String
)

data class ParsedChapterContent(
    val id: String,
    val title: String,
    val previousChapterId: String,
    val nextChapterId: String,
    val nextPageUrl: String,
    val blocks: List<ParsedContentBlock>
)

data class ParsedExploreRow(
    val title: String,
    val books: List<ParsedExploreBook>
)

data class ParsedExploreBook(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String
)

sealed class ParsedContentBlock {
    data class Text(val text: String) : ParsedContentBlock()
    data class Image(val url: String) : ParsedContentBlock()
}

class LinovelibHtmlParser(
    private val textConverter: (String) -> String = { it }
) {
    private val parsedBookCache = ConcurrentHashMap<String, ParsedBookInformation>()
    private val exploreBookCache = ConcurrentHashMap<String, ParsedExploreBook>()

    fun parseBookInformation(id: String, html: String): ParsedBookInformation {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        val meta = { property: String ->
            document.selectFirst("meta[property=\"$property\"]")?.attr("content").orEmpty()
        }
        val metaText = document.select(".book-detail-info .book-meta.book-layout-inline")
            .joinToString(" ") { it.text() }
            .ifBlank {
                document.select(".book-meta.book-layout-inline").joinToString(" ") { it.text() }
            }
        val tagElements = document.select(".tag-small-group .tag-small")
        val tags = tagElements
            .filterNot { it.hasClass("orange") || it.hasClass("gray") }
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        val publishingHouse = tagElements
            .firstOrNull { it.hasClass("orange") }
            ?.text()
            .orEmpty()

        return ParsedBookInformation(
            id = id,
            title = document.selectFirst(".book-detail-info .book-title, .book-title")?.text().orEmpty()
                .ifBlank { meta("og:novel:book_name") },
            subtitle = document.selectFirst(".backupname span")?.text().orEmpty(),
            coverUrl = normalizeUrl(meta("og:image")),
            author = document.selectFirst(".authorname a")?.text().orEmpty()
                .ifBlank { meta("og:novel:author") },
            description = document.selectFirst("#bookSummary content")
                ?.let(::htmlWithBreaksToText)
                ?.ifBlank { null }
                ?: meta("og:description"),
            tags = tags,
            publishingHouse = publishingHouse.ifBlank { meta("og:novel:category") },
            wordCount = parseWordCount(metaText),
            lastUpdated = parseLastUpdated(
                document.selectFirst(".book-status")?.text().orEmpty()
                    .ifBlank { meta("og:novel:update_time") }
            ),
            isComplete = metaText.contains(STATUS_COMPLETE) || meta("og:novel:status") == STATUS_COMPLETE
        ).also { parsedBookCache[id] = it }
    }

    fun parseCatalog(bookId: String, html: String): ParsedCatalog {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        val volumes = document.select("#volumes .catalog-volume")
            .mapNotNull { volume ->
                val title = volume.selectFirst(".chapter-bar h3")?.text().orEmpty()
                if (title.isEmpty()) return@mapNotNull null
                val chapters = volume.select(".jsChapter a")
                    .mapNotNull { chapter ->
                        val href = chapter.attr("href")
                        val chapterId = chapterIdFromUrl(href) ?: return@mapNotNull null
                        val chapterTitle = chapter.selectFirst(".chapter-index")?.text()
                            ?: chapter.text()
                        ParsedChapter(chapterId, chapterTitle)
                    }
                ParsedVolume(title, chapters)
            }
        return ParsedCatalog(bookId, volumes)
    }

    fun parseChapterContent(
        chapterId: String,
        html: String,
        baseUrl: String = LinovelibUrls.HOST,
        restoreParagraphOrder: Boolean = false
    ): ParsedChapterContent {
        val document = Jsoup.parse(html, baseUrl)
        val content = document.selectFirst("#acontent")
        content?.select(".cgo, center, script, style, ins")?.remove()
        val contentElements = content?.children()?.toList().orEmpty()
        val orderedElements = if (restoreParagraphOrder) {
            restoreParagraphs(chapterId, contentElements)
        } else {
            contentElements
        }
        val blocks = orderedElements.flatMap(::parseContentBlocks)
        val readParams = document.select("script")
            .joinToString("\n") { it.data() + it.html() }

        return ParsedChapterContent(
            id = chapterId,
            title = textConverter(document.selectFirst("#atitle")?.text().orEmpty()),
            previousChapterId = readParamChapterId(readParams, "url_previous"),
            nextChapterId = readParamChapterId(readParams, "url_next"),
            nextPageUrl = readParamUrl(readParams, "url_next"),
            blocks = blocks
        )
    }

    private fun restoreParagraphs(chapterId: String, elements: List<Element>): List<Element> {
        val positions = elements.indices.filter { index ->
            val element = elements[index]
            element.normalName() == "p" && element.html().replace(Regex("""\s+"""), "").isNotEmpty()
        }
        val restoredParagraphs = LinovelibParagraphOrder.restore(
            chapterId,
            positions.map(elements::get)
        )
        return elements.toMutableList().apply {
            positions.forEachIndexed { paragraphIndex, elementIndex ->
                this[elementIndex] = restoredParagraphs[paragraphIndex]
            }
        }
    }

    fun parseExploreRows(html: String): List<ParsedExploreRow> {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        val rows = document.select(".module")
            .mapNotNull { module ->
                val title = module.selectFirst(".module-title")?.text()?.trim().orEmpty()
                val books = module
                    .select("a.module-slide-a[href], a.book-layout[href]")
                    .mapNotNull(::parseExploreBook)
                    .distinctBy { it.id }
                    .take(MAX_BOOKS_PER_ROW)
                if (title.isBlank() || books.isEmpty()) null else ParsedExploreRow(title, books)
            }
            .filter { it.books.isNotEmpty() }
            .take(MAX_EXPLORE_ROWS)

        if (rows.isNotEmpty()) return rows.also(::rememberExploreRows)

        val fallbackBooks = document
            .select("a[href*=/novel/][href$=.html]")
            .mapNotNull(::parseExploreBook)
            .distinctBy { it.id }
            .take(MAX_BOOKS_PER_ROW)
        return if (fallbackBooks.isEmpty()) {
            emptyList()
        } else {
            listOf(ParsedExploreRow("Home", fallbackBooks)).also(::rememberExploreRows)
        }
    }

    fun parseRankingRows(html: String): List<ParsedExploreRow> {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        return document.select(".category-list")
            .mapNotNull { category ->
                val title = category.selectFirst(".fl-header-l")?.text()?.trim().orEmpty()
                val books = category.select(".fl-content a[href*=/novel/]")
                    .mapNotNull(::parseExploreBook)
                    .distinctBy { it.id }
                    .take(MAX_BOOKS_PER_ROW)
                if (title.isBlank() || books.isEmpty()) null else ParsedExploreRow(title, books)
            }
            .take(MAX_EXPLORE_ROWS)
            .also(::rememberExploreRows)
    }

    fun parseListRow(title: String, html: String): ParsedExploreRow {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        val books = document.select("ol.book-ol .book-li a.book-layout[href], a.book-layout[href]")
            .mapNotNull(::parseExploreBook)
            .distinctBy { it.id }
            .take(MAX_LIST_BOOKS)
        return ParsedExploreRow(title, books).also { rememberExploreRows(listOf(it)) }
    }

    fun parseLastPage(html: String): Int {
        val document = Jsoup.parse(html, LinovelibUrls.HOST)
        document.selectFirst("#pagelink a.last")
            ?.text()
            ?.toIntOrNull()
            ?.let { return it }
        return Regex("""\u7b2c\d+/(\d+)\u9875""")
            .find(document.selectFirst("#pagelink")?.text().orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 1
    }

    fun searchExploreBooks(keyword: String, rows: List<ParsedExploreRow>): List<ParsedExploreBook> {
        val query = keyword.searchFold()
        if (query.isEmpty()) return emptyList()
        return rows
            .flatMap { it.books }
            .distinctBy { it.id }
            .filter {
                it.title.searchFold().contains(query) ||
                    it.author.searchFold().contains(query)
            }
            .take(MAX_SEARCH_RESULTS)
    }

    fun bookIdFromKeyword(keyword: String): String? {
        val trimmed = keyword.trim()
        if (trimmed.all(Char::isDigit) && trimmed.isNotEmpty()) return trimmed
        return bookIdFromHref(trimmed)
    }

    fun bookIdFromHref(href: String): String? {
        return Regex("""/novel/(\d+)(?:\.html|/catalog)?""")
            .find(href)
            ?.groupValues
            ?.getOrNull(1)
    }

    fun chapterIdFromHref(href: String): String? = chapterIdFromUrl(href)

    fun cachedBookInformation(id: String): ParsedBookInformation? = parsedBookCache[id]

    fun cachedExploreBook(id: String): ParsedExploreBook? = exploreBookCache[id]

    private fun parseExploreBook(element: Element): ParsedExploreBook? {
        val id = bookIdFromHref(element.attr("href")) ?: return null
        val image = element.selectFirst("img")
        val visibleTitle = element.selectFirst(".module-slide-caption, .book-title")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
        val imageTitle = image?.attr("alt")?.trim()?.ifBlank { null }
        val title = textConverter(imageTitle
            ?.takeIf { visibleTitle.isNullOrBlank() || visibleTitle.endsWith("...") || visibleTitle.endsWith("\u2026") }
            ?: visibleTitle
            ?: imageTitle
            ?: cleanExploreTitle(element.text()))
        if (title.isBlank()) return null

        val author = textConverter(element.selectFirst(".module-slide-author .gray, .book-author, .module-slide-author")
            ?.text()
            ?.let(::cleanAuthor)
            .orEmpty()
            .ifBlank {
                Regex("""\bAuthor\s*[: ]\s*(\S+)""")
                    .find(element.text())
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
            })
        val coverUrl = image
            ?.attr("data-src")
            ?.ifBlank { image.attr("data-original") }
            ?.ifBlank { image.attr("src") }
            ?.let(::normalizeUrl)
            .orEmpty()

        return ParsedExploreBook(id, title, author, coverUrl).also { exploreBookCache[id] = it }
    }

    private fun parseContentBlock(element: Element): ParsedContentBlock? =
        parseContentBlocks(element).firstOrNull()

    private fun parseContentBlocks(element: Element): List<ParsedContentBlock> {
        if (element.hasClass("cgo") || element.normalName() in SKIPPED_CONTENT_TAGS) return emptyList()
        if (element.normalName() == "img") {
            val sourceAttribute = if (element.attr("data-src").isNotBlank()) "data-src" else "src"
            val src = element.absUrl(sourceAttribute).ifBlank { element.attr(sourceAttribute) }
            return listOf(ParsedContentBlock.Image(normalizeUrl(src)))
        }
        if (element.selectFirst("img") == null) {
            val text = cleanContentText(element.textWithNewLines())
            if (text.isEmpty()) return emptyList()
            return listOf(ParsedContentBlock.Text(textConverter(text)))
        }
        val blocks = mutableListOf<ParsedContentBlock>()
        val text = StringBuilder()

        fun flushText() {
            val cleaned = cleanContentText(text.toString())
            if (cleaned.isNotEmpty()) blocks += ParsedContentBlock.Text(textConverter(cleaned))
            text.clear()
        }

        element.childNodes().forEach { node ->
            when (node) {
                is TextNode -> text.append(node.text())
                is Element -> when {
                    node.normalName() == "br" -> text.append('\n')
                    node.normalName() == "img" || node.selectFirst("img") != null -> {
                        flushText()
                        blocks += parseContentBlocks(node)
                    }
                    else -> text.append(node.textWithNewLines())
                }
            }
        }
        flushText()
        return blocks
    }

    private fun parseWordCount(text: String): Int {
        val match = Regex("""([\d.]+)\s*([\u842c\u4e07]?)\u5b57""").find(text) ?: return 0
        val number = match.groupValues[1].toDoubleOrNull() ?: return 0
        val unit = match.groupValues[2]
        return if (unit.isNotEmpty()) (number * 10_000).toInt() else number.toInt()
    }

    private fun parseLastUpdated(text: String): LocalDate {
        val match = Regex("""\d{4}-\d{2}-\d{2}""").find(text)
        return match?.value?.let(LocalDate::parse) ?: LinovelibDates.unknownLocalDate
    }

    private fun readParamChapterId(script: String, key: String): String {
        val url = readParamUrl(script, key)
        return chapterIdFromUrl(url).orEmpty()
    }

    private fun readParamUrl(script: String, key: String): String =
        Regex("""$key\s*:\s*(['"])(.*?)\1""")
            .find(script)
            ?.groupValues
            ?.getOrNull(2)
            .orEmpty()

    private fun chapterIdFromUrl(url: String): String? =
        Regex("""/novel/\d+/(\d+)(?:_\d+)?\.html""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)

    private fun normalizeUrl(url: String): String {
        if (url.isBlank()) return ""
        val absolute = when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> LinovelibUrls.HOST + url
            else -> "${LinovelibUrls.HOST}/$url"
        }
        return absolute.substringBefore("?")
    }

    private fun Element.textWithNewLines(): String {
        val parts = mutableListOf<String>()
        childNodes().forEach { node ->
            when (node) {
                is TextNode -> node.text().trim().takeIf { it.isNotEmpty() }?.let(parts::add)
                is Element -> when (node.normalName()) {
                    "br" -> parts.add("\n")
                    "p" -> node.textWithNewLines().takeIf { it.isNotBlank() }?.let(parts::add)
                    else -> node.textWithNewLines().takeIf { it.isNotBlank() }?.let(parts::add)
                }
            }
        }
        return parts.joinToString("\n")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    private fun cleanExploreTitle(text: String): String =
        text.trim()
            .replace(Regex("""^top\d+\s*""", RegexOption.IGNORE_CASE), "")
            .substringBefore(" \u4f5c\u8005")
            .substringBefore(" Author")
            .trim()

    private fun cleanAuthor(text: String): String =
        text.trim()
            .removePrefix("Author")
            .removePrefix("\u4f5c\u8005")
            .removePrefix(":")
            .removePrefix("\uff1a")
            .trim()

    private fun String.searchFold(): String =
        lowercase()
            .map { SEARCH_FOLD_MAP[it] ?: it }
            .joinToString("")
            .replace(Regex("""[\s　·・,，.。:：'’"“”\-—_()（）\[\]【】]+"""), "")

    private fun cleanContentText(text: String): String {
        val failureMarkerIndex = listOf(CONTENT_LOAD_FAILED, SIMPLIFIED_CONTENT_LOAD_FAILED)
            .map(text::indexOf)
            .filter { it >= 0 }
            .minOrNull()
        val mobileWarningIndex = text.indexOf(MOBILE_PAGE_WARNING).takeIf { it >= 0 }
        val cutIndex = listOfNotNull(failureMarkerIndex, mobileWarningIndex).minOrNull()
        val visibleText = cutIndex?.let { text.substring(0, it) } ?: text
        return if (failureMarkerIndex != null) {
            trimLoadFailureTail(visibleText)
        } else {
            visibleText.trim()
        }
    }

    private fun trimLoadFailureTail(text: String): String {
        val withoutMarkerTail = text.trim()
            .replace(Regex("""(?:…|．){1,}[（(]?$|\.{3,}[（(]?$"""), "")
            .trim()
        val lastSentenceEnd = Regex("""[。.!！？!?」』”）)]""")
            .findAll(withoutMarkerTail)
            .lastOrNull()
            ?.range
            ?.last
        return if (lastSentenceEnd != null && lastSentenceEnd < withoutMarkerTail.lastIndex) {
            withoutMarkerTail.substring(0, lastSentenceEnd + 1).trim()
        } else {
            withoutMarkerTail
        }
    }

    private fun htmlWithBreaksToText(element: Element): String =
        Jsoup.parseBodyFragment(
            element.html().replace(Regex("(?i)<br\\s*/?>"), "\n")
        ).body().wholeText()
            .replace(Regex("[ \t]*\n[ \t]*"), "\n")
            .replace(Regex("\n{2,}"), "\n")
            .trim()

    private fun rememberExploreRows(rows: List<ParsedExploreRow>) {
        rows.flatMap { it.books }.forEach { exploreBookCache[it.id] = it }
    }

    private companion object {
        const val MAX_EXPLORE_ROWS = 8
        const val MAX_BOOKS_PER_ROW = 12
        const val MAX_LIST_BOOKS = 30
        const val MAX_SEARCH_RESULTS = 20
        const val STATUS_COMPLETE = "\u5b8c\u7d50"
        const val CONTENT_LOAD_FAILED = "\u5167\u5bb9\u52a0\u8f09\u5931\u6557"
        const val SIMPLIFIED_CONTENT_LOAD_FAILED = "\u5185\u5bb9\u52a0\u8f7d\u5931\u8d25"
        const val MOBILE_PAGE_WARNING = "\u624b\u6a5f\u7248\u9801\u9762"
        val SKIPPED_CONTENT_TAGS = setOf("center", "script", "style", "ins")
        val SEARCH_FOLD_MAP = mapOf(
            '剑' to '劍',
            '进' to '進',
            '击' to '擊',
            '实' to '實',
            '势' to '勢',
            '义' to '義',
            '欢' to '歡',
            '错' to '錯',
            '题' to '題',
            '儿' to '兒',
            '异' to '異',
            '转' to '轉',
            '变' to '變',
            '这' to '這',
            '档' to '檔',
            '关' to '關',
            '于' to '於',
            '为' to '為',
            '与' to '與',
            '轻' to '輕',
            '说' to '說',
            '学' to '學',
            '战' to '戰',
            '斗' to '鬥',
            '龙' to '龍',
            '爱' to '愛',
            '恋' to '戀',
            '传' to '傳',
            '网' to '網',
            '馆' to '館',
            '迷' to '迷',
            '砾' to '礫'
        )
    }
}
