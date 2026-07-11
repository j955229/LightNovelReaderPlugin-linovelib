package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.junit.Assert.assertEquals
import org.junit.Test

class LinovelibSearchGuardTest {
    @Test
    fun extractsJavascriptCookieFromGuardResponse() {
        val javascript = """
            document.cookie="jieqiSearchJs=495454.example-token; path=/; max-age=3600";
        """.trimIndent()

        assertEquals(
            "495454.example-token",
            LinovelibSearchGuard.extractCookieValue(javascript, "jieqiSearchJs")
        )
    }

    @Test
    fun buildsSearchCookiesFromGuardValues() {
        assertEquals(
            mapOf(
                "night" to "0",
                "jieqiSearchJs" to "js-token",
                "jieqiSearchCss" to "css-token"
            ),
            LinovelibSearchGuard.guardCookies("js-token", "css-token")
        )
        assertEquals(
            mapOf("jieqiSearchTicket" to "ticket-token"),
            LinovelibSearchGuard.ticketCookies("ticket-token")
        )
    }
}
