package io.nightfish.lightnovelreader.plugin.linovelib.source

internal object LinovelibSearchGuard {
    fun extractCookieValue(body: String, name: String): String =
        Regex("""(?:^|[\"'])${Regex.escape(name)}=([^;\"']+)""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    fun guardCookies(jsToken: String, cssToken: String): Map<String, String> = mapOf(
        "night" to "0",
        "jieqiSearchJs" to jsToken,
        "jieqiSearchCss" to cssToken
    )

    fun ticketCookies(ticketToken: String): Map<String, String> =
        mapOf("jieqiSearchTicket" to ticketToken)
}
