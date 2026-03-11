---
status: pending
priority: p2
issue_id: "046"
tags: [code-review, performance, android]
dependencies: []
---

# SettingsActivity 旋转重建时反复重新生成 QR Bitmap

## Problem Statement

`AndroidManifest.xml` 中 `SettingsActivity` 没有声明 `android:configChanges`，屏幕旋转会触发完整的 `onDestroy → onCreate` 重建，每次都重新执行 `loadInviteQrCode()`，重新生成 512×512 Bitmap。`qrToken` 在旋转过程中完全不变，重新生成是纯粹的浪费：旧 Bitmap 被丢弃等待 GC 回收，在内存紧张的老设备上可能触发 GC 暂停。

## Findings

**位置 1：** `android/app/src/main/AndroidManifest.xml` — `SettingsActivity` 无 `configChanges` 声明

**位置 2：** `android/app/src/main/java/com/photoframe/SettingsActivity.kt` 第 80 行
```kotlin
loadInviteQrCode()  // 在 onCreate() 中调用，旋转时重新执行
```

## Proposed Solutions

### 方案 A：Manifest 声明 configChanges（成本最低，推荐）
```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".SettingsActivity"
    android:configChanges="orientation|screenSize|keyboardHidden"
    ... />
```
旋转时系统不重建 Activity，直接调用 `onConfigurationChanged()`，避免重新生成 Bitmap。
- **优点：** 1 行改动，完全消除问题
- **缺点：** 若布局需要响应旋转变化，需额外处理 `onConfigurationChanged()`（当前设置页布局较简单，旋转后 ScrollView 自适应即可）

### 方案 B：ViewModel 缓存 Bitmap
```kotlin
class SettingsViewModel : ViewModel() {
    var cachedQrBitmap: Bitmap? = null
}
// SettingsActivity 优先从 ViewModel 取缓存
```
- **优点：** 规范 Android 架构，Bitmap 跨旋转复用
- **缺点：** 需引入 ViewModel，改动较大

### 方案 C：与 Todo 044 合并（异步 + 缓存）
若 Todo 044 的协程方案已实现，可在协程中先检查已有 Bitmap：若 `ivInviteQr.drawable != null` 则跳过重新生成。但此方法依赖 View 状态，不如 ViewModel 可靠。

## Recommended Action

方案 A — 在 `AndroidManifest.xml` 中为 `SettingsActivity` 加 `configChanges`，一行改动消除问题，可与 Todo 044 一起处理。

## Technical Details

- **受影响文件：**
  - `android/app/src/main/AndroidManifest.xml`（需添加 configChanges）
  - `android/app/src/main/java/com/photoframe/SettingsActivity.kt`（可选缓存逻辑）
- **场景：** 相框设备固定横放时发生概率低，但 `sensor` 模式无法排除

## Acceptance Criteria

- [ ] 屏幕旋转后，设置页 QR 码正常显示（不闪烁/消失）
- [ ] 旋转时不重新生成 QR Bitmap（通过 configChanges 或 ViewModel 缓存）

## Work Log

- 2026-03-11: 发现于 PR #3 代码审查，performance-oracle agent 标识为 P1
