package com.photoframe

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.photoframe.data.AppPrefs
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
    private lateinit var btnSkip: Button

    private val http = OkHttpClient()
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPrefs(this)

        // 已绑定直接进主界面
        if (prefs.isBound && prefs.deviceId != null) {
            goMain()
            return
        }

        setContentView(R.layout.activity_bind)
        ivQr = findViewById(R.id.iv_qr)
        tvHint = findViewById(R.id.tv_hint)
        btnSkip = findViewById(R.id.btn_skip)

        btnSkip.setOnClickListener { goMain() }

        lifecycleScope.launch {
            registerDevice()
        }
    }

    private suspend fun registerDevice() = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getString(R.string.server_base_url)
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

                withContext(Dispatchers.Main) {
                    showQrCode(qrToken)
                    startPollingBind()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                tvHint.text = "连接服务器失败，请检查网络后重试"
            }
        }
    }

    private fun showQrCode(qrToken: String) {
        val qrContent = "photoframe://bind?qr_token=$qrToken"
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 512, 512)
        ivQr.setImageBitmap(bitmap)
        tvHint.text = "用微信扫码绑定相框"
        ivQr.visibility = View.VISIBLE
    }

    /** 每 3 秒轮询一次，检查是否已有用户绑定 */
    private fun startPollingBind() {
        pollJob = lifecycleScope.launch {
            val deviceId = prefs.deviceId ?: return@launch
            val baseUrl = getString(R.string.server_base_url)
            while (isActive) {
                delay(3_000)
                try {
                    val request = Request.Builder()
                        .url("$baseUrl/api/device/bind-status?device_id=$deviceId")
                        .build()
                    // use{} 确保 response 和 body 在读取后被正确关闭，防止连接泄漏
                    http.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: return@use
                            val body = JSONObject(bodyStr)
                            if (body.optBoolean("bound", false)) {
                                prefs.isBound = true
                                goMain()
                                return@launch
                            }
                        }
                    }
                } catch (_: Exception) { /* 忽略网络错误，继续轮询 */ }
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
    }
}
