package com.photoframe

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.photoframe.adapter.SlideShowAdapter
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.data.RemotePhotoRepository
import com.photoframe.service.PhotoSyncService
import com.photoframe.service.ScreenScheduler
import com.photoframe.updater.AutoUpdater
import com.photoframe.viewmodel.MainViewModel
import com.photoframe.viewmodel.MainViewModelFactory
import java.lang.ref.WeakReference

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var viewModel: MainViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: SlideShowAdapter
    private lateinit var syncService: PhotoSyncService
    private lateinit var screenScheduler: ScreenScheduler
    private lateinit var autoUpdater: AutoUpdater

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector

    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            val state = viewModel.uiState.value
            if (state.photos.isEmpty()) {
                handler.postDelayed(this, 5_000)
                return
            }
            val next = viewModel.nextPageIndex()
            viewPager.setCurrentItem(next, true)
            handler.postDelayed(this, state.slideDurationMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPrefs(this)
        viewModel = ViewModelProvider(this, MainViewModelFactory(prefs))[MainViewModel::class.java]
        viewModel.loadPreferences()

        // 全屏沉浸式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.view_pager)

        val state = viewModel.uiState.value
        adapter = SlideShowAdapter(state.photos, state.showPhotoInfo)
        viewPager.adapter = adapter
        applyTransitionEffect()

        // 点击屏幕进入设置（在 Activity 层拦截，避免 ViewPager2 消费触摸事件）
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 先恢复亮度（夜间模式下触摸唤醒）
                if (viewModel.uiState.value.isNightMode) {
                    screenScheduler.setBrightness(-1f)
                    viewModel.setNightMode(false)
                    handler.postDelayed({ viewModel.setNightMode(true) }, 30_000)
                } else {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                return true
            }
        })

        // 同步服务 — 使用 Repository 统一数据访问路径
        val photoRepo = RemotePhotoRepository(ApiClient.service, prefs.serverBaseUrl)
        syncService = PhotoSyncService(this, photoRepo) { newPhotos ->
            android.util.Log.d("MainActivity", "收到 ${newPhotos.size} 张照片，当前已有 ${viewModel.uiState.value.photos.size} 张")
            viewModel.onNewPhotos(newPhotos)
            // 更新 adapter
            val updatedPhotos = viewModel.uiState.value.photos
            android.util.Log.d("MainActivity", "更新 adapter，总计 ${updatedPhotos.size} 张")
            adapter.updatePhotos(updatedPhotos)
        }

        // 定时黑屏
        screenScheduler = ScreenScheduler(this)
        screenScheduler.nightModeListener = WeakReference { isNight ->
            viewModel.setNightMode(isNight)
        }

        // 注册 401 回调：Token 过期时清除绑定状态并跳转重新绑定
        ApiClient.onUnauthorized = {
            runOnUiThread {
                prefs.isBound = false
                prefs.userToken = null
                startActivity(Intent(this, BindActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("reason", "expired")
                })
            }
        }

        // 自动更新
        autoUpdater = AutoUpdater(this)
        autoUpdater.checkAndUpdate(packageManager.getPackageInfo(packageName, 0).versionName)
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后重新应用偏好
        viewModel.loadPreferences()
        val state = viewModel.uiState.value
        adapter.setShowInfo(state.showPhotoInfo)
        applyTransitionEffect()

        handler.removeCallbacks(autoSlideRunnable)
        handler.postDelayed(autoSlideRunnable, state.slideDurationMs)

        syncService.start()
        screenScheduler.start()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoSlideRunnable)
        syncService.stop()
        screenScheduler.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncService.destroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun applyTransitionEffect() {
        when (viewModel.uiState.value.transitionEffect) {
            "slide" -> viewPager.setPageTransformer(null) // 默认滑动
            "zoom" -> viewPager.setPageTransformer(ZoomPageTransformer())
            else -> viewPager.setPageTransformer(FadePageTransformer())
        }
    }
}

/** 淡入淡出 */
class FadePageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.translationX = -position * page.width  // 抵消默认水平位移
        page.alpha = 1f - Math.abs(position)
    }
}

/** 缩放 */
class ZoomPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.translationX = -position * page.width  // 抵消默认水平位移
        val scale = if (Math.abs(position) < 1) 0.85f + (1 - Math.abs(position)) * 0.15f else 0.85f
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - Math.abs(position) * 0.5f
    }
}
