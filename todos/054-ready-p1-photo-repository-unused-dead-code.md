---
status: ready
priority: p1
issue_id: "054"
tags:
  - code-review
  - architecture
  - android
  - dead-code
dependencies: []
---

# PhotoRepository 接口和实现已创建但未被任何代码使用

## Problem Statement

本次 PR 创建了 `PhotoRepository` 接口和 `RemotePhotoRepository` 实现，但 `PhotoSyncService`（实际负责照片拉取的组件）完全没有使用它们，仍然直接调用 `ApiClient.service.listPhotos()` 并手动做 DTO→Domain 映射。这意味着 Repository 层是死代码，且 URL 拼接逻辑在两处重复。

## Findings

1. **PhotoRepository.kt** — 定义了接口但无代码引用
2. **RemotePhotoRepository.kt** — 31 行实现代码，零调用方
3. **PhotoSyncService.kt:57-92** — 直接调用 `ApiClient.service.listPhotos()`，手动做相同的 DTO→Photo 映射和 URL 拼接
4. **URL baseUrl 来源不一致:**
   - `PhotoSyncService` 使用 `context.getString(R.string.server_base_url)`（编译时默认值）
   - `RemotePhotoRepository` 使用构造参数 `serverBaseUrl`
   - 用户修改服务器地址后，`PhotoSyncService` 可能仍使用旧的默认值

## Proposed Solutions

### Option A: 让 PhotoSyncService 使用 PhotoRepository（推荐）
1. `PhotoSyncService` 通过构造参数接收 `PhotoRepository` 实例
2. 删除 service 内的重复映射逻辑
3. 将 `baseUrl` 改为读取 `prefs.serverBaseUrl`
- **Pros:** 消除死代码和重复，统一数据路径，修复 URL 来源 bug
- **Cons:** 需要修改 PhotoSyncService 构造方式
- **Effort:** Medium
- **Risk:** Low

### Option B: 删除未使用的 Repository
- 删除 `PhotoRepository.kt` 和 `RemotePhotoRepository.kt`
- **Pros:** 消除死代码
- **Cons:** 失去了重构方向，后续需要重新创建
- **Effort:** Small
- **Risk:** Low

## Recommended Action

(Filled during triage)

## Technical Details

**Affected Files:**
- `android/app/src/main/java/com/photoframe/data/PhotoRepository.kt` (unused)
- `android/app/src/main/java/com/photoframe/data/RemotePhotoRepository.kt` (unused)
- `android/app/src/main/java/com/photoframe/service/PhotoSyncService.kt` (has duplicate logic)

## Acceptance Criteria

- [ ] `PhotoRepository` 接口有至少一个调用方
- [ ] URL 拼接逻辑只在一个位置存在
- [ ] `baseUrl` 来源为 `AppPrefs.serverBaseUrl`（而非 `R.string.server_base_url`）

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Architecture + Simplicity agents both flagged |

## Resources

- PR: refactor/android-test-automation
