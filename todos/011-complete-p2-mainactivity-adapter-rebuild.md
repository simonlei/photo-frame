---
status: pending
priority: p2
issue_id: "011"
tags: [code-review, android, kotlin, performance, ui]
dependencies: []
---

# P2: MainActivity onResume 无条件重建 SlideShowAdapter（性能问题）

## Problem Statement

`MainActivity.kt` 在每次 `onResume()` 时无条件重建 `SlideShowAdapter` 并重新设置给 `ViewPager2`。这导致：
1. 每次切换回 MainActivity（如从 SettingsActivity 返回、屏幕亮起）都会重置幻灯片到第一张
2. 正在播放的照片被中断，用户体验差
3. `ViewPager2` 会重新测量和布局，造成不必要的 UI 开销

## Findings

**受影响文件:** `android/app/src/main/java/com/simonlei/photoframe/MainActivity.kt`

```kotlin
override fun onResume() {
    super.onResume()
    // 每次 onResume 都重建 Adapter，重置到第一张
    adapter = SlideShowAdapter(allPhotos)
    viewPager.adapter = adapter  // 这会导致 ViewPager2 重置位置
    startSlideShow()
}
```

## Proposed Solutions

### 方案 A（推荐）：仅在数据变化时更新 Adapter
使用标志位或比较数据，只在必要时更新：

```kotlin
private var adapterInitialized = false

override fun onResume() {
    super.onResume()
    if (!adapterInitialized) {
        adapter = SlideShowAdapter(allPhotos)
        viewPager.adapter = adapter
        adapterInitialized = true
    }
    // 设置变化时刷新：startSlideShow 检查配置是否变更
    applyLatestSettings()
    startSlideShowIfNeeded()
}
```

PhotoSyncService 回调新照片时调用 `adapter.updatePhotos()` 而不是重建 Adapter。

- 优点：消除不必要重建，保持当前播放位置
- 缺点：需要 Adapter 支持 `updatePhotos()` 方法
- 风险：低

### 方案 B：SlideShowAdapter 使用 DiffUtil
在 `SlideShowAdapter` 中实现 `DiffUtil.Callback`，支持差量更新：

```kotlin
fun updatePhotos(newPhotos: List<Photo>) {
    val diffResult = DiffUtil.calculateDiff(PhotoDiffCallback(photos, newPhotos))
    photos = newPhotos.toMutableList()
    diffResult.dispatchUpdatesTo(this)
}
```

`onResume` 中调用 `adapter.updatePhotos(allPhotos)` 而非重建 Adapter。

- 优点：最佳性能，精确更新变化的 item，保持滚动位置
- 缺点：需要实现 `DiffUtil.Callback`
- 风险：低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `android/app/src/main/java/com/simonlei/photoframe/MainActivity.kt`
- `android/app/src/main/java/com/simonlei/photoframe/adapter/SlideShowAdapter.kt`

**用户可见影响:** 从设置页返回时幻灯片重置到第一张，影响使用体验

## Acceptance Criteria

- [ ] 从 SettingsActivity 返回后幻灯片继续从当前位置播放，不重置
- [ ] 新照片同步后 ViewPager2 正确更新但不重置位置
- [ ] 屏幕亮起（从黑屏恢复）后幻灯片继续播放，不重置

## Work Log

- 2026-03-04: code-review 发现，由 architecture-strategist 代理报告
