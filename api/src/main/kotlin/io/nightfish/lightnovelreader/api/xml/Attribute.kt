package io.nightfish.lightnovelreader.api.xml

/**
 * XML元素的属性类
 * 用于在[XmlBuilder]中为XML元素指定属性键值对
 *
 * @property name 属性名
 * @property value 属性值，为null时该属性不会被添加到XML元素中
 *
 * @since Api 2
 */
open class Attribute(val name: String, val value: Any?) {
    /** [Attribute] 工厂方法和常量集合 @since Api 2 */
    companion object {
        /**
         * 空属性单例，名称和值均为空
         *
         * @since Api 2
         */
        val empty = Attribute(name = "", value = null)
    }
}
