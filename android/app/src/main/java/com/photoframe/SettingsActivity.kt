package com.photoframe

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.updater.AutoUpdater
import com.photoframe.util.QrCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        setContentView(R.layout.activity_settings)

        // 服务器地址
        val etServerUrl = findViewById<EditText>(R.id.et_server_url)
        etServerUrl.setText(prefs.serverBaseUrl)

        val defaultUrl = getString(R.string.server_base_url)
        findViewById<Button>(R.id.btn_reset_url).setOnClickListener {
            etServerUrl.setText(defaultUrl)
        }

        // 展示时长
        val etDuration = findViewById<EditText>(R.id.et_duration)
        etDuration.setText(prefs.slideDurationSec.toString())

        // 播放模式
        val rgPlayMode = findViewById<RadioGroup>(R.id.rg_play_mode)
        if (prefs.playMode == "random") {
            rgPlayMode.check(R.id.rb_random)
        } else {
            rgPlayMode.check(R.id.rb_sequential)
        }

        // 切换动画
        val spinnerEffect = findViewById<Spinner>(R.id.spinner_effect)
        val effects = arrayOf("fade", "slide", "zoom")
        val effectLabels = arrayOf("淡入淡出", "左右滑动", "缩放")
        spinnerEffect.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, effectLabels)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerEffect.setSelection(effects.indexOf(prefs.transitionEffect).coerceAtLeast(0))

        // 显示照片信息
        val switchInfo = findViewById<Switch>(R.id.switch_photo_info)
        switchInfo.isChecked = prefs.showPhotoInfo

        // 定时黑屏
        val switchNight = findViewById<Switch>(R.id.switch_night)
        val layoutNightTime = findViewById<LinearLayout>(R.id.layout_night_time)
        switchNight.isChecked = prefs.nightModeEnabled
        layoutNightTime.visibility = if (prefs.nightModeEnabled) LinearLayout.VISIBLE else LinearLayout.GONE
        switchNight.setOnCheckedChangeListener { _, checked ->
            layoutNightTime.visibility = if (checked) LinearLayout.VISIBLE else LinearLayout.GONE
        }

        val tpNightStart = findViewById<TimePicker>(R.id.tp_night_start)
        tpNightStart.setIs24HourView(true)
        tpNightStart.hour = prefs.nightModeStartHour
        tpNightStart.minute = prefs.nightModeStartMinute

        val tpNightEnd = findViewById<TimePicker>(R.id.tp_night_end)
        tpNightEnd.setIs24HourView(true)
        tpNightEnd.hour = prefs.nightModeEndHour
        tpNightEnd.minute = prefs.nightModeEndMinute

        // 相框 ID
        val tvDeviceId = findViewById<TextView>(R.id.tv_device_id)
        tvDeviceId.text = "相框 ID: ${prefs.deviceId ?: "未注册"}"

        // 邀请家人二维码
        loadInviteQrCode()

        // 保存
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            val newUrl = etServerUrl.text.toString().trim()
            if (newUrl.isEmpty() || (!newUrl.startsWith("http://") && !newUrl.startsWith("https://"))) {
                Toast.makeText(this, "服务器地址格式不正确，请以 http:// 或 https:// 开头", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val urlChanged = newUrl != prefs.serverBaseUrl

            prefs.slideDurationSec = etDuration.text.toString().toIntOrNull()?.coerceIn(5, 300) ?: 15
            prefs.playMode = if (rgPlayMode.checkedRadioButtonId == R.id.rb_random) "random" else "sequential"
            prefs.transitionEffect = effects[spinnerEffect.selectedItemPosition]
            prefs.showPhotoInfo = switchInfo.isChecked
            prefs.nightModeEnabled = switchNight.isChecked
            prefs.nightModeStartHour = tpNightStart.hour
            prefs.nightModeStartMinute = tpNightStart.minute
            prefs.nightModeEndHour = tpNightEnd.hour
            prefs.nightModeEndMinute = tpNightEnd.minute

            if (urlChanged) {
                // 服务器地址变更：清除绑定状态，重新初始化 ApiClient，跳转重新绑定
                prefs.serverBaseUrl = newUrl
                prefs.isBound = false
                prefs.userToken = null
                prefs.deviceId = null
                prefs.qrToken = null
                prefs.lastSyncTime = null
                ApiClient.reinit(newUrl, null)
                Toast.makeText(this, "服务器地址已更新，请重新绑定相框", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, BindActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } else {
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // 版本号
        val tvVersion = findViewById<TextView>(R.id.tv_version)
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        tvVersion.text = "版本 v$versionName"

        // 检查更新
        findViewById<Button>(R.id.btn_check_update).setOnClickListener { btn ->
            btn.isEnabled = false
            (btn as Button).text = "检查中..."
            AutoUpdater(this).checkAndUpdate(versionName) {
                btn.isEnabled = true
                btn.text = "检查更新"
                Toast.makeText(this, "已是最新版本", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadInviteQrCode() {
        val ivInviteQr = findViewById<ImageView>(R.id.iv_invite_qr)
        val qrToken = prefs.qrToken
        if (qrToken.isNullOrBlank()) {
            ivInviteQr.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                QrCodeHelper.generateBitmap(qrToken)
            }
            if (bitmap != null) {
                ivInviteQr.setImageBitmap(bitmap)
                ivInviteQr.visibility = View.VISIBLE
            } else {
                ivInviteQr.visibility = View.GONE
            }
        }
    }
}
