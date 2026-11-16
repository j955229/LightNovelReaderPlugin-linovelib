package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinovelibSearchCaptchaTest {
    private val pageUrl = "https://www.bilinovel.com/search.html?searchkey=%E9%AD%94%E5%A5%B3"

    @Test
    fun parsesCaptchaChallengePage() {
        val html = """
            <html><head><title>搜索验证</title></head><body><main>
            <form method="post" action="">
            <input type="hidden" name="searchkey" value="魔女">
            <input type="hidden" name="searchtype" value="all">
            <input type="hidden" name="search_challenge" value="4ddaa99812c779c6c18b90246f26f28c">
            <input id="search_code" name="search_code" maxlength="4">
            <button type="button" id="refresh-code"><img id="search-captcha" src="/checkcode.php?scope=search&amp;module=article&amp;id=4ddaa99812c779c6c18b90246f26f28c"></button>
            <button type="submit">验证并搜索</button>
            </form></main></body></html>
        """.trimIndent()

        val challenge = LinovelibSearchCaptcha.parse(html, pageUrl)

        assertNotNull(challenge)
        challenge!!
        assertEquals("4ddaa99812c779c6c18b90246f26f28c", challenge.challenge)
        assertEquals(pageUrl, challenge.actionUrl)
        assertEquals(
            "https://www.bilinovel.com/checkcode.php?scope=search&module=article&id=4ddaa99812c779c6c18b90246f26f28c",
            challenge.imageUrl
        )
        assertEquals(
            mapOf(
                "searchkey" to "魔女",
                "searchtype" to "all",
                "search_challenge" to "4ddaa99812c779c6c18b90246f26f28c"
            ),
            challenge.formFields
        )
    }

    @Test
    fun returnsNullForSearchResultPage() {
        val html = "<html><head><title>魔女 - 搜索结果</title></head><body><ul class=\"book-ol\"></ul></body></html>"

        assertNull(LinovelibSearchCaptcha.parse(html, pageUrl))
    }
}
