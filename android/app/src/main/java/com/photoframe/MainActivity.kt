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
import androidx.viewpager2.widget.ViewPager2
import com.photoframe.adapter.SlideShowAdapter
import com.photoframe.data.ApiClient
import com.photoframe.data.AppPrefs
import com.photoframe.data.Photo
import com.photoframe.service.PhotoSyncService
import com.photoframe.service.ScreenScheduler
import com.photoframe.updater.AutoUpdater

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPrefs
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: SlideShowAdapter
    private lateinit var syncService: PhotoSyncService
    private lateinit var screenScheduler: ScreenScheduler
    private lateinit var autoUpdater: AutoUpdater

    private val handler = Handler(Looper.getMainLooper())
    private val allPhotos = mutableListOf<Photo>()
    private var isNightMode = false
    private lateinit var gestureDetector: GestureDetector

    private val autoSlideRunnable = object : Runnable {
        override fun run() {
            if (allPhotos.isEmpty()) {
                handler.postDelayed(this, 5_000)
                return
            }
            val next = (viewPager.currentItem + 1) % allPhotos.size
            viewPager.setCurrentItem(next, true)
            handler.postDelayed(this, prefs.slideDurationSec * 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPrefs(this)
        // ApiClient 已在 PhotoFrameApplication.onCreate() 中初始化，此处无需重复调用

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

        adapter = SlideShowAdapter(allPhotos, prefs.showPhotoInfo)
        viewPager.adapter = adapter
        applyTransitionEffect()

        // 点击屏幕进入设置（在 Activity 层拦截，避免 ViewPager2 消费触摸事件）
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // 先恢复亮度（夜间模式下触摸唤醒）
                if (isNightMode) {
                    screenScheduler.setBrightness(-1f)
                    isNightMode = false
                    handler.postDelayed({ isNightMode = true }, 30_000)
                } else {
                    startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                return true
            }
        })

        // 同步服务
        syncService = PhotoSyncService(this) { newPhotos ->
            android.util.Log.d("MainActivity", "收到 ${newPhotos.size} 张照片，当前已有 ${allPhotos.size} 张")
            val existingIds = allPhotos.map { it.id }.toSet()
            val fresh = newPhotos.filter { it.id !in existingIds }
            android.util.Log.d("MainActivity", "去重后新增 ${fresh.size} 张")
            if (fresh.isNotEmpty()) {
                if (prefs.playMode == "random") {
                    allPhotos.addAll(fresh)
                    allPhotos.shuffle()
                } else {
                    allPhotos.addAll(fresh)
                }
                android.util.Log.d("MainActivity", "更新 adapter，总计 ${allPhotos.size} 张")
                adapter.updatePhotos(allPhotos.toList())
            }
        }

        // 定时黑屏
        screenScheduler = ScreenScheduler(this)

        // 自动更新
        autoUpdater = AutoUpdater(this)
        autoUpdater.checkAndUpdate(packageManager.getPackageInfo(packageName, 0).versionName)
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后重新应用切换效果（不重建 Adapter，保持当前播放位置）
        adapter.setShowInfo(prefs.showPhotoInfo)
        applyTransitionEffect()

        handler.removeCallbacks(autoSlideRunnable)
        handler.postDelayed(autoSlideRunnable, prefs.slideDurationSec * 1_000L)

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
        when (prefs.transitionEffect) {
            "slide" -> viewPager.setPageTransformer(null) // 默认滑动
            "zoom" -> viewPager.setPageTransformer(ZoomPageTransformer())
            else -> viewPager.setPageTransformer(FadePageTransformer())
        }
    }
}

/** 淡入淡出 */
class FadePageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.alpha = 1f - Math.abs(position)
    }
}

/** 缩放 */
class ZoomPageTransformer : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        val scale = if (Math.abs(position) < 1) 0.85f + (1 - Math.abs(position)) * 0.15f else 0.85f
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - Math.abs(position) * 0.5f
    }
}
