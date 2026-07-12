package io.nightfish.lightnovelreader.plugin.linovelib.source

import java.net.URI
import java.util.Base64

internal object LinovelibImageProxy {
    const val authority = "io.nightfish.lightnovelreader.plugin.linovelib.images"

    fun uriString(originalUrl: String): String {
        if (!isAllowed(originalUrl)) return originalUrl
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(originalUrl.toByteArray(Charsets.UTF_8))
        return "content://$authority/image/$encoded"
    }

    fun route(originalUrl: String): String =
        if (isAllowed(originalUrl)) uriString(originalUrl) else originalUrl

    fun decodeUriString(proxyUri: String): String {
        val encoded = URI.create(proxyUri).path.substringAfterLast('/')
        return Base64.getUrlDecoder().decode(encoded).toString(Charsets.UTF_8)
    }

    fun isAllowed(url: String): Boolean = runCatching {
        val uri = URI.create(url)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme.equals("https", ignoreCase = true) && (
            host.endsWith(".readpai.com") ||
                host == "readpai.com" ||
                host.endsWith(".linovelib.com") ||
                host == "linovelib.com" ||
                host.endsWith(".bilinovel.com") ||
                host == "bilinovel.com"
            )
    }.getOrDefault(false)
}
