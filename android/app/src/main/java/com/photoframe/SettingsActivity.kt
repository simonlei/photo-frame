package com.photoframe

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.photoframe.data.AppPrefs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPrefs(this)
        setContentView(R.layout.activity_settings)

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

        // 相框 ID（只读）
        val tvDeviceId = findViewById<TextView>(R.id.tv_device_id)
        tvDeviceId.text = "相框 ID: ${prefs.deviceId ?: "未注册"}"

        // 保存
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            prefs.slideDurationSec = etDuration.text.toString().toIntOrNull()?.coerceIn(5, 300) ?: 15
            prefs.playMode = if (rgPlayMode.checkedRadioButtonId == R.id.rb_random) "random" else "sequential"
            prefs.transitionEffect = effects[spinnerEffect.selectedItemPosition]
            prefs.showPhotoInfo = switchInfo.isChecked
            prefs.nightModeEnabled = switchNight.isChecked
            prefs.nightModeStartHour = tpNightStart.hour
            prefs.nightModeStartMinute = tpNightStart.minute
            prefs.nightModeEndHour = tpNightEnd.hour
            prefs.nightModeEndMinute = tpNightEnd.minute

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
