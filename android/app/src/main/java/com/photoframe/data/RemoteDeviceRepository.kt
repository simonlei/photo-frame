package com.photoframe.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * DeviceRepository 的生产实现，通过 OkHttpClient 直接调用设备注册和绑定状态 API
 */
class RemoteDeviceRepository(private val httpClient: OkHttpClient) : DeviceRepository {

    override suspend fun registerDevice(serverBaseUrl: String): DeviceRegisterResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$serverBaseUrl/api/device/register")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: throw Exception("响应体为空")
                if (!resp.isSuccessful) throw Exception("注册失败: ${resp.code}")
                val body = JSONObject(bodyStr)
                DeviceRegisterResult(
                    deviceId = body.getString("device_id"),
                    qrToken = body.getString("qr_token")
                )
            }
        }

    override suspend fun checkBindStatus(serverBaseUrl: String, deviceId: String): BindStatusResult =
        withContext(Dispatchers.IO) {
            val url = "$serverBaseUrl/api/device/bind-status?device_id=$deviceId"
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string()
                if (!resp.isSuccessful || bodyStr.isNullOrEmpty()) {
                    return@withContext BindStatusResult(bound = false, userToken = null)
                }
                val body = JSONObject(bodyStr)
                BindStatusResult(
                    bound = body.optBoolean("bound", false),
                    userToken = body.optString("user_token").takeIf { it.isNotEmpty() }
                )
            }
        }
}
