package com.photoframe.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用配置持久化（SharedPreferences 封装）
 */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("photo_frame_prefs", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(v) = prefs.edit().putString("device_id", v).apply()

    var qrToken: String?
        get() = prefs.getString("qr_token", null)
        set(v) = prefs.edit().putString("qr_token", v).apply()

    var isBound: Boolean
        get() = prefs.getBoolean("is_bound", false)
        set(v) = prefs.edit().putBoolean("is_bound", v).apply()

    // 展示设置
    var slideDurationSec: Int
        get() = prefs.getInt("slide_duration_sec", 15)
        set(v) = prefs.edit().putInt("slide_duration_sec", v).apply()

    var playMode: String  // "sequential" | "random"
        get() = prefs.getString("play_mode", "sequential") ?: "sequential"
        set(v) = prefs.edit().putString("play_mode", v).apply()

    var transitionEffect: String  // "fade" | "slide" | "zoom"
        get() = prefs.getString("transition_effect", "fade") ?: "fade"
        set(v) = prefs.edit().putString("transition_effect", v).apply()

    var showPhotoInfo: Boolean
        get() = prefs.getBoolean("show_photo_info", true)
        set(v) = prefs.edit().putBoolean("show_photo_info", v).apply()

    // 定时黑屏
    var nightModeEnabled: Boolean
        get() = prefs.getBoolean("night_mode_enabled", false)
        set(v) = prefs.edit().putBoolean("night_mode_enabled", v).apply()

    var nightModeStartHour: Int
        get() = prefs.getInt("night_start_hour", 22)
        set(v) = prefs.edit().putInt("night_start_hour", v).apply()

    var nightModeStartMinute: Int
        get() = prefs.getInt("night_start_minute", 0)
        set(v) = prefs.edit().putInt("night_start_minute", v).apply()

    var nightModeEndHour: Int
        get() = prefs.getInt("night_end_hour", 8)
        set(v) = prefs.edit().putInt("night_end_hour", v).apply()

    var nightModeEndMinute: Int
        get() = prefs.getInt("night_end_minute", 0)
        set(v) = prefs.edit().putInt("night_end_minute", v).apply()

    // 上次同步时间（ISO 8601，用于增量拉取）
    var lastSyncTime: String?
        get() = prefs.getString("last_sync_time", null)
        set(v) = prefs.edit().putString("last_sync_time", v).apply()
}
