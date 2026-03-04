---
status: pending
priority: p3
issue_id: "013"
tags: [code-review, android, performance, quality]
dependencies: []
---

# P3: Android 端多项代码质量改进

## Problem Statement

Android 端代码中存在多个较小的代码质量问题，不会直接导致功能异常，但影响性能、可维护性和用户体验：

1. **SlideShowAdapter 使用 `notifyDataSetChanged()`**：全量重绘 ViewPager2，效率低，可改用 DiffUtil
2. **build.gradle 未开启代码混淆**：`minifyEnabled false`，APK 体积偏大，且发布版本代码未混淆，逆向工程难度低
3. **PhotoSyncService CoroutineScope 未绑定生命周期**：使用独立 `CoroutineScope(Dispatchers.IO + SupervisorJob())`，Service 销毁时协程不自动取消，需手动管理
4. **AutoUpdater 广播接收器生命周期**：`downloadReceiver` 未在 Activity/Service 销毁时反注册（`unregisterReceiver`），可能导致内存泄漏

## Findings

### 问题 1: notifyDataSetChanged
**文件:** `android/app/src/main/java/com/simonlei/photoframe/adapter/SlideShowAdapter.kt`
```kotlin
fun updatePhotos(newPhotos: List<Photo>) {
    photos.clear()
    photos.addAll(newPhotos)
    notifyDataSetChanged()  // 全量重绘，可改用 DiffUtil
}
```

### 问题 2: 混淆配置
**文件:** `android/app/build.gradle`
```gradle
buildTypes {
    release {
        minifyEnabled false  // 应改为 true
        // 缺少 proguardFiles 配置
    }
}
```

### 问题 3: CoroutineScope 生命周期
**文件:** `android/app/src/main/java/com/simonlei/photoframe/service/PhotoSyncService.kt`
```kotlin
private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
// Service 销毁时应调用 scope.cancel()
override fun onDestroy() {
    super.onDestroy()
    // 缺少 scope.cancel()
}
```

### 问题 4: BroadcastReceiver 泄漏
**文件:** `android/app/src/main/java/com/simonlei/photoframe/updater/AutoUpdater.kt`
```kotlin
context.registerReceiver(downloadReceiver, IntentFilter(...))
// 缺少 context.unregisterReceiver(downloadReceiver)
```

## Proposed Solutions

### 问题 1 修复
实现 `PhotoDiffCallback` 并使用 `DiffUtil.calculateDiff()` 替换 `notifyDataSetChanged()`（见 todo-011 中的方案 B 详细代码）。

### 问题 2 修复
```gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
    }
}
```
需同步添加 `proguard-rules.pro` 保留必要的类（Retrofit、Gson 模型等）。

### 问题 3 修复
```kotlin
override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
}
```

### 问题 4 修复
下载完成或 AutoUpdater 不再使用时调用 `context.unregisterReceiver(downloadReceiver)`：
```kotlin
private val downloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 处理完成后立即反注册
        context.unregisterReceiver(this)
        ...
    }
}
```

## Technical Details

**受影响文件:**
- `adapter/SlideShowAdapter.kt`
- `app/build.gradle`
- `service/PhotoSyncService.kt`
- `updater/AutoUpdater.kt`

## Acceptance Criteria

- [ ] SlideShowAdapter 使用 DiffUtil 进行差量更新
- [ ] release build 开启 minifyEnabled 并配置 ProGuard 规则
- [ ] PhotoSyncService.onDestroy() 调用 scope.cancel()
- [ ] AutoUpdater 在下载完成后反注册 BroadcastReceiver

## Work Log

- 2026-03-04: code-review 发现，由多个审查代理报告
