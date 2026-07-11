package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.icu.text.Transliterator
import android.os.Build

internal object LinovelibChineseConverter {
    private val simplifiedToTraditional: Transliterator? by lazy {
        createTransliterator("Simplified-Traditional")
    }
    private val traditionalToSimplified: Transliterator? by lazy {
        createTransliterator("Traditional-Simplified")
    }

    fun toTraditional(text: String): String = transliterate(simplifiedToTraditional, text)

    fun toSimplified(text: String): String = transliterate(traditionalToSimplified, text)

    private fun createTransliterator(id: String): Transliterator? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching { Transliterator.getInstance(id) }.getOrNull()
    }

    private fun transliterate(transliterator: Transliterator?, text: String): String {
        if (transliterator == null || text.isEmpty()) return text
        return synchronized(transliterator) {
            transliterator.transliterate(text)
        }
    }
}
