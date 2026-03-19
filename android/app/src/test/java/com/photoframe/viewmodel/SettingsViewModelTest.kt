package com.photoframe.viewmodel

import com.photoframe.data.AppPrefs
import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class SettingsViewModelTest {

    private val prefs = mockk<AppPrefs>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setup() {
        every { prefs.serverBaseUrl } returns "http://example.com"
        every { prefs.slideDurationSec } returns 15
        every { prefs.playMode } returns "sequential"
        every { prefs.transitionEffect } returns "fade"
        every { prefs.showPhotoInfo } returns true
        every { prefs.nightModeEnabled } returns false
        every { prefs.nightModeStartHour } returns 22
        every { prefs.nightModeStartMinute } returns 0
        every { prefs.nightModeEndHour } returns 8
        every { prefs.nightModeEndMinute } returns 0
        every { prefs.deviceId } returns "dev-123"
        viewModel = SettingsViewModel(prefs)
    }

    @Test
    fun `loadSettings populates uiState from prefs`() {
        viewModel.loadSettings()
        val state = viewModel.uiState.value
        assertEquals("http://example.com", state.serverBaseUrl)
        assertEquals(15, state.slideDurationSec)
        assertEquals("sequential", state.playMode)
        assertEquals("fade", state.transitionEffect)
        assertTrue(state.showPhotoInfo)
        assertFalse(state.nightModeEnabled)
        assertEquals(22, state.nightModeStartHour)
        assertEquals(0, state.nightModeStartMinute)
        assertEquals(8, state.nightModeEndHour)
        assertEquals(0, state.nightModeEndMinute)
        assertEquals("dev-123", state.deviceId)
    }

    @Test
    fun `saveSettings without server change returns Success`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            slideDurationSec = 10,
            playMode = "random",
            transitionEffect = "zoom",
            showPhotoInfo = false,
            nightModeEnabled = true,
            nightModeStartHour = 23,
            nightModeStartMinute = 30,
            nightModeEndHour = 7,
            nightModeEndMinute = 0
        )
        val result = viewModel.saveSettings(newState)
        assertTrue(result is SettingsSaveResult.Success)
        verify { prefs.slideDurationSec = 10 }
        verify { prefs.playMode = "random" }
        verify { prefs.transitionEffect = "zoom" }
        verify { prefs.showPhotoInfo = false }
    }

    @Test
    fun `saveSettings with server change clears binding and returns ServerUrlChanged`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://new-server.com",
            slideDurationSec = 15,
            playMode = "sequential"
        )
        val result = viewModel.saveSettings(newState)
        assertTrue(result is SettingsSaveResult.ServerUrlChanged)
        assertEquals("http://new-server.com", (result as SettingsSaveResult.ServerUrlChanged).newUrl)
        verify { prefs.isBound = false }
        verify { prefs.userToken = null }
        verify { prefs.deviceId = null }
        verify { prefs.qrToken = null }
        verify { prefs.lastSyncTime = null }
    }

    @Test
    fun `saveSettings persists night mode schedule`() {
        val newState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            nightModeEnabled = true,
            nightModeStartHour = 23,
            nightModeStartMinute = 30,
            nightModeEndHour = 7,
            nightModeEndMinute = 0
        )
        viewModel.saveSettings(newState)
        verify { prefs.nightModeEnabled = true }
        verify { prefs.nightModeStartHour = 23 }
        verify { prefs.nightModeStartMinute = 30 }
        verify { prefs.nightModeEndHour = 7 }
        verify { prefs.nightModeEndMinute = 0 }
    }

    @Test
    fun `saveSettings clamps duration to valid range`() {
        // 小于 5 的值应被 clamp 到 5
        val lowState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            slideDurationSec = 1
        )
        viewModel.saveSettings(lowState)
        verify { prefs.slideDurationSec = 5 }

        // 大于 300 的值应被 clamp 到 300
        val highState = SettingsUiState(
            serverBaseUrl = "http://example.com",
            slideDurationSec = 999
        )
        viewModel.saveSettings(highState)
        verify { prefs.slideDurationSec = 300 }
    }

    @Test
    fun `saveSettings with invalid url returns ValidationError`() {
        val noProtocol = SettingsUiState(serverBaseUrl = "example.com")
        val result1 = viewModel.saveSettings(noProtocol)
        assertTrue(result1 is SettingsSaveResult.ValidationError)

        val empty = SettingsUiState(serverBaseUrl = "")
        val result2 = viewModel.saveSettings(empty)
        assertTrue(result2 is SettingsSaveResult.ValidationError)

        val whitespace = SettingsUiState(serverBaseUrl = "   ")
        val result3 = viewModel.saveSettings(whitespace)
        assertTrue(result3 is SettingsSaveResult.ValidationError)
    }

    @Test
    fun `saveSettings trims whitespace from url`() {
        val newState = SettingsUiState(
            serverBaseUrl = "  http://example.com  ",
            slideDurationSec = 15
        )
        val result = viewModel.saveSettings(newState)
        assertTrue(result is SettingsSaveResult.Success)
        verify { prefs.serverBaseUrl = "http://example.com" }
    }
}
