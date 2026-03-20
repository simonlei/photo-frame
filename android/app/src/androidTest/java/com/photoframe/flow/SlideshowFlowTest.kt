package com.photoframe.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.SettingsActivity
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Test

class SlideshowFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        // 预设已绑定状态
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @Test
    fun slideshow_displaysPhotosFromServer() {
        // MockWebServer 返回照片列表
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 3))

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 等待网络请求完成
        Thread.sleep(2_000)

        // 验证 ViewPager2 显示照片（照片容器可见）
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
        scenario.close()
    }

    @Test
    fun slideshow_autoAdvancesPage() {
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 3))
        prefs.slideDurationSec = 3 // 设置较短的翻页间隔便于测试

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // TODO: 替换为 IdlingResource 等待自动翻页事件
        Thread.sleep(8_000) // 等待至少两次翻页周期（3s + buffer）

        // 验证当前页不再是第 0 页（即发生了翻页）
        scenario.onActivity { activity ->
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)
            assert(viewPager.currentItem > 0) { "Auto-advance should have moved past first page" }
        }
        scenario.close()
    }

    @Test
    fun slideshow_longPressOpensSettings() {
        server.enqueue(MockResponses.photoList(server.url("/").toString(), count = 2))

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 等待照片加载
        Thread.sleep(2_000)

        // 长按手势进入设置页
        onView(withId(R.id.view_pager)).perform(longClick())
        Intents.intended(hasComponent(SettingsActivity::class.java.name))
        scenario.close()
    }
}
