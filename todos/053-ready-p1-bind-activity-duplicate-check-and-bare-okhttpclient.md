---
status: ready
priority: p1
issue_id: "053"
tags:
  - code-review
  - architecture
  - security
  - android
dependencies: []
---

# BindActivity 绕过 ApiClient 使用裸 OkHttpClient + 冗余前置检查

## Problem Statement

本次 PR 引入了两个相关的架构问题：
1. `BindActivity` 创建裸 `OkHttpClient()` 实例，绕过了应用中统一的 `ApiClient` 网络配置
2. `BindActivity.onCreate()` 中有一段绑定状态前置检查，与 `BindViewModel.checkBindingStatus()` 中的检查逻辑重复且条件不一致

## Findings

### Finding 1: 裸 OkHttpClient
- **BindActivity.kt:39** — `RemoteDeviceRepository(OkHttpClient())`
- 裸 `OkHttpClient()` 无日志拦截器、无超时配置、无 SSL 定制、无连接池共享
- 与 `ApiClient` 中经过配置的 client 形成两条并行的 HTTP 路径
- 未来添加 certificate pinning 等安全措施时 Bind 流程会被遗漏

### Finding 2: 冗余前置检查
- **BindActivity.kt:42-46** — `if (prefs.isBound && prefs.deviceId != null && prefs.userToken != null)`
- **BindViewModel.kt:37-39** — `if (prefs.isBound && !prefs.userToken.isNullOrEmpty())`
- Activity 多检查了 `deviceId`，ViewModel 没有
- Activity 用 `!= null`，ViewModel 用 `isNullOrEmpty`（空字符串行为不同）
- Activity 前置检查跳过时未调用 `setContentView()`，ViewModel 被创建但未使用

## Proposed Solutions

### Option A: 统一网络客户端 + 删除 Activity 前置检查（推荐）
1. 在 `ApiClient` 中暴露一个 base `OkHttpClient`（不带 Token 拦截器但带日志/超时/SSL 配置）
2. `RemoteDeviceRepository` 使用该 base client
3. 删除 `BindActivity` 中的前置检查，统一由 ViewModel 管理
4. Activity 只 observe `uiState`，收到 `AlreadyBound` 时执行 `goMain()`
- **Pros:** 统一网络层，消除逻辑重复，符合 MVVM
- **Cons:** 需修改 ApiClient 暴露接口
- **Effort:** Medium
- **Risk:** Low

### Option B: 仅修复前置检查一致性
- 让 Activity 和 ViewModel 使用相同的判断条件
- **Pros:** 最小改动
- **Cons:** 裸 OkHttpClient 问题仍存在
- **Effort:** Small
- **Risk:** Low

## Recommended Action

(Filled during triage)

## Technical Details

**Affected Files:**
- `android/app/src/main/java/com/photoframe/BindActivity.kt` (lines 39, 42-46)
- `android/app/src/main/java/com/photoframe/viewmodel/BindViewModel.kt` (lines 37-39)
- `android/app/src/main/java/com/photoframe/data/ApiClient.kt`

## Acceptance Criteria

- [ ] 只有一条 OkHttpClient 配置路径（或共享 base client）
- [ ] 绑定状态判断逻辑只在 ViewModel 中存在
- [ ] Activity 只做 UI 渲染和导航

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Multiple agents flagged this |

## Resources

- PR: refactor/android-test-automation
