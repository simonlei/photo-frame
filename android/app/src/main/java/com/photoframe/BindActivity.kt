package com.photoframe

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.data.RemoteDeviceRepository
import com.photoframe.util.QrCodeHelper
import com.photoframe.viewmodel.BindUiState
import com.photoframe.viewmodel.BindViewModel
import com.photoframe.viewmodel.BindViewModelFactory
import kotlinx.coroutines.*
import okhttp3.OkHttpClient

/**
 * 首次启动：注册设备并显示二维码，等待用户扫码绑定
 */
class BindActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var viewModel: BindViewModel
    private lateinit var ivQr: ImageView
    private lateinit var tvHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPrefs(this)

        // 初始化 ViewModel
        val deviceRepo = RemoteDeviceRepository(OkHttpClient())
        viewModel = ViewModelProvider(this, BindViewModelFactory(prefs, deviceRepo))[BindViewModel::class.java]

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

        // 观察 ViewModel 状态
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is BindUiState.Loading -> {
                        tvHint.text = "正在连接服务器..."
                    }
                    is BindUiState.AlreadyBound -> {
                        goMain()
                    }
                    is BindUiState.ShowQrCode -> {
                        showQrCode(state.qrToken)
                    }
                    is BindUiState.BindSuccess -> {
                        goMain()
                    }
                    is BindUiState.Error -> {
                        tvHint.text = "连接服务器失败，请检查网络后重试"
                        Log.w("BindActivity", "Error: ${state.message}")
                    }
                }
            }
        }

        // 触发绑定流程
        viewModel.checkBindingStatus()
    }

    private fun showQrCode(qrToken: String) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeHelper.generateBitmap(qrToken)
            }
            bitmap?.let {
                ivQr.setImageBitmap(it)
                ivQr.visibility = View.VISIBLE
            }
            tvHint.text = "用微信扫码绑定相框"
        }
    }

    private fun goMain() {
        Log.d("BindActivity", "goMain() reinit ApiClient with token=${prefs.userToken?.take(8) ?: "null"}")
        ApiClient.init(prefs.serverBaseUrl, prefs.userToken)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
