package com.photoframe.service

import android.content.Context
import android.util.Log
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import kotlinx.coroutines.*
import java.time.Instant

/**
 * 后台轮询服务：每 60 秒从服务器拉取新照片，通过回调通知 MainActivity
 */
class PhotoSyncService(
    private val context: Context,
    private val onPhotosUpdated: (List<Photo>) -> Unit
) {
    private val TAG = "PhotoSyncService"
    private val prefs = AppPrefs(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var syncJob: Job? = null

    fun start() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                try {
                    fetchNewPhotos()
                } catch (e: Exception) {
                    Log.w(TAG, "同步失败: ${e.message}")
                }
                delay(60_000L) // 60 秒轮询
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
    }

    /** 当持有方（如 Activity）销毁时调用，释放协程资源 */
    fun destroy() {
        scope.cancel()
    }

    private suspend fun fetchNewPhotos() {
        val deviceId = prefs.deviceId ?: return
        val since = prefs.lastSyncTime

        val response = ApiClient.service.listPhotos(
            deviceId = deviceId,
            since = since
        )

        if (response.photos.isNotEmpty()) {
            val photos = response.photos.map {
                Photo(
                    id = it.id,
                    url = it.url,
                    takenAt = it.takenAt,
                    uploaderName = it.uploaderName,
                    uploadedAt = it.uploadedAt
                )
            }
            // 更新最后同步时间为最新一张的 uploadedAt
            prefs.lastSyncTime = photos.last().uploadedAt
            Log.d(TAG, "拉取到 ${photos.size} 张新照片")
            withContext(Dispatchers.Main) {
                onPhotosUpdated(photos)
            }
        }
    }
}
