package io.nightfish.lightnovelreader.api.content.builder

import io.nightfish.lightnovelreader.api.content.component.SimpleTextComponentData

/**
 * 向[ContentBuilder]中添加一个简单文本组件
 *
 * @param text 组件包含的文本内容
 *
 * @return 当前构建器实例, 支持链式调用
 *
 * @since Api 2
 */
fun ContentBuilder.simpleText(text: String): ContentBuilder = component(SimpleTextComponentData(text))