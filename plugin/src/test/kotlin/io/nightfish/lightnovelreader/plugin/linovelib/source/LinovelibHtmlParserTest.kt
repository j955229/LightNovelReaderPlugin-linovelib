package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LinovelibHtmlParserTest {
    private val parser = LinovelibHtmlParser()

    @Test
    fun parseBookInformationReadsMetadataFromNovelPage() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://tw.linovelib.com/files/article/image/0/1/1s.jpg" />
              </head>
              <body>
                <h1 class="book-title">High School DxD</h1>
                <span class="authorname"><a href="/authorarticle/Ichiei-Ishibumi.html">Ichiei Ishibumi</a></span>
                <p class="book-meta book-layout-inline">29035 readers<span>/</span>5283 votes<b>/</b></p>
                <p class="book-meta book-layout-inline">398.2 ${"\u842c\u5b57"}<span>|</span>${"\u5b8c\u7d50"}<span>|</span>anime</p>
                <span class="tag-small-group origin-left">
                  <em class="tag-small red"><a href="/wenku/lastupdate_1_0_0_0_0_0_0_1_0.html">School</a></em>
                  <em class="tag-small red"><a href="/wenku/lastupdate_2_0_0_0_0_0_0_1_0.html">Fantasy</a></em>
                  <em class="tag-small orange"><a>Fujimi Fantasia Bunko</a></em>
                </span>
                <section id="bookSummary">
                  <content>Line one<br>Line two</content>
                  <aside class="backupname"><em>Alias</em><span>DxD</span></aside>
                </section>
                <a class="book-meta book-status">
                  <div>last update<span>dot</span>2024-02-25</div>
                </a>
              </body>
            </html>
        """.trimIndent()

        val book = parser.parseBookInformation("1", html)

        assertEquals("1", book.id)
        assertEquals("High School DxD", book.title)
        assertEquals("DxD", book.subtitle)
        assertEquals("https://tw.linovelib.com/files/article/image/0/1/1s.jpg", book.coverUrl)
        assertEquals("Ichiei Ishibumi", book.author)
        assertEquals("Line one\nLine two", book.description)
        assertEquals(listOf("School", "Fantasy"), book.tags)
        assertEquals("Fujimi Fantasia Bunko", book.publishingHouse)
        assertEquals(3_982_000, book.wordCount)
        assertEquals(LocalDate.of(2024, 2, 25), book.lastUpdated)
        assertTrue(book.isComplete)
        assertEquals(
            "https://tw.linovelib.com/authorarticle/Ichiei-Ishibumi.html",
            parser.relatedTarget("\u4f5c\u8005\uff1aIchiei Ishibumi")
        )
        assertEquals(
            "https://tw.linovelib.com/wenku/lastupdate_1_0_0_0_0_0_0_1_0.html",
            parser.relatedTarget("School")
        )
    }

    @Test
    fun parseCatalogReadsVolumesAndSkipsJavascriptChapterLinks() {
        val html = """
            <div id="volumes">
              <div class="catalog-volume"><ul class="volume-chapters">
                <li class="chapter-bar chapter-li"><a href="/novel/1/vol_1.html"><h3>Volume 1</h3></a></li>
                <li class="chapter-li jsChapter"><a href="/novel/1/108523.html"><span class="chapter-index">Illustrations</span></a></li>
                <li class="chapter-li jsChapter"><a href="javascript:cid(1)"><span class="chapter-index">Life.0</span></a></li>
              </ul></div>
              <div class="catalog-volume"><ul class="volume-chapters">
                <li class="chapter-bar chapter-li"><a href="/novel/1/vol_9.html"><h3>Volume 2</h3></a></li>
                <li class="chapter-li jsChapter"><a href="/novel/1/10.html"><span class="chapter-index">Life.0</span></a></li>
              </ul></div>
            </div>
        """.trimIndent()

        val catalog = parser.parseCatalog("1", html)

        assertEquals("1", catalog.bookId)
        assertEquals(2, catalog.volumes.size)
        assertEquals("Volume 1", catalog.volumes[0].title)
        assertEquals(listOf(ParsedChapter("108523", "Illustrations")), catalog.volumes[0].chapters)
        assertEquals("Volume 2", catalog.volumes[1].title)
        assertEquals(listOf(ParsedChapter("10", "Life.0")), catalog.volumes[1].chapters)
    }

    @Test
    fun parseChapterContentReadsTextImagesAndNavigation() {
        val html = """
            <script>
              var ReadParams={url_previous:'/novel/1/225507.html',url_next:'/novel/1/115455.html',chapterid:'225508'}
            </script>
            <h1 id="atitle">Afterword</h1>
            <div id="acontent">
              <p>Hello again.</p>
              <br>
              <p><img src="/files/image.jpg" /></p>
              <div id="hidden-images">
                <img data-src="/files/hidden-1.jpg" />
                <img data-src="/files/hidden-2.jpg" />
              </div>
              <p>Short notes follow.</p>
              <center style="color:red;">${"\u624b\u6a5f\u7248\u9801\u9762"} warning</center>
              <div class="cgo">ad</div>
            </div>
        """.trimIndent()

        val chapter = parser.parseChapterContent("225508", html)

        assertEquals("225508", chapter.id)
        assertEquals("Afterword", chapter.title)
        assertEquals("225507", chapter.previousChapterId)
        assertEquals("115455", chapter.nextChapterId)
        assertEquals("/novel/1/115455.html", chapter.nextPageUrl)
        assertEquals(
            listOf(
                ParsedContentBlock.Text("Hello again."),
                ParsedContentBlock.Image("https://tw.linovelib.com/files/image.jpg"),
                ParsedContentBlock.Image("https://tw.linovelib.com/files/hidden-1.jpg"),
                ParsedContentBlock.Image("https://tw.linovelib.com/files/hidden-2.jpg"),
                ParsedContentBlock.Text("Short notes follow.")
            ),
            chapter.blocks
        )
        assertFalse(chapter.blocks.any { it is ParsedContentBlock.Text && it.text.contains("warning") })
    }

    @Test
    fun parseChapterContentKeepsTextAroundInlineImage() {
        val html = """
            <h1 id="atitle">Mixed content</h1>
            <div id="acontent">
              <p>Before image.<img src="/files/inline.jpg" />After image.</p>
            </div>
        """.trimIndent()

        val chapter = parser.parseChapterContent("12", html)

        assertEquals(
            listOf(
                ParsedContentBlock.Text("Before image."),
                ParsedContentBlock.Image("https://tw.linovelib.com/files/inline.jpg"),
                ParsedContentBlock.Text("After image.")
            ),
            chapter.blocks
        )
    }

    @Test
    fun parseChapterContentReadsPagedNextUrlAsSameChapter() {
        val html = """
            <script>
              var ReadParams={url_previous:"/novel/1/108523.html",url_next:"/novel/1/2_2.html",chapterid:'2',page:'1'}
            </script>
            <h1 id="atitle">Life.0</h1>
            <div id="acontent"><p>First page.</p></div>
        """.trimIndent()

        val chapter = parser.parseChapterContent("2", html)

        assertEquals("2", chapter.nextChapterId)
        assertEquals("/novel/1/2_2.html", chapter.nextPageUrl)
    }

    @Test
    fun parseChapterContentRemovesIncompleteLoadFailureTail() {
        val html = """
            <div id="acontent">
              <p>Complete sentence. Broken sta……（${"\u5167\u5bb9\u52a0\u8f09\u5931\u6557"}${"\u624b\u6a5f\u7248\u9801\u9762"}</p>
            </div>
        """.trimIndent()

        val chapter = parser.parseChapterContent("2", html)

        assertEquals(
            listOf(ParsedContentBlock.Text("Complete sentence.")),
            chapter.blocks
        )
    }

    @Test
    fun parseChapterContentKeepsTextBeforeLoadFailureMarker() {
        val html = """
            <div id="acontent">
              <p>Valid line one.<br>Valid line two.${"\u5167\u5bb9\u52a0\u8f09\u5931\u6557"}${"\u624b\u6a5f\u7248\u9801\u9762"}</p>
            </div>
        """.trimIndent()

        val chapter = parser.parseChapterContent("2", html)

        assertEquals(
            listOf(ParsedContentBlock.Text("Valid line one.\nValid line two.")),
            chapter.blocks
        )
    }

    @Test
    fun parseChapterContentRestoresBilinovelParagraphOrder() {
        val shuffled = (0..19).toList() + listOf(22, 24, 25, 21, 29, 27, 20, 28, 23, 26)
        val html = shuffled.joinToString(
            prefix = "<div id=\"acontent\">",
            postfix = "</div>"
        ) { "<p>Paragraph $it</p>" }

        val chapter = parser.parseChapterContent(
            chapterId = "61960",
            html = html,
            baseUrl = LinovelibUrls.CONTENT_HOST,
            restoreParagraphOrder = true
        )

        assertEquals(
            (0..29).map { ParsedContentBlock.Text("Paragraph $it") },
            chapter.blocks
        )
    }

    @Test
    fun parserAppliesTextConverterToChapterAndSearchResults() {
        val convertingParser = LinovelibHtmlParser { text -> "converted:$text" }
        val chapter = convertingParser.parseChapterContent(
            "10",
            """
                <h1 id="atitle">Chapter title</h1>
                <div id="acontent"><p>Chapter body</p></div>
            """.trimIndent()
        )
        val searchRow = convertingParser.parseListRow(
            "Search",
            """
                <ol class="book-ol"><li class="book-li">
                  <a class="book-layout" href="/novel/10.html">
                    <h4 class="book-title">Book title</h4>
                    <p class="book-author">Author Writer</p>
                  </a>
                </li></ol>
            """.trimIndent()
        )

        assertEquals("converted:Chapter title", chapter.title)
        assertEquals(
            listOf(ParsedContentBlock.Text("converted:Chapter body")),
            chapter.blocks
        )
        assertEquals("converted:Book title", searchRow.books.single().title)
        assertEquals("converted:Writer", searchRow.books.single().author)
    }

    @Test
    fun parseChapterContentResolvesImagesAgainstContentHost() {
        val chapter = parser.parseChapterContent(
            chapterId = "10",
            html = "<div id=\"acontent\"><img src=\"/files/illustration.jpg\"></div>",
            baseUrl = LinovelibUrls.CONTENT_HOST
        )

        assertEquals(
            listOf(
                ParsedContentBlock.Image(
                    "${LinovelibUrls.CONTENT_HOST}/files/illustration.jpg"
                )
            ),
            chapter.blocks
        )
    }

    @Test
    fun parseExploreRowsReadsHomePageModules() {
        val html = """
            <section class="module">
              <header class="module-header">
                <h2 class="module-title">Hot Books</h2>
              </header>
              <a class="module-slide-a" href="/novel/1.html">
                <img data-src="/files/article/image/0/1/1s.jpg" alt="High School DxD" />
                <div class="module-slide-caption">High School DxD</div>
                <div class="module-slide-author"><span class="gray">Ichiei Ishibumi</span></div>
              </a>
              <a class="book-layout" href="/novel/2.html">
                <img src="/files/article/image/0/2/2s.jpg" />
                <div class="book-title">Another Novel</div>
                <div class="book-author">Author Tester</div>
              </a>
            </section>
        """.trimIndent()

        val rows = parser.parseExploreRows(html)

        assertEquals(1, rows.size)
        assertEquals("Hot Books", rows[0].title)
        assertEquals(
            listOf(
                ParsedExploreBook("1", "High School DxD", "Ichiei Ishibumi", "https://tw.linovelib.com/files/article/image/0/1/1s.jpg"),
                ParsedExploreBook("2", "Another Novel", "Tester", "https://tw.linovelib.com/files/article/image/0/2/2s.jpg")
            ),
            rows[0].books
        )
        assertEquals(listOf(rows[0].books[0]), parser.searchExploreBooks("DxD", rows))
    }

    @Test
    fun parseBookInformationFallsBackToOpenGraphMetadata() {
        val html = """
            <html>
              <head>
                <meta property="og:novel:book_name" content="Meta Title" />
                <meta property="og:novel:author" content="Meta Author" />
                <meta property="og:description" content="Meta description" />
                <meta property="og:novel:category" content="Meta Publisher" />
                <meta property="og:novel:update_time" content="2025-01-02 12:00:00" />
                <meta property="og:novel:status" content="${"\u5b8c\u7d50"}" />
              </head>
            </html>
        """.trimIndent()

        val book = parser.parseBookInformation("9", html)

        assertEquals("Meta Title", book.title)
        assertEquals("Meta Author", book.author)
        assertEquals("Meta description", book.description)
        assertEquals("Meta Publisher", book.publishingHouse)
        assertEquals(LocalDate.of(2025, 1, 2), book.lastUpdated)
        assertTrue(book.isComplete)
    }

    @Test
    fun parseRankingRowsReadsCategoryLists() {
        val html = """
            <div class="category-list">
              <a class="fl-header"><div class="fl-header-l">Monthly</div></a>
              <ul class="fl-content">
                <li><a href="/novel/10.html">Ranked Book</a></li>
                <li><a href="/novel/11.html">Second Book</a></li>
              </ul>
            </div>
        """.trimIndent()

        val rows = parser.parseRankingRows(html)

        assertEquals(1, rows.size)
        assertEquals("Monthly", rows[0].title)
        assertEquals(
            listOf(
                ParsedExploreBook("10", "Ranked Book", "", "https://tw.linovelib.com/files/article/image/0/10/10s.jpg"),
                ParsedExploreBook("11", "Second Book", "", "https://tw.linovelib.com/files/article/image/0/11/11s.jpg")
            ),
            rows[0].books
        )
    }

    @Test
    fun parseRankingRowsBuildsCoverPathForFourDigitBookId() {
        val html = """
            <div class="category-list">
              <a class="fl-header">
                <div class="fl-header-l">Monthly</div>
                <img src="/files/article/image/3/3768/3768s.jpg" />
              </a>
              <ul class="fl-content">
                <li><a href="/novel/3768.html">Ranked Book</a></li>
              </ul>
            </div>
        """.trimIndent()

        val book = parser.parseRankingRows(html).single().books.single()

        assertEquals("https://tw.linovelib.com/files/article/image/3/3768/3768s.jpg", book.coverUrl)
    }

    @Test
    fun parseListRowReadsCompleteBookList() {
        val html = """
            <ol class="book-ol">
              <li class="book-li">
                <a href="/novel/20.html" class="book-layout">
                  <img data-src="/files/article/image/0/20/20s.jpg" />
                  <h4 class="book-title">Complete Book</h4>
                  <p class="book-author">${"\u4f5c\u8005"} Writer</p>
                </a>
              </li>
            </ol>
        """.trimIndent()

        val row = parser.parseListRow("Complete", html)

        assertEquals("Complete", row.title)
        assertEquals(
            listOf(ParsedExploreBook("20", "Complete Book", "Writer", "https://tw.linovelib.com/files/article/image/0/20/20s.jpg")),
            row.books
        )
    }

    @Test
    fun parseListRowUsesImageAltWhenVisibleTitleIsTruncated() {
        val html = """
            <ol class="book-ol">
              <li class="book-li">
                <a href="/novel/21.html" class="book-layout">
                  <img data-src="/files/article/image/0/21/21s.jpg" alt="Very Long Complete Book Title" />
                  <h4 class="book-title">Very Long Complete...</h4>
                </a>
              </li>
            </ol>
        """.trimIndent()

        val row = parser.parseListRow("Wenku", html)

        assertEquals("Very Long Complete Book Title", row.books.single().title)
    }

    @Test
    fun searchExploreBooksMatchesSimplifiedAndTraditionalKeywords() {
        val rows = listOf(
            ParsedExploreRow(
                "Books",
                listOf(
                    ParsedExploreBook("3247", "刀劍神域 進擊篇 Progressive", "川原礫", ""),
                    ParsedExploreBook("8", "歡迎來到實力至上主義的教室", "衣笠彰梧", "")
                )
            )
        )

        assertEquals("3247", parser.searchExploreBooks("刀剑神域", rows).single().id)
        assertEquals("8", parser.searchExploreBooks("实力至上", rows).single().id)
        assertEquals("3247", parser.searchExploreBooks("川原砾", rows).single().id)
    }

    @Test
    fun parseRankingRowsCanBeSearchedWithSimplifiedKeyword() {
        val rankingHtml = """
            <div class="category-list">
              <a class="fl-header"><div class="fl-header-l">Monthly</div></a>
              <ul class="fl-content">
                <li><a href="/novel/8.html">${"\u6b61\u8fce\u4f86\u5230\u5be6\u529b\u81f3\u4e0a\u4e3b\u7fa9\u7684\u6559\u5ba4"}</a></li>
              </ul>
            </div>
        """.trimIndent()
        val parsedRows = parser.parseRankingRows(rankingHtml)
        assertEquals("8", parser.searchExploreBooks("\u5b9e\u529b\u81f3\u4e0a", parsedRows).single().id)
    }
}
