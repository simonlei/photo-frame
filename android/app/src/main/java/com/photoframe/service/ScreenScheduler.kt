package com.photoframe.service

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.photoframe.data.AppPrefs
import com.photoframe.util.isInNightPeriod
import java.lang.ref.WeakReference
import java.util.Calendar

/**
 * 定时黑屏/亮屏调度器
 * 每分钟检查一次当前时间是否在黑屏时段内，相应调整亮度。
 * 使用 Activity 窗口亮度（无需系统权限）。
 */
class ScreenScheduler(private val activity: Activity) {
    private val prefs = AppPrefs(activity)
    private val handler = Handler(Looper.getMainLooper())

    /** 夜间模式状态变化回调，使用弱引用避免持有 Activity 导致内存泄漏 */
    var nightModeListener: WeakReference<((isNight: Boolean) -> Unit)>? = null

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (prefs.nightModeEnabled) {
                updateBrightness()
            }
            handler.postDelayed(this, 60_000L)
        }
    }

    fun start() {
        handler.post(checkRunnable)
    }

    fun stop() {
        handler.removeCallbacks(checkRunnable)
    }

    private fun updateBrightness() {
        val cal = Calendar.getInstance()
        val isNightTime = isInNightPeriod(
            currentHour = cal.get(Calendar.HOUR_OF_DAY),
            currentMinute = cal.get(Calendar.MINUTE),
            startHour = prefs.nightModeStartHour,
            startMinute = prefs.nightModeStartMinute,
            endHour = prefs.nightModeEndHour,
            endMinute = prefs.nightModeEndMinute
        )

        setBrightness(if (isNightTime) 0.01f else -1f)
        nightModeListener?.get()?.invoke(isNightTime)
    }

    fun setBrightness(brightness: Float) {
        val params = activity.window.attributes
        params.screenBrightness = brightness
        activity.window.attributes = params
    }
}
