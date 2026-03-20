package com.photoframe.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import com.photoframe.BindActivity
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.data.ApiClient
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Test

class BindFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        // 清除绑定状态
        prefs.isBound = false
        prefs.deviceId = null
        prefs.qrToken = null
        prefs.userToken = null
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @Test
    fun bindFlow_showsQrCode_thenNavigatesToMain() {
        // 预设响应：注册 → 未绑定 → 已绑定（按路径分发）
        enqueueForPath("/api/device/register", MockResponses.registerDevice())
        enqueueForPath("/api/device/bind-status", MockResponses.bindStatusUnbound())
        enqueueForPath("/api/device/bind-status", MockResponses.bindStatusBound())
        // goMain() 后 MainActivity 需要的响应
        enqueueForPath("/api/photos", MockResponses.photoList(mockServerUrl, count = 2))
        enqueueForPath("/api/version/latest", MockResponses.latestVersion())

        val scenario = ActivityScenario.launch(BindActivity::class.java)

        // 等待注册请求 + QR 码生成
        Thread.sleep(5_000)

        // 验证二维码显示
        onView(withId(R.id.iv_qr)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

        // 等待轮询完成
        Thread.sleep(10_000)
        Intents.intended(hasComponent(MainActivity::class.java.name))
        scenario.close()
    }

    @Test
    fun bindFlow_alreadyBound_skipsToMain() {
        prefs.isBound = true
        prefs.userToken = "existing-token"
        prefs.deviceId = "dev-test"
        ApiClient.init(mockServerUrl, "existing-token")

        // goMain() 跳转后 MainActivity 需要的响应
        enqueueForPath("/api/photos", MockResponses.photoList(mockServerUrl, count = 2))
        enqueueForPath("/api/version/latest", MockResponses.latestVersion())

        val scenario = ActivityScenario.launch(BindActivity::class.java)

        Thread.sleep(3_000)
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
        scenario.close()
    }
}
