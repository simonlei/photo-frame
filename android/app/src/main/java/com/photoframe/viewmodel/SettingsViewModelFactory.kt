package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.photoframe.data.AppPrefs

class SettingsViewModelFactory(private val prefs: AppPrefs) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(prefs) as T
    }
}
