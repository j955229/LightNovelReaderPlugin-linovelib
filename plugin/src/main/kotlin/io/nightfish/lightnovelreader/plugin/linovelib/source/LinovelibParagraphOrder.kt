package io.nightfish.lightnovelreader.plugin.linovelib.source

internal object LinovelibParagraphOrder {
    private const val FIXED_PARAGRAPHS = 20
    private const val MULTIPLIER = 9302L
    private const val INCREMENT = 49397L
    private const val MODULUS = 233280L
    private const val CHAPTER_MULTIPLIER = 126L
    private const val CHAPTER_INCREMENT = 232L

    fun <T> restore(chapterId: String, shuffled: List<T>): List<T> {
        if (shuffled.size <= FIXED_PARAGRAPHS) return shuffled
        val numericChapterId = chapterId.toLongOrNull() ?: return shuffled
        val permutation = (shuffled.indices).toMutableList()
        var seed = numericChapterId * CHAPTER_MULTIPLIER + CHAPTER_INCREMENT

        for (index in shuffled.lastIndex downTo FIXED_PARAGRAPHS + 1) {
            seed = (seed * MULTIPLIER + INCREMENT) % MODULUS
            val swapIndex = FIXED_PARAGRAPHS +
                ((seed.toDouble() / MODULUS) * (index - FIXED_PARAGRAPHS + 1)).toInt()
            val value = permutation[index]
            permutation[index] = permutation[swapIndex]
            permutation[swapIndex] = value
        }

        val restored = shuffled.toMutableList()
        shuffled.indices.forEach { rawIndex ->
            restored[permutation[rawIndex]] = shuffled[rawIndex]
        }
        return restored
    }
}
