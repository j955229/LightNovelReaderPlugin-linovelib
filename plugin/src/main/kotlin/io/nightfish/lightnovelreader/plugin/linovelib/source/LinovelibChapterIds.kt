package io.nightfish.lightnovelreader.plugin.linovelib.source

internal object LinovelibChapterIds {
    private const val PREFIX = "linovelib-v3:"
    private val VERSION_PREFIXES = listOf(PREFIX, "linovelib-v2:")

    fun forApp(chapterId: String): String =
        if (chapterId.isBlank()) chapterId else PREFIX + forWebsite(chapterId)

    fun forWebsite(chapterId: String): String =
        VERSION_PREFIXES.firstOrNull(chapterId::startsWith)
            ?.let(chapterId::removePrefix)
            ?: chapterId
}
