package io.nightfish.lightnovelreader.plugin.linovelib.source

internal object LinovelibContentFormatter {
    fun format(blocks: List<ParsedContentBlock>): List<ParsedContentBlock> {
        val result = mutableListOf<ParsedContentBlock>()
        val paragraphs = mutableListOf<String>()

        fun flushParagraphs() {
            if (paragraphs.isEmpty()) return
            result += ParsedContentBlock.Text(
                paragraphs.joinToString("\n\n") { paragraph -> INDENT + paragraph }
            )
            paragraphs.clear()
        }

        blocks.forEach { block ->
            when (block) {
                is ParsedContentBlock.Text -> block.text.trim()
                    .takeIf(String::isNotEmpty)
                    ?.let(paragraphs::add)
                is ParsedContentBlock.Image -> {
                    flushParagraphs()
                    result += block
                }
            }
        }
        flushParagraphs()
        return result
    }

    private const val INDENT = "\u3000\u3000"
}
