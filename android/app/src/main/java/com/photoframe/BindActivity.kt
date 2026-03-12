package com.photoframe

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.util.QrCodeHelper
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 首次启动：注册设备并显示二维码，等待用户扫码绑定
 */
class BindActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var ivQr: ImageView
    private lateinit var tvHint: TextView

    private val http = OkHttpClient()
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPrefs(this)

        // 已绑定且 token 有效，直接进主界面
        if (prefs.isBound && prefs.deviceId != null && prefs.userToken != null) {
            goMain()
            return
        }

        setContentView(R.layout.activity_bind)
        ivQr = findViewById(R.id.iv_qr)
        tvHint = findViewById(R.id.tv_hint)

        // 若因 Token 过期被跳转，先提示用户
        if (intent.getStringExtra("reason") == "expired") {
            Toast.makeText(this, "登录已过期，请重新绑定相框", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            registerDevice()
        }
    }

    private suspend fun registerDevice() = withContext(Dispatchers.IO) {
        // 已有 deviceId 和 qrToken，直接复用，无需重新注册
        val existingDeviceId = prefs.deviceId
        val existingQrToken = prefs.qrToken
        if (!existingDeviceId.isNullOrBlank() && !existingQrToken.isNullOrBlank()) {
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeHelper.generateBitmap(existingQrToken)
            }
            withContext(Dispatchers.Main) {
                bitmap?.let {
                    ivQr.setImageBitmap(it)
                    ivQr.visibility = View.VISIBLE
                }
                tvHint.text = "用微信扫码绑定相框"
                startPollingBind()
            }
            return@withContext
        }

        try {
            val baseUrl = AppPrefs(this@BindActivity).serverBaseUrl
            val request = Request.Builder()
                .url("$baseUrl/api/device/register")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            // use{} 确保 response 被正确关闭
            http.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: throw Exception("响应体为空")
                val body = JSONObject(bodyStr)
                val deviceId = body.getString("device_id")
                val qrToken = body.getString("qr_token")

                prefs.deviceId = deviceId
                prefs.qrToken = qrToken

                val bitmap = withContext(Dispatchers.Default) {
                    QrCodeHelper.generateBitmap(qrToken)
                }
                withContext(Dispatchers.Main) {
                    bitmap?.let {
                        ivQr.setImageBitmap(it)
                        ivQr.visibility = View.VISIBLE
                    }
                    tvHint.text = "用微信扫码绑定相框"
                    startPollingBind()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                tvHint.text = "连接服务器失败，请检查网络后重试"
            }
        }
    }

    /** 每 3 秒轮询一次，检查是否已有用户绑定 */
    private fun startPollingBind() {
        pollJob = lifecycleScope.launch(Dispatchers.IO) {
            val deviceId = prefs.deviceId ?: return@launch
            val baseUrl = AppPrefs(this@BindActivity).serverBaseUrl
            while (isActive) {
                delay(3_000)
                try {
                    val url = "$baseUrl/api/device/bind-status?device_id=$deviceId"
                    Log.d("BindActivity", "poll request -> GET $url")
                    val request = Request.Builder()
                        .url(url)
                        .build()
                    // use{} 确保 response 和 body 在读取后被正确关闭，防止连接泄漏
                    http.newCall(request).execute().use { resp ->
                        val bodyStr = resp.body?.string()
                        Log.d("BindActivity", "poll response <- ${resp.code} ${resp.message}, headers=${resp.headers}")
                        if (resp.isSuccessful) {
                            if (bodyStr.isNullOrEmpty()) return@use
                            val body = JSONObject(bodyStr)
                            val bound = body.optBoolean("bound", false)
                            Log.d("BindActivity", "poll parsed: bound=$bound, device=$deviceId")
                            if (bound) {
                                prefs.isBound = true
                                val token = body.optString("user_token").takeIf { it.isNotEmpty() }
                                Log.d("BindActivity", "bind-status: bound=true, user_token=${if (token != null) "[PRESENT]" else "null"}")
                                token?.let { prefs.userToken = it }
                                Log.d("BindActivity", "prefs.userToken after save=${prefs.userToken?.take(8) ?: "null"}")
                                withContext(Dispatchers.Main) { goMain() }
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("BindActivity", "poll bind-status error: ${e.message}", e)
                }
            }
        }
    }

    private fun goMain() {
        Log.d("BindActivity", "goMain() reinit ApiClient with token=${prefs.userToken?.take(8) ?: "null"}")
        ApiClient.init(prefs.serverBaseUrl, prefs.userToken)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
    }
}
