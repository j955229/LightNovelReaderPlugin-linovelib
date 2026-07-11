package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

data class HtmlInspection(
    val characters: Int,
    val hasLoadFailure: Boolean
)

class LinovelibDiagnostics(
    private val sink: (Int, String, String, Throwable?) -> Unit = ::writeAndroidLog
) {
    fun info(event: String, fields: Map<String, Any?> = emptyMap()) {
        sink(Log.INFO, TAG, format(event, fields), null)
    }

    fun error(
        event: String,
        throwable: Throwable,
        fields: Map<String, Any?> = emptyMap()
    ) {
        sink(Log.ERROR, TAG, format(event, fields), throwable)
    }

    fun inspectHtml(html: String): HtmlInspection = HtmlInspection(
        characters = html.length,
        hasLoadFailure = LOAD_FAILURE_MARKERS.any(html::contains)
    )

    fun isSuccessfulHttpStatus(status: Int): Boolean = status in 200..399

    private fun format(event: String, fields: Map<String, Any?>): String = buildString {
        append(event)
        fields.forEach { (key, value) ->
            append(' ')
            append(key)
            append('=')
            append(value.toString().replace("\r", "\\r").replace("\n", "\\n"))
        }
    }

    private companion object {
        const val TAG = "LinovelibDiagnostic"
        val LOAD_FAILURE_MARKERS = listOf("內容加載失敗", "内容加载失败")
    }
}

internal fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

private fun writeAndroidLog(level: Int, tag: String, message: String, throwable: Throwable?) {
    if (throwable == null) {
        Log.println(level, tag, message)
    } else {
        Log.e(tag, message, throwable)
    }
}
