package io.nightfish.lightnovelreader.plugin.linovelib.source

import org.jsoup.Jsoup

internal data class LinovelibSearchChallenge(
    val challenge: String,
    val actionUrl: String,
    val imageUrl: String,
    val formFields: Map<String, String>
)

internal class LinovelibSearchException(message: String) : Exception(message)

internal object LinovelibSearchCaptcha {
    fun parse(html: String, pageUrl: String): LinovelibSearchChallenge? {
        val document = Jsoup.parse(html, pageUrl)
        val challengeInput = document.selectFirst("input[name=search_challenge]") ?: return null
        val challenge = challengeInput.attr("value").trim()
        if (challenge.isEmpty()) return null
        val form = challengeInput.closest("form")
        val actionUrl = form?.absUrl("action")?.takeIf(String::isNotBlank) ?: pageUrl
        val formFields = linkedMapOf<String, String>()
        form?.select("input[type=hidden][name]")?.forEach { input ->
            formFields[input.attr("name")] = input.attr("value")
        }
        formFields["search_challenge"] = challenge
        val imageUrl = document.selectFirst("img[src*=checkcode]")
            ?.absUrl("src")
            ?.substringBefore("&refresh=")
            ?.takeIf(String::isNotBlank)
            ?: "${LinovelibUrls.CONTENT_HOST}/checkcode.php?scope=search&module=article&id=$challenge"
        return LinovelibSearchChallenge(
            challenge = challenge,
            actionUrl = actionUrl,
            imageUrl = imageUrl,
            formFields = formFields
        )
    }
}
