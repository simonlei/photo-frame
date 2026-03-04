package com.photoframe

import android.app.Application
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs

/**
 * Application 入口：在所有组件初始化之前完成全局配置，
 * 确保 ApiClient 在首次访问前已使用正确的服务器地址初始化。
 */
class PhotoFrameApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 必须在 Application.onCreate() 中初始化，保证所有 Activity/Service/Receiver
        // 访问 ApiClient.service 时已完成 Retrofit 构建，避免 lazy 初始化竞态问题
        val serverUrl = getString(R.string.server_base_url)
        ApiClient.init(serverUrl)
    }
}
