package io.nightfish.lightnovelreader.plugin.linovelib.source

object LinovelibUrls {
    const val HOST = "https://tw.linovelib.com"
    const val SIMPLIFIED_HOST = "https://www.linovelib.com"
    const val CONTENT_HOST = "https://www.bilinovel.com"
    val SEARCH_HOSTS = listOf(HOST, SIMPLIFIED_HOST)
    const val TOP = "$HOST/top.html"
    const val COMPLETE = "$HOST/topfull/postdate/1.html"
    const val TOP_POSTDATE = "$HOST/top/postdate/1.html"

    fun top(host: String): String = "$host/top.html"

    fun complete(host: String): String = "$host/topfull/postdate/1.html"

    fun topPostdate(host: String): String = "$host/top/postdate/1.html"

    fun wenku(order: String, page: Int): String =
        wenku(HOST, order, page)

    fun wenku(host: String, order: String, page: Int): String =
        "$host/wenku/${order}_0_0_0_0_0_0_0_${page}_0.html"

    fun book(bookId: String): String = "$HOST/novel/$bookId.html"

    fun catalog(bookId: String): String = "$HOST/novel/$bookId/catalog"

    fun chapter(bookId: String, chapterId: String): String = "$HOST/novel/$bookId/$chapterId.html"

    fun fullChapter(bookId: String, chapterId: String): String =
        "$CONTENT_HOST/novel/$bookId/$chapterId.html"
}
