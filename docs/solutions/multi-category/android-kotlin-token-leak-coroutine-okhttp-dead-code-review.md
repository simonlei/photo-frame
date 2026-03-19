---
title: "Code Review Fix: Android Photo Frame P1 Issues (#051–#054)"
category: multi-category
date: 2026-03-19
branch: refactor/android-test-automation
tags:
  - android
  - kotlin
  - security
  - coroutine
  - code-review
  - P1
  - token-leak
  - cancellation-exception
  - dead-code
  - okhttp
  - repository-pattern
affected_components:
  - BindActivity.kt
  - BindViewModel.kt
  - ApiClient.kt
  - PhotoSyncService.kt
  - MainActivity.kt
  - BindViewModelTest.kt
severity: P1
problem_type:
  - security/information-leak
  - concurrency/coroutine-misuse
  - architecture/misconfiguration
  - maintainability/dead-code
related_docs:
  - docs/solutions/multi-category/android-qrcode-main-thread-token-leak-config-issues.md
  - docs/solutions/multi-category/photo-frame-comprehensive-code-review.md
  - todos/045-complete-p2-logcat-user-token-leak.md
  - todos/005-complete-p1-bindactivity-response-body-leak.md
---

# Code Review Fix: Android P1 Issues (#051–#054)

在 `refactor/android-test-automation` 分支的代码审查中发现并修复了 4 个 P1 阻塞合并问题。这些问题涵盖安全（token 泄露）、并发（协程取消异常）、架构（OkHttpClient 配置散落）、可维护性（Repository 死代码）四个维度。

## 问题总览

| # | Todo | 类型 | 严重度 | 文件 |
|---|------|------|--------|------|
| 1 | #051 | Token 泄露到 Logcat | P1 | `ApiClient.kt`, `BindActivity.kt` |
| 2 | #052 | 轮询吞掉 CancellationException | P1 | `BindViewModel.kt` |
| 3 | #053 | 裸 OkHttpClient + 冗余前置检查 | P1 | `ApiClient.kt`, `BindActivity.kt` |
| 4 | #054 | PhotoRepository 死代码 / 逻辑重复 | P1 | `PhotoSyncService.kt`, `MainActivity.kt` |

---

## 问题 1: Token 泄露到 Logcat (#051)

### 症状

`ApiClient.init()`、`ApiClient.reinit()` 和 `BindActivity.goMain()` 中将 token 前 8 位通过 `token.take(8)` 输出到 Android Logcat。在生产设备上通过 `adb logcat` 即可获取用户认证凭据片段。

### 根因

开发调试时为观察 token 状态而添加的日志，使用了 `take(8)` 截断 token 值。截断后的 token 仍可能被用于暴力破解或收窄攻击面。**这是一个回归问题**——`todo-045` 已修复过同类 token 日志泄露，但新代码重新引入了该反模式。

### 解决方案

将所有 token 日志替换为布尔存在性检查：

```kotlin
// ❌ BEFORE — 泄露 token 前缀
Log.d(TAG, "init() token=${if (token != null) "present(${token.take(8)}...)" else "null"}")

// ✅ AFTER — 仅记录存在性
Log.d(TAG, "init() token=${if (token != null) "[PRESENT]" else "null"}")
```

修改了 3 处：
- `ApiClient.kt` — `init()` 和 `reinit()` 方法
- `BindActivity.kt` — `goMain()` 方法

### 为什么正确

用 `"[PRESENT]"` / `"null"` 替代 token 实际内容，既保留了开发调试所需的 token 存在性可观测性，又彻底杜绝了任何 token 字符在日志中的暴露。

---

## 问题 2: 轮询吞掉 CancellationException (#052)

### 症状

`BindViewModel.startPolling()` 中使用 `catch (_: Exception)` 捕获所有异常，包括 `CancellationException`，导致：
1. `pollJob.cancel()` 无法终止协程（协程泄漏）
2. 无限循环无退出机制——若服务器持续不可达，轮询永远运行
3. 无退避策略——固定 3s 间隔持续请求不可用服务器

### 根因

Kotlin 协程的取消机制依赖 `CancellationException` 的正常传播。裸 `catch(Exception)` 违反了协程契约，阻断了取消信号链。

### 解决方案

```kotlin
// ❌ BEFORE — 吞掉所有异常，包括 CancellationException
private fun startPolling(deviceId: String) {
    pollJob = scope.launch {
        while (true) {
            delay(3_000)
            try {
                val status = deviceRepo.checkBindStatus(prefs.serverBaseUrl, deviceId)
                if (status.bound && !status.userToken.isNullOrEmpty()) { ... break }
            } catch (_: Exception) {
                // 静默忽略
            }
        }
    }
}

// ✅ AFTER — 三重保障：CancellationException 重抛 + 退避 + 重试上限
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
            } catch (e: CancellationException) {
                throw e  // 必须重新抛出
            } catch (_: Exception) {
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

新增单元测试 `poll emits Error after max retries` 验证超时退出行为。

### 为什么正确

1. **CancellationException 重新抛出** — 遵守 Kotlin 协程契约，cancel() 时协程立即停止
2. **指数退避** — 连续失败 5 次后从 3s → 10s，减少无意义消耗
3. **最大重试上限** — 约 10 分钟后输出 Error 状态，避免无限循环

---

## 问题 3: 裸 OkHttpClient + 冗余前置检查 (#053)

### 症状

- `BindActivity` 使用 `OkHttpClient()` 裸构造，无日志拦截器、无超时配置
- Activity 中存在与 ViewModel 重复的 `isBound` 检查逻辑

### 根因

`BindActivity` 绕过 `ApiClient` 的配置体系，直接创建 OkHttpClient 实例，导致该请求无日志可观测性、无统一超时策略。Activity 层的 `isBound` 前置检查与 `BindViewModel.checkBindingStatus()` 逻辑重复，违反 MVVM 单一职责。**这也是回归问题**——`todo-005` 修复过 BindActivity 的 OkHttp 问题，新代码又引入了裸客户端。

### 解决方案

**1. ApiClient 暴露共享 base client：**

```kotlin
// ApiClient.kt — 新增
val baseHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()
}
```

**2. BindActivity 使用共享 client + 删除冗余检查：**

```kotlin
// ❌ BEFORE
val deviceRepo = RemoteDeviceRepository(OkHttpClient())
// 冗余检查
if (prefs.isBound && prefs.deviceId != null && prefs.userToken != null) {
    goMain(); return
}

// ✅ AFTER
val deviceRepo = RemoteDeviceRepository(ApiClient.baseHttpClient)
// isBound 检查统一由 ViewModel 管理
```

### 为什么正确

- `baseHttpClient` 是 lazy 单例，所有非认证请求共享连接池和配置
- 移除 Activity 中的 `isBound` 检查，由 ViewModel 统一管理状态（`AlreadyBound` 状态）

---

## 问题 4: PhotoRepository 死代码 / 逻辑重复 (#054)

### 症状

`PhotoSyncService.fetchPhotos()` 直接调用 `ApiClient.service.listPhotos()`，手工拼接 URL 和映射 DTO→Photo，完全复制了 `RemotePhotoRepository` 中已有的逻辑。`PhotoRepository` 接口和 `RemotePhotoRepository` 实现变成死代码。

### 根因

`PhotoSyncService` 先于 Repository 层编写，后来引入 Repository 抽象时未回头重构 Service 层，导致两处独立维护的数据转换代码。

### 解决方案

```kotlin
// ❌ BEFORE — 直接调用 API，重复映射
class PhotoSyncService(
    private val context: Context,
    private val onPhotosUpdated: (List<Photo>) -> Unit
) {
    private suspend fun fetchPhotos(since: String?) {
        val response = ApiClient.service.listPhotos(deviceId, since)
        val photos = response.photos.map { dto ->
            val fullUrl = if (dto.url.startsWith("http")) dto.url else "$baseUrl${dto.url}"
            Photo(id = dto.id, url = fullUrl, ...)
        }
        ...
    }
}

// ✅ AFTER — 通过 Repository 接口获取数据
class PhotoSyncService(
    private val context: Context,
    private val photoRepository: PhotoRepository,
    private val onPhotosUpdated: (List<Photo>) -> Unit
) {
    private suspend fun fetchPhotos(since: String?) {
        val photos = photoRepository.getPhotos(deviceId, since)
        ...
    }
}
```

`MainActivity` 在创建 `PhotoSyncService` 时注入 `RemotePhotoRepository` 实例。

### 为什么正确

1. **单一数据路径** — DTO→领域模型转换集中在 `RemotePhotoRepository`
2. **可测试性** — 构造函数接受接口，可注入 mock
3. **一致性** — 数据结构变更只需改一处

---

## 防范策略

### Code Review Checklist

- [ ] `Log.*` / `Timber.*` 调用中是否包含 token、password、API key 的全部或部分值？
- [ ] 协程上下文中的 `catch(Exception)` 是否先处理了 `CancellationException` 并重新抛出？
- [ ] PR 中是否新增了 `OkHttpClient()` 或 `OkHttpClient.Builder()`？是否从 `ApiClient.baseHttpClient.newBuilder()` 派生？
- [ ] ViewModel / Service 是否直接引用了 `ApiClient.service`？应通过 Repository 接口注入
- [ ] 新增的 DTO→领域模型转换是否与已有 Repository 逻辑重复？

### 推荐的 Lint / 静态分析规则

| 工具 | 规则 | 覆盖问题 |
|------|------|----------|
| Detekt 自定义 | 检测 `Log.*` 中直接引用 `token`/`password`/`secret` 变量 | #051 |
| Detekt 自定义 | 协程 `catch(Exception)` 未重抛 `CancellationException` | #052 |
| Detekt 自定义 | `ApiClient.kt` 外创建 `OkHttpClient.Builder()` | #053 |
| Detekt 自定义 | `viewmodel/`、`service/` 包中 import `ApiClient` 非 `baseHttpClient` | #054 |
| grep CI 脚本 | `rg "token\\.take\|\.take.*token" --type kotlin` | #051 回归检测 |

### 测试建议

1. **Token 日志审计测试** — 使用自定义 Timber.Tree 收集所有日志，断言无 token 子串泄露
2. **协程取消测试** — 启动轮询后调用 `cancel()`，断言后续无新的 API 调用
3. **OkHttpClient 架构测试** — 扫描所有 .kt 文件，断言 `OkHttpClient()` 仅出现在 `ApiClient.kt`
4. **Repository 集成测试** — 注入 mock PhotoRepository，验证 PhotoSyncService 通过接口获取数据

---

## 回归模式观察

**这是本项目中 token 日志泄露和裸 OkHttpClient 问题第二次出现。**

| 问题 | 首次发现 | 首次修复 | 回归引入 | 本次修复 |
|------|---------|---------|---------|---------|
| Token 日志泄露 | #045 (P2) | 2026-03-11 | `refactor/android-test-automation` | #051 (P1) |
| 裸 OkHttpClient | #005 (P1) | 2026-03-04 | `refactor/android-test-automation` | #053 (P1) |

**关键教训：仅靠文档和代码审查无法阻止回归，需要在 CI 层面引入自动化检测规则。**

---

## 交叉引用

- [android-qrcode-main-thread-token-leak-config-issues.md](../multi-category/android-qrcode-main-thread-token-leak-config-issues.md) — 首次 token 泄露修复和日志安全规则表
- [photo-frame-comprehensive-code-review.md](../multi-category/photo-frame-comprehensive-code-review.md) — SafeResourceUse 模式（OkHttp）、CoroutineScope 取消模式
- [todo-045](../../todos/045-complete-p2-logcat-user-token-leak.md) — 首次 logcat token 泄露修复
- [todo-005](../../todos/005-complete-p1-bindactivity-response-body-leak.md) — 首次 OkHttp ResponseBody 泄漏修复

---

## 当前代码中仍需关注的点

| 文件 | 问题 | 严重度 |
|------|------|--------|
| `PhotoSyncService.kt:31,41` | `catch(e: Exception)` 未重抛 `CancellationException` | 中 |
| `AuthGlideModule.kt:23` | 独立创建 `OkHttpClient.Builder()` 未复用 `baseHttpClient` | 低 |
| `AutoUpdater.kt:31` | 直接调用 `ApiClient.service`，绕过 Repository 抽象 | 低 |
| `AutoUpdater.kt:43` | 协程中 `catch(Exception)` 未处理 `CancellationException` | 中 |
