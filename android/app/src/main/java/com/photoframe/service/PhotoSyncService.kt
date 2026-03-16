package com.photoframe.service

import android.content.Context
import android.util.Log
import com.photoframe.R
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import kotlinx.coroutines.*

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
            // 启动时先全量拉取，确保所有照片都加载
            try {
                Log.d(TAG, "首次全量拉取照片")
                fetchPhotos(since = null)
            } catch (e: Exception) {
                Log.w(TAG, "全量拉取失败: ${e.message}")
            }
            // 之后每 60 秒增量拉取新照片
            while (isActive) {
                delay(60_000L)
                try {
                    val since = prefs.lastSyncTime
                    Log.d(TAG, "增量拉取照片, since=$since")
                    fetchPhotos(since = since)
                } catch (e: Exception) {
                    Log.w(TAG, "增量拉取失败: ${e.message}")
                }
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

    private suspend fun fetchPhotos(since: String?) {
        val deviceId = prefs.deviceId ?: return
        val baseUrl = context.getString(R.string.server_base_url).trimEnd('/')

        Log.d(TAG, "请求照片列表: deviceId=$deviceId, since=$since")
        val response = ApiClient.service.listPhotos(
            deviceId = deviceId,
            since = since
        )

        Log.d(TAG, "服务端返回 ${response.photos.size} 张照片")
        if (response.photos.isNotEmpty()) {
            val photos = response.photos.map {
                // 服务端可能返回相对路径（如 /uploads/xxx.jpg），需要补全为绝对 URL
                val fullUrl = if (it.url.startsWith("http")) it.url else "$baseUrl${it.url}"
                Photo(
                    id = it.id,
                    url = fullUrl,
                    takenAt = it.takenAt,
                    uploadedAt = it.uploadedAt,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    locationAddress = it.locationAddress,
                    cameraMake = it.cameraMake,
                    cameraModel = it.cameraModel,
                    uploaderName = it.uploaderName
                )
            }
            // 更新最后同步时间为最新一张的 uploadedAt
            prefs.lastSyncTime = photos.last().uploadedAt
            Log.d(TAG, "拉取到 ${photos.size} 张照片, 更新 lastSyncTime=${prefs.lastSyncTime}")
            withContext(Dispatchers.Main) {
                onPhotosUpdated(photos)
            }
        }
    }
}
