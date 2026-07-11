package io.nightfish.lightnovelreader.api.plugin

/**
 * 插件相关常量
 *
 * @since Api 2
 */
object PluginConstants {
    /**
     * 插件发现所需的Intent Action
     * 软件通过发送此Action的广播来扫描已安装的插件
     *
     * @since Api 2
     */
    const val DISCOVERY_ACTION = "io.nightfish.lightnovelreader.PLUGIN_DISCOVERY"
}
