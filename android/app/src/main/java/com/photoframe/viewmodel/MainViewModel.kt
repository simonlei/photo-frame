package com.photoframe.viewmodel

import androidx.lifecycle.ViewModel
import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MainUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val isNightMode: Boolean = false,
    val showPhotoInfo: Boolean = true,
    val slideDurationMs: Long = 15_000L,
    val transitionEffect: String = "fade"
)

class MainViewModel(private val prefs: AppPrefs) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            showPhotoInfo = prefs.showPhotoInfo,
            slideDurationMs = prefs.slideDurationSec * 1_000L,
            transitionEffect = prefs.transitionEffect
        )
    }

    /**
     * 收到新照片时调用（来自 PhotoSyncService 回调）
     * 去重 + 按 playMode 排序
     */
    fun onNewPhotos(newPhotos: List<Photo>) {
        val current = _uiState.value.photos
        val existingIds = current.map { it.id }.toSet()
        val fresh = newPhotos.filter { it.id !in existingIds }
        if (fresh.isNotEmpty()) {
            val updated = current + fresh
            val sorted = if (prefs.playMode == "random") updated.shuffled() else updated
            _uiState.value = _uiState.value.copy(photos = sorted)
        }
    }

    /**
     * 计算下一页索引（循环播放）
     */
    fun nextPageIndex(): Int {
        val size = _uiState.value.photos.size
        if (size == 0) return 0
        val next = (_uiState.value.currentIndex + 1) % size
        _uiState.value = _uiState.value.copy(currentIndex = next)
        return next
    }

    fun setCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
    }

    fun setNightMode(isNight: Boolean) {
        _uiState.value = _uiState.value.copy(isNightMode = isNight)
    }
}
