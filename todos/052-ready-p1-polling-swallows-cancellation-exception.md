---
status: ready
priority: p1
issue_id: "052"
tags:
  - code-review
  - performance
  - architecture
  - android
  - coroutines
dependencies: []
---

# BindViewModel 轮询 catch 吞掉 CancellationException，阻止协程取消

## Problem Statement

`BindViewModel.startPolling()` 中的 `catch (_: Exception)` 捕获了所有异常，包括 Kotlin 协程的 `CancellationException`。这违反了协程结构化并发的核心规则：`CancellationException` 必须被重新抛出以允许协程正常取消。

## Findings

1. **BindViewModel.kt:80** — `catch (_: Exception) { // 轮询失败静默忽略，继续重试 }`
   - `CancellationException` 被吞掉后，`pollJob?.cancel()` 在 `onCleared()` 中无法立即终止循环
   - 协程会在下一轮 `delay(3_000)` 处重新抛出 `CancellationException`，但此时已多执行了一次网络请求
   - 在 ViewModel 已销毁后继续操作 `_uiState` 和 `prefs`

2. **无最大重试次数** — `while(true)` 无退出条件（除绑定成功），若服务器永不返回 `bound=true`，轮询永不停止

3. **无退避策略** — 固定 3 秒间隔，网络异常时不加长等待，浪费电量和带宽

## Proposed Solutions

### Option A: 修复异常处理 + 添加退避 + 最大重试（推荐）
```kotlin
private fun startPolling(deviceId: String) {
    pollJob?.cancel()
    pollJob = scope.launch {
        var consecutiveFailures = 0
        val maxRetries = 200 // ~10 分钟
        while (true) {
            val delayMs = if (consecutiveFailures > 5) 10_000L else 3_000L
            delay(delayMs)
            try {
                val status = deviceRepo.checkBindStatus(prefs.serverBaseUrl, deviceId)
                consecutiveFailures = 0
                if (status.bound && !status.userToken.isNullOrEmpty()) {
                    prefs.userToken = status.userToken
                    prefs.isBound = true
                    _uiState.value = BindUiState.BindSuccess
                    break
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                consecutiveFailures++
                if (consecutiveFailures >= maxRetries) {
                    _uiState.value = BindUiState.Error("轮询超时，请检查网络后重试")
                    break
                }
            }
        }
    }
}
```
- **Pros:** 修复核心 bug、添加退避、限制重试
- **Cons:** 改动较大，需更新测试
- **Effort:** Medium
- **Risk:** Low

### Option B: 最小修复（仅重抛 CancellationException）
```kotlin
catch (e: Exception) {
    if (e is CancellationException) throw e
    // 继续重试
}
```
- **Pros:** 一行修复核心 bug
- **Cons:** 无退避和重试上限问题仍存在
- **Effort:** Small
- **Risk:** Low

## Recommended Action

(Filled during triage)

## Technical Details

**Affected Files:**
- `android/app/src/main/java/com/photoframe/viewmodel/BindViewModel.kt` (lines 67-85)

**Tests to update:**
- `BindViewModelTest.kt` — 需新增"始终失败后超时退出"测试场景

## Acceptance Criteria

- [ ] `CancellationException` 被正确重新抛出
- [ ] 协程取消后不再发出额外网络请求
- [ ] 连续失败后有合理退出机制
- [ ] 测试覆盖"重试超时"场景

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Kotlin coroutines: never swallow CancellationException |

## Resources

- PR: refactor/android-test-automation
- [Kotlin Coroutines Best Practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
