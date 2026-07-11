package io.nightfish.lightnovelreader.api.web

/**
 * 网络数据源的优先级枚举
 * 优先级越高的数据源将被优先使用
 *
 * @since Api 2
 */
enum class WebDataSourcePriority {
    /** 高优先级 */
    High,
    /** 默认优先级 */
    Default,
    /** 低优先级 */
    Low
}