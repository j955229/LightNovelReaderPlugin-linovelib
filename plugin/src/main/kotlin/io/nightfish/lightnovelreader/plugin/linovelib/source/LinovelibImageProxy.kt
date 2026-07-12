package io.nightfish.lightnovelreader.plugin.linovelib.source

import java.net.URI

internal object LinovelibImageProxy {
    const val authority = "io.nightfish.lightnovelreader.plugin.linovelib.images"

    fun uriString(originalUrl: String): String {
        if (!isAllowed(originalUrl)) return originalUrl
        val originalBytes = originalUrl.toByteArray(Charsets.UTF_8)
        require(originalBytes.size <= MAX_ORIGINAL_URL_BYTES) { "Image URL is too long" }
        val encoded = encodeHex(originalBytes)
        return "content://$authority/image/h/$encoded"
    }

    fun route(originalUrl: String): String =
        if (isAllowed(originalUrl)) uriString(originalUrl) else originalUrl

    fun decodeUriString(proxyUri: String): String {
        val uri = URI.create(proxyUri)
        require(uri.scheme == "content" && uri.authority == authority) { "Invalid image URI authority" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "Invalid image URI suffix" }
        val segments = uri.rawPath.split('/')
        val decoded = when {
            segments.size == 4 && segments[0].isEmpty() &&
                segments[1] == "image" && segments[2] == "h" && segments[3].isNotEmpty() ->
                decodeHex(segments[3])
            segments.size == 3 && segments[0].isEmpty() &&
                segments[1] == "image" && segments[2].isNotEmpty() ->
                decodeLegacyBase64Url(segments[2])
            else -> throw IllegalArgumentException("Invalid image URI path")
        }
        require(decoded.size <= MAX_ORIGINAL_URL_BYTES) { "Decoded image URL is too long" }
        return decoded.toString(Charsets.UTF_8)
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

    private fun encodeHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private fun decodeHex(encoded: String): ByteArray {
        require(encoded.length <= MAX_ENCODED_PATH_CHARS) { "Encoded image URL is too long" }
        require(encoded.length % 2 == 0) { "Invalid hexadecimal image URL" }
        return ByteArray(encoded.length / 2) { index ->
            val high = hexValue(encoded[index * 2])
            val low = hexValue(encoded[index * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    private fun hexValue(character: Char): Int = when (character) {
        in '0'..'9' -> character.code - '0'.code
        in 'a'..'f' -> character.code - 'a'.code + 10
        in 'A'..'F' -> character.code - 'A'.code + 10
        else -> throw IllegalArgumentException("Invalid hexadecimal image URL")
    }

    private fun decodeLegacyBase64Url(encoded: String): ByteArray {
        require(encoded.length <= MAX_ENCODED_PATH_CHARS) { "Encoded image URL is too long" }
        val paddingStart = encoded.indexOf('=')
        val data = if (paddingStart >= 0) encoded.substring(0, paddingStart) else encoded
        val paddingCount = encoded.length - data.length
        require(data.isNotEmpty() && data.length % 4 != 1) { "Invalid legacy image URL length" }
        if (paddingCount > 0) {
            require(paddingCount in 1..2 && encoded.drop(paddingStart).all { it == '=' }) {
                "Invalid legacy image URL padding"
            }
            val expectedPadding = (4 - data.length % 4) % 4
            require(encoded.length % 4 == 0 && paddingCount == expectedPadding) {
                "Invalid legacy image URL padding"
            }
        }
        val output = ByteArray(data.length * 6 / 8)
        var outputIndex = 0
        var buffer = 0
        var bufferedBits = 0
        data.forEach { character ->
            val value = BASE64_URL_DIGITS.indexOf(character)
            require(value >= 0) { "Invalid legacy image URL" }
            buffer = (buffer shl 6) or value
            bufferedBits += 6
            if (bufferedBits >= 8) {
                bufferedBits -= 8
                output[outputIndex++] = (buffer shr bufferedBits).toByte()
                buffer = if (bufferedBits == 0) 0 else buffer and ((1 shl bufferedBits) - 1)
            }
        }
        require(buffer == 0) { "Invalid legacy image URL trailing bits" }
        return output.copyOf(outputIndex)
    }

    private const val HEX_DIGITS = "0123456789abcdef"
    private const val BASE64_URL_DIGITS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val MAX_ORIGINAL_URL_BYTES = 2_048
    private const val MAX_ENCODED_PATH_CHARS = MAX_ORIGINAL_URL_BYTES * 2
}
