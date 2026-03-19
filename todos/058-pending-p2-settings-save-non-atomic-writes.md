---
status: pending
priority: p2
issue_id: "058"
tags:
  - code-review
  - architecture
  - performance
  - android
dependencies: []
---

# SettingsViewModel.saveSettings 产生 10+ 次独立 SharedPreferences 写盘

## Problem Statement

`AppPrefs` 每个 setter 都创建独立的 `Editor` 并 `apply()`。`saveSettings()` 连续调用 ~10 个 setter，产生 10+ 次独立磁盘写入。如果 app 在写入过程中崩溃，可能只写入部分设置导致不一致状态。

## Findings

1. **AppPrefs.kt** — 每个 `set` 都执行 `prefs.edit().putXxx(v).apply()`
2. **SettingsViewModel.kt:58-76** — 连续调用 ~10 个 setter
3. 服务器地址变更场景下连续 5 个 `null` 赋值清除绑定状态，崩溃可导致半清除状态

## Proposed Solutions

### Option A: AppPrefs 添加 batch write + clearBindingData()（推荐）
```kotlin
// AppPrefs.kt 新增
fun clearBindingData() {
    prefs.edit()
        .putBoolean("is_bound", false)
        .remove("user_token").remove("device_id").remove("qr_token").remove("last_sync_time")
        .apply()
}
fun edit(block: SharedPreferences.Editor.() -> Unit) {
    prefs.edit().apply(block).apply()
}
```
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 单次 saveSettings 只产生 1-2 次 `apply()` 调用
- [ ] clearBindingData 是原子操作

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Performance + Architecture agents flagged |
