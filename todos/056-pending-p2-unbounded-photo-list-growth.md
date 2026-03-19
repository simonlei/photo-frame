---
status: pending
priority: p2
issue_id: "056"
tags:
  - code-review
  - performance
  - android
dependencies: []
---

# MainViewModel 照片列表无上限增长 + 随机模式全量 shuffle

## Problem Statement

`MainViewModel.onNewPhotos()` 只做追加不做移除，列表无限增长。随机模式下每次增量同步（每60秒）都对全量列表 `shuffled()`，造成不必要的 GC 压力和 Adapter 全量刷新。

## Findings

1. **MainViewModel.kt:40** — `val updated = current + fresh` 只追加不移除
2. **MainViewModel.kt:41** — `if (prefs.playMode == "random") updated.shuffled()` 每次全量 shuffle
3. Shuffle 后 `currentIndex` 指向的照片完全改变，用户正在看的画面突然跳变
4. **MainViewModel.kt:37** — `current.map { it.id }.toSet()` 每次创建两个临时集合

## Proposed Solutions

### Option A: 限制列表容量 + 增量随机插入（推荐）
```kotlin
fun onNewPhotos(newPhotos: List<Photo>) {
    val merged = (_uiState.value.photos + newPhotos).distinctBy { it.id }
    val capped = if (merged.size > MAX_PHOTOS) merged.takeLast(MAX_PHOTOS) else merged
    // 随机模式：仅对新增照片随机插入，不打乱已有顺序
    _uiState.value = _uiState.value.copy(photos = capped)
}
```
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 照片列表有合理上限（如 1000 张）
- [ ] 随机模式不对已有列表重新 shuffle
- [ ] 当前播放位置不因新照片而跳变

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Performance agent flagged |
