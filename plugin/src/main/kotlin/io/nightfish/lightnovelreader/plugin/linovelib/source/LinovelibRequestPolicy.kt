package io.nightfish.lightnovelreader.plugin.linovelib.source

internal object LinovelibRequestPolicy {
    const val maxAttempts = 4
    const val minimumRequestIntervalMillis = 1_200L
    private const val maximumRetryDelayMillis = 60_000L

    fun requestDelayMillis(
        lastRequestStartedAtMillis: Long,
        nowMillis: Long,
        minimumIntervalMillis: Long = minimumRequestIntervalMillis
    ): Long = (lastRequestStartedAtMillis + minimumIntervalMillis - nowMillis).coerceAtLeast(0L)

    fun retryDelayMillis(statusCode: Int, retryAfter: String?, attempt: Int): Long? {
        val headerDelay = retryAfter
            ?.trim()
            ?.toLongOrNull()
            ?.times(1_000L)
            ?.coerceIn(0L, maximumRetryDelayMillis)

        return when {
            statusCode == 429 -> headerDelay ?: progressiveDelay(3_000L, attempt)
            statusCode == 408 || statusCode == 425 || statusCode in 500..599 ->
                progressiveDelay(1_000L, attempt)
            else -> null
        }
    }

    fun networkRetryDelayMillis(attempt: Int): Long = progressiveDelay(1_000L, attempt)

    private fun progressiveDelay(baseMillis: Long, attempt: Int): Long =
        (baseMillis * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(maximumRetryDelayMillis)
}
