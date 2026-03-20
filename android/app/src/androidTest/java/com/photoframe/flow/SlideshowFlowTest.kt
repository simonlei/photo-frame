package com.photoframe.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.SettingsActivity
import com.photoframe.data.ApiClient
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
        ApiClient.init(mockServerUrl, "test-token")
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    /**
     * 准备 MainActivity 启动所需的基本 Mock 响应（按路径分发）。
     */
    private fun enqueueMainActivityResponses(photoCount: Int = 3) {
        enqueueForPath("/api/photos", MockResponses.photoList(mockServerUrl, count = photoCount))
        enqueueForPath("/api/version/latest", MockResponses.latestVersion())
    }

    @Test
    fun slideshow_displaysPhotosFromServer() {
        enqueueMainActivityResponses(photoCount = 3)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3_000)
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
        scenario.close()
    }

    @Test
    fun slideshow_autoAdvancesPage() {
        prefs.slideDurationSec = 3
        enqueueMainActivityResponses(photoCount = 3)

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // 等待照片加载 + 至少两次翻页周期（3s × 2 + buffer）
        Thread.sleep(12_000)

        scenario.onActivity { activity ->
            val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.view_pager)
            assert(viewPager.currentItem > 0) { "Auto-advance should have moved past first page, but currentItem=${viewPager.currentItem}" }
        }
        scenario.close()
    }

    @Test
    fun slideshow_tapOpensSettings() {
        enqueueMainActivityResponses(photoCount = 2)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3_000)

        // MainActivity 使用 GestureDetector.onSingleTapConfirmed 进入设置页
        onView(withId(R.id.view_pager)).perform(click())
        Thread.sleep(500)
        Intents.intended(hasComponent(SettingsActivity::class.java.name))
        scenario.close()
    }
}
