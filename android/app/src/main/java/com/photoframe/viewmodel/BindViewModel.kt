package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoframe.data.AppPrefs
import com.photoframe.data.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class BindUiState {
    object Loading : BindUiState()
    object AlreadyBound : BindUiState()
    data class ShowQrCode(val qrToken: String) : BindUiState()
    object BindSuccess : BindUiState()
    data class Error(val message: String) : BindUiState()
}

class BindViewModel(
    private val prefs: AppPrefs,
    private val deviceRepo: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BindUiState>(BindUiState.Loading)
    val uiState: StateFlow<BindUiState> = _uiState

    private var pollJob: Job? = null

    fun checkBindingStatus() {
        if (prefs.isBound && !prefs.userToken.isNullOrEmpty()) {
            _uiState.value = BindUiState.AlreadyBound
            return
        }
        registerDevice()
    }

    private fun registerDevice() {
        viewModelScope.launch {
            try {
                // 如有已保存的 deviceId + qrToken，直接复用
                val existingDeviceId = prefs.deviceId
                val existingQrToken = prefs.qrToken
                if (!existingDeviceId.isNullOrEmpty() && !existingQrToken.isNullOrEmpty()) {
                    _uiState.value = BindUiState.ShowQrCode(existingQrToken)
                    startPolling(existingDeviceId)
                    return@launch
                }

                val result = deviceRepo.registerDevice(prefs.serverBaseUrl)
                prefs.deviceId = result.deviceId
                prefs.qrToken = result.qrToken
                _uiState.value = BindUiState.ShowQrCode(result.qrToken)
                startPolling(result.deviceId)
            } catch (e: Exception) {
                _uiState.value = BindUiState.Error(e.message ?: "注册失败")
            }
        }
    }

    private fun startPolling(deviceId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                try {
                    val status = deviceRepo.checkBindStatus(prefs.serverBaseUrl, deviceId)
                    if (status.bound && !status.userToken.isNullOrEmpty()) {
                        prefs.userToken = status.userToken
                        prefs.isBound = true
                        _uiState.value = BindUiState.BindSuccess
                        break
                    }
                } catch (_: Exception) {
                    // 轮询失败静默忽略，继续重试
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
