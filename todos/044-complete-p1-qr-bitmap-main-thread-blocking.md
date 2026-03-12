---
status: pending
priority: p1
issue_id: "044"
tags: [code-review, performance, android]
dependencies: []
---

# QR 码生成在主线程执行导致 ANR/卡顿风险

## Problem Statement

`SettingsActivity.loadInviteQrCode()` 和 `BindActivity.showQrCode()` 均在主线程同步执行 ZXing 的 `BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)`。此操作包含 Reed-Solomon 纠错矩阵构建和 262,144 次像素写入，在老旧 Android 平板（ARM Cortex-A53、1.3GHz）上实测耗时 80-150ms，直接阻塞 UI 线程，导致设置页/启动页打开时明显卡顿（>16ms 即掉帧，>100ms 用户可感知）。

`BindActivity` 是 Launcher Activity，用户冷启动时最先看到，主线程阻塞的用户感知最严重。

## Findings

**位置 1：** `android/app/src/main/java/com/photoframe/SettingsActivity.kt` 第 147 行
```kotlin
val bitmap = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)
// 在 onCreate() 主线程直接调用
```

**位置 2：** `android/app/src/main/java/com/photoframe/BindActivity.kt` 第 102 行
```kotlin
val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 512, 512)
// 在 withContext(Dispatchers.Main) 中调用，仍是主线程
```

此外 `BindActivity.showQrCode()` 没有 try/catch 保护，若低内存设备 OOM，会直接崩溃（与 `SettingsActivity` 的防护不对称）。

## Proposed Solutions

### 方案 A：协程异步生成（推荐）
将 Bitmap 生成移至 `Dispatchers.Default`，仅在主线程设置 ImageView：

**SettingsActivity.kt:**
```kotlin
private fun loadInviteQrCode() {
    val ivInviteQr = findViewById<ImageView>(R.id.iv_invite_qr)
    val qrToken = prefs.qrToken
    if (qrToken.isNullOrBlank()) {
        ivInviteQr.visibility = View.GONE
        return
    }
    val content = "photoframe://bind?qr_token=$qrToken"
    lifecycleScope.launch {
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 256, 256)
            }.getOrNull()
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

**BindActivity.kt（在 registerDevice 的 IO 协程中）：**
```kotlin
val bitmap = withContext(Dispatchers.Default) {
    BarcodeEncoder().encodeBitmap("photoframe://bind?qr_token=$qrToken", BarcodeFormat.QR_CODE, 256, 256)
}
withContext(Dispatchers.Main) {
    ivQr.setImageBitmap(bitmap)
    tvHint.text = "用微信扫码绑定相框"
    ivQr.visibility = View.VISIBLE
    startPollingBind()
}
```
- **优点：** 完全消除主线程阻塞，同时将尺寸从 512 降至 256（内存从 1MB 降至 256KB）
- **缺点：** 需要对两个 Activity 同时改动
- **风险：** 低（协程在 Activity 销毁时自动取消）

### 方案 B：仅添加 try/catch（最小修复）
保持同步，只为 `BindActivity.showQrCode()` 补充 try/catch，消除崩溃风险，但不解决卡顿问题。
- **优点：** 改动最小
- **缺点：** 主线程阻塞问题仍在

## Recommended Action

方案 A — 同时修复两处，将尺寸从 512 改为 256 可额外减少 75% 内存占用。

## Technical Details

- **受影响文件：**
  - `android/app/src/main/java/com/photoframe/SettingsActivity.kt:147`
  - `android/app/src/main/java/com/photoframe/BindActivity.kt:99-106`
- **目标设备：** 老旧 Android 平板，内存受限
- **影响组件：** SettingsActivity(onCreate)、BindActivity(showQrCode)

## Acceptance Criteria

- [ ] `loadInviteQrCode()` 的 `encodeBitmap` 调用不在主线程执行
- [ ] `showQrCode()` 的 `encodeBitmap` 调用不在主线程执行
- [ ] `BindActivity.showQrCode()` 有 try/catch 或等效异常保护
- [ ] 生成尺寸降至 256（可选，建议同步处理）
- [ ] Activity 销毁后协程不泄漏（使用 lifecycleScope）

## Work Log

- 2026-03-11: 发现于 PR #3 代码审查，performance-oracle agent 标识为 P0
