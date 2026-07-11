package io.nightfish.lightnovelreader.api.web

/**
 * 标记一个类为网络数据源
 * 被此注解标记的类将在运行时被宿主识别并加载为[WebBookDataSource]
 *
 * @property name 数据源的显示名称
 * @property provider 数据源的提供方名称
 *
 * @since Api 2
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class WebDataSource(
    val name: String,
    val provider: String
)
