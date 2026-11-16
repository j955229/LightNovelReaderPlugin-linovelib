package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 顯示嗶哩輕小說的搜尋驗證碼，等待使用者輸入。使用者取消時回傳 null。
 */
internal object LinovelibCaptchaPrompt {
    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun requestCode(
        context: Context,
        keyword: String,
        errorMessage: String?,
        loadImage: suspend (refresh: Boolean) -> ByteArray
    ): String? {
        val firstImage = loadImage(false)
        val activity = (context as? Activity)?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: LinovelibActivityTracker.current()
            ?: throw LinovelibSearchException("搜尋需要輸入驗證碼，但無法顯示驗證視窗")

        return suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                if (!continuation.isActive) return@post
                if (activity.isFinishing || activity.isDestroyed) {
                    continuation.resume(null)
                    return@post
                }
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()

                val message = TextView(activity).apply {
                    text = buildString {
                        append("嗶哩輕小說要求輸入圖片中的 4 碼驗證碼，通過後 30 分鐘內搜尋免驗證。")
                        append("\n搜尋關鍵字：")
                        append(keyword)
                        append("\n點擊圖片可更換驗證碼。")
                    }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                }
                val errorText = TextView(activity).apply {
                    text = errorMessage.orEmpty()
                    setTextColor(Color.rgb(0xB5, 0x23, 0x23))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    visibility = if (errorMessage.isNullOrEmpty()) TextView.GONE else TextView.VISIBLE
                }
                val image = ImageView(activity).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.WHITE)
                    contentDescription = "驗證碼圖片"
                    setCaptchaImage(firstImage)
                }
                val input = EditText(activity).apply {
                    hint = "驗證碼"
                    isSingleLine = true
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    filters = arrayOf(InputFilter.LengthFilter(4))
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    letterSpacing = 0.2f
                }
                val layout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(12), dp(24), 0)
                    addView(message)
                    addView(errorText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
                    addView(
                        image,
                        LinearLayout.LayoutParams(dp(183), dp(66)).apply {
                            topMargin = dp(12)
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    )
                    addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
                }

                var finished = false
                fun finish(result: String?) {
                    if (finished) return
                    finished = true
                    if (continuation.isActive) continuation.resume(result)
                }

                image.setOnClickListener {
                    imageScope.launch {
                        val bytes = runCatching { loadImage(true) }.getOrNull() ?: return@launch
                        mainHandler.post { image.setCaptchaImage(bytes) }
                    }
                }

                val dialog = AlertDialog.Builder(activity)
                    .setTitle("搜尋驗證")
                    .setView(layout)
                    .setCancelable(true)
                    .setPositiveButton("驗證並搜尋", null)
                    .setNegativeButton("取消") { _, _ -> finish(null) }
                    .setOnCancelListener { finish(null) }
                    .setOnDismissListener { finish(null) }
                    .create()

                fun submit() {
                    val code = input.text.toString().trim()
                    if (!code.matches(CODE_PATTERN)) {
                        errorText.text = "請輸入 4 碼英文或數字"
                        errorText.visibility = TextView.VISIBLE
                        return
                    }
                    finish(code)
                    dialog.dismiss()
                }

                input.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() }
                    input.requestFocus()
                    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                }
                continuation.invokeOnCancellation {
                    mainHandler.post { if (dialog.isShowing) dialog.dismiss() }
                }
                dialog.show()
            }
        }
    }

    private fun ImageView.setCaptchaImage(bytes: ByteArray) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::setImageBitmap)
    }

    private val CODE_PATTERN = Regex("[A-Za-z0-9]{4}")
}
