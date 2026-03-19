package com.photoframe.util

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.photoframe.data.AppPrefs
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

/**
 * UI 测试基类，封装 MockWebServer 的启停和 AppPrefs 的初始化。
 * 所有 UI E2E 测试继承此类，确保 API 请求全部指向本地 Mock 服务器。
 */
abstract class MockServerTestBase {
    protected val server = MockWebServer()
    protected lateinit var prefs: AppPrefs

    @Before
    open fun setUp() {
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPrefs(context)
        // 重定向所有请求到 MockWebServer
        prefs.serverBaseUrl = server.url("/").toString().trimEnd('/')
    }

    @After
    open fun tearDown() {
        server.shutdown()
    }
}
