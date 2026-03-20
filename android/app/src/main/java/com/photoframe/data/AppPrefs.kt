package com.photoframe.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用配置持久化（SharedPreferences 封装）
 */
class AppPrefs(private val context: Context) {
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

    var userToken: String?
        get() = prefs.getString("user_token", null)
        set(v) = prefs.edit().putString("user_token", v).apply()

    // 上次同步时间（ISO 8601，用于增量拉取）
    var lastSyncTime: String?
        get() = prefs.getString("last_sync_time", null)
        set(v) = prefs.edit().putString("last_sync_time", v).apply()

    // 服务器地址（默认回退到 strings.xml 硬编码值）
    var serverBaseUrl: String
        get() = prefs.getString("server_base_url", null)
            ?: context.getString(com.photoframe.R.string.server_base_url)
        set(v) = prefs.edit().putString("server_base_url", v).apply()

    // 测试模式标志（E2E 测试时设为 true，跳过沉浸式全屏等与 Espresso 冲突的行为）
    var isTestMode: Boolean
        get() = prefs.getBoolean("is_test_mode", false)
        set(v) = prefs.edit().putBoolean("is_test_mode", v).apply()
}
