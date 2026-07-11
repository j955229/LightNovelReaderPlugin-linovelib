package io.nightfish.lightnovelreader.api.ui

import androidx.compose.ui.graphics.Color

/**
 * 阅读器样式配置
 *
 * @param fontSize 字体大小(sp)
 * @param fontLineHeight 行间距附加大小(sp)
 * @param fontWeight 字体粗细程度(100~900)
 * @param textColor 亮色模式下的文本颜色
 * @param textDarkColor 深色模式下的文本颜色
 *
 * @since Api 2
 */
data class ReaderStyle(
    val fontSize: Float,
    val fontLineHeight: Float,
    val fontWeight: Float,
    val textColor: Color,
    val textDarkColor: Color
)
