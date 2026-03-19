---
date: 2026-03-12
problem_type:
  - performance_issue
  - security_issue
  - code_quality
  - android_patterns
module: android
severity: P1
symptoms:
  - SettingsActivity 打开时明显卡顿（老旧平板 80-150ms 主线程阻塞）
  - BindActivity（Launcher Activity）冷启动延迟
  - ADB logcat 可读取完整 user_token 明文
  - 屏幕旋转后 QR Bitmap 被重新生成（无必要的重复计算）
  - SettingsActivity 和 BindActivity 重复实现 QR 生成逻辑，错误处理不一致
tags:
  - ZXing
  - BarcodeEncoder
  - QR-code
  - main-thread
  - ANR
  - Dispatchers
  - coroutine
  - lifecycleScope
  - configChanges
  - logcat
  - token-leak
  - security
  - android-performance
  - SharedPreferences
---

# Android QR 码主线程阻塞、logcat Token 泄露及 Activity 配置问题

## 背景

在为电子相框 Android App 新增"设置页展示邀请二维码"功能时，代码审查发现了一组 Android 开发中的典型问题：CPU 密集操作在主线程同步执行、调试日志包含敏感凭证、Activity 旋转无 configChanges 保护，以及跨 Activity 重复代码。

---

## 问题 1（P1）：ZXing BarcodeEncoder 在主线程执行

### 症状

- SettingsActivity 打开时明显卡顿，`onCreate()` 耗时远超 16ms
- BindActivity（Launcher Activity）冷启动出现白屏延迟

### 根因

`BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)` 在两处均在主线程同步执行：

**SettingsActivity（直接在 onCreate 主线程）：**
```kotlin
// ❌ 错误：主线程同步执行 CPU 密集操作
private fun loadInviteQrCode() {
    val content = "photoframe://bind?qr_token=$qrToken"
    try {
        val bitmap = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)
        ivInviteQr.setImageBitmap(bitmap)
    } catch (e: Exception) { ... }
}
```

**BindActivity（在协程中但位于 `withContext(Dispatchers.Main)` 内）：**
```kotlin
// ❌ 错误：虽在协程中，但 encodeBitmap 仍在主线程执行
withContext(Dispatchers.Main) {
    showQrCode(qrToken)  // showQrCode 内部调用 encodeBitmap
}
```

ZXing 生成 512×512 QR 码需要：Reed-Solomon 纠错矩阵构建 + 262,144 次像素写入，在 ARM Cortex-A53（1.3GHz 老旧平板）上耗时 **80-150ms**，直接阻塞 UI 线程。Android 16.67ms/帧的预算被严重超出。

### 解决方案

**第一步：提取 QrCodeHelper 工具类**

新建 `android/app/src/main/java/com/photoframe/util/QrCodeHelper.kt`：

```kotlin
package com.photoframe.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

object QrCodeHelper {
    fun buildBindUrl(qrToken: String) = "photoframe://bind?qr_token=$qrToken"

    fun generateBitmap(qrToken: String, size: Int = 256): Bitmap? =
        runCatching {
            BarcodeEncoder().encodeBitmap(buildBindUrl(qrToken), BarcodeFormat.QR_CODE, size, size)
        }.getOrNull()
}
```

设计要点：
- 默认尺寸降为 256（内存从 1MB 降至 256KB，**减少 75%**）
- `runCatching` 统一错误处理，返回可空 Bitmap，调用方统一处理 null
- singleton object，无需实例化

**第二步：SettingsActivity 异步化**

```kotlin
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
```

**第三步：BindActivity 通过 ViewModel + StateFlow 异步生成**

> **⚠️ 架构变更（2026-03-19）：** BindActivity 已重构为 MVVM 架构。注册和轮询逻辑移入 `BindViewModel`，网络调用由 `RemoteDeviceRepository` 负责。Activity 仅负责 UI 渲染。

```kotlin
// BindActivity 观察 ViewModel 状态，收到 ShowQrCode 时异步生成 QR
lifecycleScope.launch {
    viewModel.uiState.collect { state ->
        when (state) {
            is BindUiState.ShowQrCode -> showQrCode(state.qrToken)
            // ... 其他状态处理 ...
        }
    }
}

// showQrCode() 在 Dispatchers.Default 上生成 Bitmap，主线程只做 setImageBitmap
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
```

`showQrCode()` 保留为独立方法（职责清晰），轮询逻辑已完全移入 `BindViewModel.startPolling()`。

---

## 问题 2（P2）：logcat 明文打印 user_token

### 症状

ADB 连接时（或开启开发者选项的设备），任何人可通过 `adb logcat | grep BindActivity` 读取完整的 user_token。

### 根因

```kotlin
// ❌ 错误：bodyStr 在绑定成功时包含完整的 {"bound": true, "user_token": "abc123..."}
Log.d("BindActivity", "poll response body: $bodyStr")
```

### 解决方案

```kotlin
// ✅ 删除包含完整响应体的行
// Log.d("BindActivity", "poll response body: $bodyStr")  ← 删除此行

// ✅ token 存在性标志替代内容输出
// 原：user_token=${token?.take(8) ?: "null"}
// 改：
Log.d("BindActivity", "bind-status: bound=true, user_token=${if (token != null) "[PRESENT]" else "null"}")
```

> **⚠️ 架构变更（2026-03-19）：** 上述 `Log.d` 调用在 BindActivity 重构后已被移除。轮询逻辑迁移到 `BindViewModel` + `RemoteDeviceRepository`，后者不包含任何 token 相关日志输出。`BindActivity.goMain()` 使用 `[PRESENT]` 模式记录 token 存在性。

### ⚠️ 回归案例（2026-03-19 #051 修复）

本问题在 `refactor/android-test-automation` 分支上**复发**。新增代码中 `token.take(8)` 再次出现在 `Log.d()` 调用中。根因是文档的防护建议仅覆盖手动 `Log.d()` 调用，遗漏了以下更隐蔽的泄露向量：

**HttpLoggingInterceptor 日志级别风险：**

```kotlin
// ⚠️ Level.HEADERS 或 Level.BODY 会将 Authorization: Bearer <token> 写入 Logcat
HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY    // ← 危险！会输出完整 Headers + Body
}

// ✅ 当前正确配置：Level.BASIC 只输出请求方法、URL、状态码和响应体大小
HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BASIC   // ← 安全
}
```

**建议：在 Release 构建中完全禁用日志拦截器：**

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
}
```

### 日志安全规则

| ❌ 禁止 | ✅ 替代 |
|---------|--------|
| `Log.d("API", "body: $responseBody")` | 不记录响应体 |
| `Log.d("Auth", "token=${token.take(8)}")` | `Log.d("Auth", "token=${if (token != null) "[PRESENT]" else "null"}")` |
| `Log.d("Login", "password=$password")` | 完全不记录 |
| `Log.d("Debug", "headers=$headers")` | 过滤 Authorization header 后记录 |
| `HttpLoggingInterceptor.Level.HEADERS` | `Level.BASIC`（不含 Headers/Body） |
| `HttpLoggingInterceptor.Level.BODY` | `Level.BASIC`，Release 用 `Level.NONE` |

---

## 问题 3（P2）：屏幕旋转重建 Activity，QR Bitmap 被重新生成

### 症状

旋转设备后，SettingsActivity 触发 `onDestroy → onCreate`，`loadInviteQrCode()` 被重新执行，无谓地重新生成 QR Bitmap，旧 Bitmap 等待 GC 回收，在内存受限的老设备上可能引发 GC 暂停。

### 解决方案

在 `AndroidManifest.xml` 的 SettingsActivity 声明中添加 `configChanges`：

```xml
<activity
    android:name=".SettingsActivity"
    android:configChanges="orientation|screenSize|keyboardHidden"
    android:screenOrientation="sensor"
    android:exported="false" />
```

旋转时系统调用 `onConfigurationChanged()` 而非重建 Activity，QR Bitmap 保持不变。

---

## 问题 4（P3）：布局颜色资源化

### 问题

`activity_settings.xml` 中 `#888888`（次要文字颜色）出现 3 次、`#EEEEEE`（分割线颜色）1 次，均为硬编码。

### 解决方案

`android/app/src/main/res/values/colors.xml` 新增：

```xml
<color name="text_secondary">#888888</color>
<color name="divider">#EEEEEE</color>
```

布局中所有 `#888888` → `@color/text_secondary`，`#EEEEEE` → `@color/divider`。

---

## 预防策略

### Android 主线程操作检查清单

在 Code Review 中，以下操作若出现在主线程（直接在 `onCreate/onResume` 或 `withContext(Dispatchers.Main)` 内），应立即标记：

| 操作类型 | 参考阈值 | 推荐 Dispatcher |
|---------|---------|----------------|
| 二维码/条形码生成（ZXing） | 任何尺寸 > 100×100 | `Dispatchers.Default` |
| Bitmap 编解码、缩放 | 任何操作 | `Dispatchers.Default` |
| 加密/哈希计算 | 任何操作 | `Dispatchers.Default` |
| 网络请求 | 任何请求 | `Dispatchers.IO` |
| 文件读写、SharedPreferences（首次） | > 5KB | `Dispatchers.IO` |

**快速判断规则：** 在 `withContext(Dispatchers.Main)` 块内，应只写 View 操作（`setImageBitmap`、`visibility`、`text =`），绝不做计算。

### Activity configChanges 规则

以下情况**必须**声明 `configChanges="orientation|screenSize|keyboardHidden"`：
- Activity 中有耗时生成的内容（图片、QR 码、图表）
- 重建成本高（含网络请求或数据库查询的 `onCreate`）
- 旋转后期望 View 状态不重置

### 日志安全检查

Code Review 时，在 `Log.*` 调用处搜索以下关键词，有任何一个出现即标记为安全问题：
`token`, `password`, `secret`, `authorization`, `responseBody`, `bodyStr`, `body`

**额外检查**：搜索 `HttpLoggingInterceptor`，确认 `level` 不是 `HEADERS` 或 `BODY`。Release 构建应使用 `Level.NONE`。

---

## 关联文档

- [android-kotlin-token-leak-coroutine-okhttp-dead-code-review.md](./android-kotlin-token-leak-coroutine-okhttp-dead-code-review.md) — 2026-03-19 修复的 4 个 P1 问题（token 泄露复发 #051、CancellationException #052、裸 OkHttpClient #053、Repository 死代码 #054）
- [photo-frame-comprehensive-code-review.md](./photo-frame-comprehensive-code-review.md) — 包含相关 Android 生命周期问题（CoroutineScope 未取消、onResume 重建 Adapter）
- [go-admin-api-security-performance-docker.md](./go-admin-api-security-performance-docker.md) — Token 安全相关（Timing Attack、LocalStorage XSS）
- [native-miniprogram-migration-and-patterns.md](../miniprogram-patterns/native-miniprogram-migration-and-patterns.md) — 小程序侧 QR token 解析模式
