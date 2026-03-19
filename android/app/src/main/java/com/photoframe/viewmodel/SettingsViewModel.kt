package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import com.photoframe.data.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SettingsUiState(
    val serverBaseUrl: String = "",
    val slideDurationSec: Int = 15,
    val playMode: String = "sequential",
    val transitionEffect: String = "fade",
    val showPhotoInfo: Boolean = true,
    val nightModeEnabled: Boolean = false,
    val nightModeStartHour: Int = 22,
    val nightModeStartMinute: Int = 0,
    val nightModeEndHour: Int = 8,
    val nightModeEndMinute: Int = 0,
    val deviceId: String? = null
)

sealed class SettingsSaveResult {
    object Success : SettingsSaveResult()
    data class ServerUrlChanged(val newUrl: String) : SettingsSaveResult()
    data class ValidationError(val message: String) : SettingsSaveResult()
}

class SettingsViewModel(private val prefs: AppPrefs) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    fun loadSettings() {
        _uiState.value = SettingsUiState(
            serverBaseUrl = prefs.serverBaseUrl,
            slideDurationSec = prefs.slideDurationSec,
            playMode = prefs.playMode,
            transitionEffect = prefs.transitionEffect,
            showPhotoInfo = prefs.showPhotoInfo,
            nightModeEnabled = prefs.nightModeEnabled,
            nightModeStartHour = prefs.nightModeStartHour,
            nightModeStartMinute = prefs.nightModeStartMinute,
            nightModeEndHour = prefs.nightModeEndHour,
            nightModeEndMinute = prefs.nightModeEndMinute,
            deviceId = prefs.deviceId
        )
    }

    fun saveSettings(newState: SettingsUiState): SettingsSaveResult {
        // 验证 URL 格式
        val url = newState.serverBaseUrl.trim()
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return SettingsSaveResult.ValidationError("服务器地址格式不正确，请以 http:// 或 https:// 开头")
        }

        val serverChanged = url != prefs.serverBaseUrl

        prefs.serverBaseUrl = url
        prefs.slideDurationSec = newState.slideDurationSec.coerceIn(5, 300)
        prefs.playMode = newState.playMode
        prefs.transitionEffect = newState.transitionEffect
        prefs.showPhotoInfo = newState.showPhotoInfo
        prefs.nightModeEnabled = newState.nightModeEnabled
        prefs.nightModeStartHour = newState.nightModeStartHour
        prefs.nightModeStartMinute = newState.nightModeStartMinute
        prefs.nightModeEndHour = newState.nightModeEndHour
        prefs.nightModeEndMinute = newState.nightModeEndMinute

        if (serverChanged) {
            prefs.isBound = false
            prefs.userToken = null
            prefs.deviceId = null
            prefs.qrToken = null
            prefs.lastSyncTime = null
            return SettingsSaveResult.ServerUrlChanged(url)
        }
        return SettingsSaveResult.Success
    }
}
