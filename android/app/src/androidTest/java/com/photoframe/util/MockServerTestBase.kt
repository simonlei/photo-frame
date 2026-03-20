package com.photoframe.util

import androidx.test.platform.app.InstrumentationRegistry
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * UI 测试基类，封装 MockWebServer 的启停和 AppPrefs 的初始化。
 * 所有 UI E2E 测试继承此类，确保 API 请求全部指向本地 Mock 服务器。
 *
 * 使用基于路径前缀的 Dispatcher，解决请求顺序不确定导致的响应错配问题。
 * 通过 [setResponse] 预设路径→响应映射，[enqueueForPath] 为同一路径排多个响应。
 */
abstract class MockServerTestBase {
    protected val server = MockWebServer()
    protected lateinit var prefs: AppPrefs
    protected lateinit var mockServerUrl: String

    /** 路径前缀 → 响应队列 */
    private val pathResponses = ConcurrentHashMap<String, ConcurrentLinkedQueue<MockResponse>>()

    /** 默认 fallback 响应（404） */
    private val fallbackResponse = MockResponse().setResponseCode(404).setBody("No mock response for this path")

    @Before
    open fun setUp() {
        server.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = AppPrefs(context)
        mockServerUrl = server.url("/").toString().trimEnd('/')
        // 重定向所有请求到 MockWebServer
        prefs.serverBaseUrl = mockServerUrl
        // 启用测试模式（跳过全屏沉浸式等与 Espresso 冲突的行为）
        prefs.isTestMode = true
        // 初始化 ApiClient（Retrofit service）指向 MockServer，
        // 这样 MainActivity 中的 ApiClient.service 调用能正确路由到 MockWebServer
        ApiClient.init(mockServerUrl, prefs.userToken)

        // 设置基于路径的 Dispatcher
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return fallbackResponse
                // 按路径前缀匹配（最长前缀优先）
                val matchedKey = pathResponses.keys
                    .filter { path.startsWith(it) || path.split("?")[0] == it }
                    .maxByOrNull { it.length }
                if (matchedKey != null) {
                    val queue = pathResponses[matchedKey]
                    val response = queue?.poll()
                    if (response != null) return response
                    // 队列空了则返回 fallback
                }
                return fallbackResponse
            }
        }
    }

    @After
    open fun tearDown() {
        prefs.isTestMode = false
        pathResponses.clear()
        server.shutdown()
    }

    /**
     * 为指定路径设置 Mock 响应，可多次调用同一路径以排队多个响应（按 FIFO 消费）。
     */
    protected fun enqueueForPath(pathPrefix: String, response: MockResponse) {
        pathResponses.getOrPut(pathPrefix) { ConcurrentLinkedQueue() }.add(response)
    }
}
