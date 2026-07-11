package io.nightfish.lightnovelreader.api.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 可将字符串或字符串资源id统一包装为Composable可解析的对象
 * 用于在非Composable范围内主动提供可本地化文本
 *
 * @since Api 2
 */
class LocalString {
    /**
     * 在 Compose 上下文中获取本地化字符串的函数
     *
     * @since Api 2
     */
    val stringGetter: @Composable () -> String

    /**
     * 以字符串直接创建, 不需本地化
     *
     * @param string 直接显示的字符串
     *
     * @since Api 2
     */
    constructor(string: String) {
        stringGetter = { string }
    }

    /**
     * 以字符串资源id创建, 在Compose中解析为本地化字符串
     *
     * @param id 字符串资源id
     *
     * @since Api 2
     */
    constructor(@StringRes id: Int) {
        stringGetter = {
            stringResource(id)
        }
    }

    /**
     * 在Composable中解析并返回字符串
     *
     * @return 解析后的字符串
     *
     * @since Api 2
     */
    @Composable
    fun resolve() = stringGetter()
}

/**
 * 将字符串包装为[LocalString]
 *
 * @return [LocalString]实例
 *
 * @since Api 2
 */
fun String.local() = LocalString(this)