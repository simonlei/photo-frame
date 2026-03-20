package com.photoframe.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.*
import com.photoframe.BindActivity
import com.photoframe.MainActivity
import com.photoframe.R
import com.photoframe.data.ApiClient
import com.photoframe.util.MockResponses
import com.photoframe.util.MockServerTestBase
import org.junit.Test

class ErrorFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        Intents.init()
    }

    override fun tearDown() {
        Intents.release()
        super.tearDown()
    }

    @Test
    fun serverError_showsErrorState() {
        // 清除绑定让 app 走注册流程
        prefs.isBound = false
        prefs.deviceId = null
        prefs.qrToken = null
        prefs.userToken = null

        enqueueForPath("/api/device/register", MockResponses.serverError())

        val scenario = ActivityScenario.launch(BindActivity::class.java)
        Thread.sleep(3_000)
        onView(withId(R.id.tv_hint)).check(matches(isDisplayed()))
        scenario.close()
    }

    @Test
    fun emptyPhotoList_showsEmptyState() {
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"
        ApiClient.init(mockServerUrl, "test-token")

        enqueueForPath("/api/photos", MockResponses.emptyPhotoList())
        enqueueForPath("/api/version/latest", MockResponses.latestVersion())

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(3_000)
        onView(withId(R.id.view_pager)).check(matches(isDisplayed()))
        scenario.close()
    }
}
