package com.photoframe.flow

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.photoframe.R
import com.photoframe.SettingsActivity
import com.photoframe.util.MockServerTestBase
import org.junit.Assert.*
import org.junit.Test

class SettingsFlowTest : MockServerTestBase() {

    override fun setUp() {
        super.setUp()
        prefs.isBound = true
        prefs.userToken = "test-token"
        prefs.deviceId = "dev-test"
        prefs.slideDurationSec = 15
        prefs.playMode = "sequential"
        prefs.transitionEffect = "fade"
    }

    @Test
    fun settings_displayCurrentValues() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        // 验证当前设置值正确渲染到 UI
        onView(withId(R.id.et_server_url)).check(
            matches(withText(prefs.serverBaseUrl))
        )
        scenario.close()
    }

    @Test
    fun settings_changePlayMode_persistsToPrefs() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        // 点击随机播放选项
        onView(withId(R.id.rb_random)).perform(click())
        // 点击保存
        onView(withId(R.id.btn_save)).perform(click())
        // 验证 AppPrefs 已更新
        assertEquals("random", prefs.playMode)
        scenario.close()
    }

    @Test
    fun settings_changeServerUrl_clearsBind() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        // 修改服务器地址
        onView(withId(R.id.et_server_url)).perform(
            clearText(),
            typeText("http://new-server.com"),
            closeSoftKeyboard()
        )
        onView(withId(R.id.btn_save)).perform(click())
        // 验证绑定状态已清除
        assertFalse(prefs.isBound)
        assertNull(prefs.userToken)
        assertNull(prefs.deviceId)
        scenario.close()
    }
}
