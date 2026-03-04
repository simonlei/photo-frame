package com.photoframe.service

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.photoframe.data.AppPrefs
import java.util.Calendar

/**
 * 定时黑屏/亮屏调度器
 * 每分钟检查一次当前时间是否在黑屏时段内，相应调整亮度。
 * 使用 Activity 窗口亮度（无需系统权限）。
 */
class ScreenScheduler(private val activity: Activity) {
    private val prefs = AppPrefs(activity)
    private val handler = Handler(Looper.getMainLooper())

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
        val curMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val nightStart = prefs.nightModeStartHour * 60 + prefs.nightModeStartMinute
        val nightEnd = prefs.nightModeEndHour * 60 + prefs.nightModeEndMinute

        val isNightTime = if (nightStart <= nightEnd) {
            curMinutes in nightStart until nightEnd
        } else {
            // 跨午夜情况，如 22:00 ~ 08:00
            curMinutes >= nightStart || curMinutes < nightEnd
        }

        setBrightness(if (isNightTime) 0.01f else -1f)
    }

    fun setBrightness(brightness: Float) {
        val params = activity.window.attributes
        params.screenBrightness = brightness
        activity.window.attributes = params
    }
}
