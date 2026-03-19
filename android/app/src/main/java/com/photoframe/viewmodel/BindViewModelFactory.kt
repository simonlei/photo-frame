package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.photoframe.data.AppPrefs
import com.photoframe.data.DeviceRepository

class BindViewModelFactory(
    private val prefs: AppPrefs,
    private val deviceRepo: DeviceRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // 生产环境不传 externalScope，使用默认的 viewModelScope
        return BindViewModel(prefs, deviceRepo) as T
    }
}
