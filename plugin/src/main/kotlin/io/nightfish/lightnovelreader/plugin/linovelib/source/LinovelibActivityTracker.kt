package io.nightfish.lightnovelreader.plugin.linovelib.source

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * 追蹤宿主目前在前景的 Activity，用來顯示搜尋驗證碼對話框。
 */
internal object LinovelibActivityTracker {
    @Volatile
    private var currentActivity: WeakReference<Activity>? = null

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            if (context is Activity) currentActivity = WeakReference(context)
            val application = context.applicationContext as? Application ?: return
            application.registerActivityLifecycleCallbacks(Callbacks)
            installed = true
        }
    }

    fun current(): Activity? =
        currentActivity?.get()?.takeIf(::isUsable) ?: findResumedActivity()?.also {
            currentActivity = WeakReference(it)
        }

    private fun isUsable(activity: Activity): Boolean =
        !activity.isFinishing && !activity.isDestroyed

    @Suppress("PrivateApi", "DiscouragedPrivateApi")
    private fun findResumedActivity(): Activity? = runCatching {
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
        val activitiesField = activityThreadClass.getDeclaredField("mActivities").apply { isAccessible = true }
        val activities = activitiesField.get(activityThread) as? Map<*, *> ?: return null
        var fallback: Activity? = null
        for (record in activities.values) {
            if (record == null) continue
            val recordClass = record.javaClass
            val activity = recordClass.getDeclaredField("activity")
                .apply { isAccessible = true }
                .get(record) as? Activity ?: continue
            if (!isUsable(activity)) continue
            val paused = runCatching {
                recordClass.getDeclaredField("paused").apply { isAccessible = true }.getBoolean(record)
            }.getOrDefault(false)
            if (!paused) return activity
            fallback = activity
        }
        fallback
    }.getOrNull()

    private object Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            currentActivity = WeakReference(activity)
        }

        override fun onActivityStarted(activity: Activity) {
            currentActivity = WeakReference(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            currentActivity = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivity?.get() === activity) currentActivity = null
        }
    }
}
