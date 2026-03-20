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
        // 预设响应
        server.enqueue(MockResponses.registerDevice())
        server.enqueue(MockResponses.bindStatusUnbound())
        server.enqueue(MockResponses.bindStatusBound())

        // setUp() 之后手动启动 Activity，确保 prefs 已指向 MockServer
        val scenario = ActivityScenario.launch(BindActivity::class.java)

        // 验证二维码显示
        onView(withId(R.id.iv_qr)).check(matches(isDisplayed()))

        // TODO: 替换为 IdlingResource 等待轮询完成，避免 Thread.sleep 导致 flaky
        Thread.sleep(5_000) // 临时方案：等待轮询周期
        Intents.intended(hasComponent(MainActivity::class.java.name))
        scenario.close()
    }

    @Test
    fun bindFlow_alreadyBound_skipsToMain() {
        prefs.isBound = true
        prefs.userToken = "existing-token"
        prefs.deviceId = "dev-test"

        val scenario = ActivityScenario.launch(BindActivity::class.java)

        // 应直接跳转，不发网络请求
        Thread.sleep(1_000)
        Intents.intended(hasComponent(MainActivity::class.java.name))
        scenario.close()
    }
}
