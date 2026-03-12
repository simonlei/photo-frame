---
status: pending
priority: p3
issue_id: "049"
tags: [code-review, quality, android]
dependencies: []
---

# QR 生成逻辑和 URL 模板在两处重复

## Problem Statement

PR #3 新增的 `SettingsActivity.loadInviteQrCode()` 与已有的 `BindActivity.showQrCode()` 存在以下重复：
1. QR 内容 URL 模板字符串 `"photoframe://bind?qr_token=$qrToken"` 在两处各写一次
2. `BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)` 调用完全相同
3. 两处错误处理不对称：`SettingsActivity` 有 try/catch，`BindActivity` 没有

未来若 URL scheme 变更（例如加版本参数），需要同步修改两处。

## Findings

**BindActivity.kt 第 100-102 行：**
```kotlin
val qrContent = "photoframe://bind?qr_token=$qrToken"
val barcodeEncoder = BarcodeEncoder()
val bitmap = barcodeEncoder.encodeBitmap(qrContent, BarcodeFormat.QR_CODE, 512, 512)
// 无 try/catch
```

**SettingsActivity.kt 第 145-148 行：**
```kotlin
val content = "photoframe://bind?qr_token=$qrToken"
try {
    val bitmap = BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512)
    ...
} catch (e: Exception) { ... }
```

## Proposed Solutions

### 方案 A：提取工具函数（推荐）
新建 `android/app/src/main/java/com/photoframe/util/QrCodeHelper.kt`：
```kotlin
object QrCodeHelper {
    fun buildBindUrl(qrToken: String) = "photoframe://bind?qr_token=$qrToken"

    fun generateBitmap(qrToken: String, size: Int = 256): Bitmap? =
        runCatching {
            BarcodeEncoder().encodeBitmap(buildBindUrl(qrToken), BarcodeFormat.QR_CODE, size, size)
        }.getOrNull()
}
```
两个 Activity 均调用 `QrCodeHelper.generateBitmap(qrToken)`，消除重复并统一错误处理。

### 方案 B：仅提取 URL 常量（最小改动）
在 `AppPrefs` 伴生对象中加：
```kotlin
companion object {
    fun buildBindUrl(qrToken: String) = "photoframe://bind?qr_token=$qrToken"
}
```
- **优点：** 改动小，消除字符串重复
- **缺点：** `encodeBitmap` 调用仍重复

## Recommended Action

方案 A，可与 Todo 044（异步化）一起实现，顺带统一两处逻辑。

## Technical Details

- **受影响文件：**
  - `android/app/src/main/java/com/photoframe/BindActivity.kt`
  - `android/app/src/main/java/com/photoframe/SettingsActivity.kt`
  - 新文件：`android/app/src/main/java/com/photoframe/util/QrCodeHelper.kt`

## Acceptance Criteria

- [ ] QR 内容 URL 模板只在一处定义
- [ ] `encodeBitmap` 调用逻辑只在一处实现
- [ ] 两处调用点行为一致（包括错误处理）

## Work Log

- 2026-03-11: 发现于 PR #3 code-simplicity-reviewer 审查，标识为优先级 1
