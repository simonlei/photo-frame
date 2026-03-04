---
status: pending
priority: p1
issue_id: "006"
tags: [code-review, android, kotlin, initialization, race-condition]
dependencies: []
---

# P1: ApiClient lazy 初始化时序问题（初始化竞态）

## Problem Statement

`ApiClient.kt` 使用 `object` 单例 + `by lazy` 延迟初始化 `service` 属性。`lazy` 委托在首次访问时才创建 `Retrofit` 实例，此时会捕获 `baseUrl` 的当前值。

**核心问题：** 如果任何代码在 `ApiClient.init(serverUrl)` 被调用之前访问了 `ApiClient.service`（例如在 `Application.onCreate()` 之前的某个生命周期回调中），`Retrofit` 将以默认值 `"https://your-server.com/"` 初始化，之后再调用 `init()` 修改 `baseUrl` 字段也**不会**影响已创建的 `Retrofit` 实例，导致所有 API 请求全部发往错误地址，且这个 bug 极难调试（只在特定启动顺序下触发）。

## Findings

**受影响文件:** `android/app/src/main/java/com/simonlei/photoframe/data/ApiClient.kt`

```kotlin
object ApiClient {
    private var baseUrl: String = "https://your-server.com/"  // 默认占位值

    fun init(url: String) {
        baseUrl = url  // 修改 baseUrl...
        // ...但如果 service 已被 lazy 初始化，修改无效！
    }

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)  // 捕获 init() 时的 baseUrl 快照
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
```

**触发场景举例：**
- `BootReceiver` 在系统启动时被调用，在 `Application.onCreate()` 之前访问 `ApiClient.service`
- Fragment/Activity 在 `onViewCreated` 中访问 `service`，但 `init()` 在 `Application.onCreate()` 中才调用

## Proposed Solutions

### 方案 A（推荐）：在 Application.onCreate() 中立即初始化
在 `PhotoFrameApplication` 的 `onCreate()` 中调用 `ApiClient.init()`，确保在任何组件访问前完成初始化：

```kotlin
// PhotoFrameApplication.kt
class PhotoFrameApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val serverUrl = AppPrefs(this).serverUrl
        ApiClient.init(serverUrl)
    }
}
```

同时在 `ApiClient` 中添加防御性校验：
```kotlin
val service: ApiService by lazy {
    check(baseUrl != "https://your-server.com/") {
        "ApiClient.init() must be called before accessing service"
    }
    Retrofit.Builder().baseUrl(baseUrl)...build()
}
```

- 优点：简单可靠，在 Application 层面保证初始化顺序
- 缺点：需要确保 `PhotoFrameApplication` 已在 `AndroidManifest.xml` 中注册
- 风险：低

### 方案 B：改用非 lazy 的重建模式
放弃 `lazy`，改为在 `init()` 时直接重建 Retrofit 实例：

```kotlin
object ApiClient {
    private var _service: ApiService? = null

    val service: ApiService
        get() = _service ?: error("ApiClient.init() not called")

    fun init(url: String) {
        _service = Retrofit.Builder()
            .baseUrl(url)
            ...
            .build()
            .create(ApiService::class.java)
    }
}
```

- 优点：`init()` 调用时机可以更灵活，可以在用户修改服务器地址时重新初始化
- 缺点：多线程访问 `_service` 时需要同步
- 风险：低

### 方案 C：Hilt/Koin 依赖注入
使用依赖注入框架管理 `Retrofit` 的生命周期和初始化顺序：
- 优点：长期维护性好，解决所有初始化顺序问题
- 缺点：引入额外依赖，改动量大，对简单项目过度工程化
- 风险：中等（改动大）

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `android/app/src/main/java/com/simonlei/photoframe/data/ApiClient.kt` - 核心问题
- `android/app/src/main/java/com/simonlei/photoframe/PhotoFrameApplication.kt`（如不存在则需创建）
- `android/app/src/main/AndroidManifest.xml` - 确认 `android:name` 指向 Application 类

## Acceptance Criteria

- [ ] `ApiClient.service` 在首次访问前必定已完成正确初始化
- [ ] 若 `init()` 未被调用，访问 `service` 时抛出有意义的异常提示
- [ ] 在 `BootReceiver` 场景下（系统重启后自动启动）`ApiClient` 正常工作
- [ ] 修改服务器地址后，新的 `ApiClient` 使用更新后的 URL

## Work Log

- 2026-03-04: code-review 发现，由 code-simplicity-reviewer 和 architecture-strategist 代理报告

## Resources

- 相关代码: `android/app/src/main/java/com/simonlei/photoframe/data/ApiClient.kt`
- Kotlin lazy 委托: https://kotlinlang.org/docs/delegated-properties.html#lazy-properties
