---
status: pending
priority: p1
issue_id: "005"
tags: [code-review, android, resource-leak, kotlin]
dependencies: []
---

# P1: BindActivity 中 OkHttp ResponseBody 未关闭（连接泄漏）

## Problem Statement

`BindActivity.kt` 中使用了原生 `OkHttpClient` 发起 HTTP 请求，但在读取响应体后未调用 `response.close()`，导致底层 TCP 连接无法被连接池回收，造成：
1. 连接泄漏，随着轮询次数增加，挂起连接堆积
2. 系统 socket 资源耗尽（特别是在 Android 资源受限环境）
3. OkHttp 连接池失效，后续请求建立新连接增加延迟

此外，`BindActivity` 与主模块 `ApiClient`（Retrofit 封装）并行存在两套 HTTP 客户端，架构不统一，增加维护成本。

## Findings

**受影响文件:** `android/app/src/main/java/com/simonlei/photoframe/BindActivity.kt`

```kotlin
// BindActivity.kt 约第60-75行：
val client = OkHttpClient()  // 每次轮询创建新实例（严重问题）
val request = Request.Builder()
    .url("$BASE_URL/api/device/bind-status?device_id=$deviceId")
    .build()

val resp = client.newCall(request).execute()
val body = resp.body!!.string()  // 读取后未调用 resp.close()
// 连接泄漏：resp 和 resp.body 都应在 finally 块中关闭
```

**架构问题：** 应使用 `ApiClient.service` 而非直接使用 `OkHttpClient`。

## Proposed Solutions

### 方案 A（推荐）：迁移到统一的 Retrofit ApiClient
在 `ApiService` 接口中添加 `bindStatus` 方法，`BindActivity` 使用 `ApiClient.service` 调用：

```kotlin
// ApiService.kt 中添加：
@GET("api/device/bind-status")
suspend fun getBindStatus(@Query("device_id") deviceId: String): Response<BindStatusResponse>

// BindActivity.kt 中替换为：
val response = ApiClient.service.getBindStatus(deviceId)
if (response.isSuccessful && response.body()?.bound == true) {
    // 绑定成功
}
// Retrofit 自动管理连接生命周期，无需手动关闭
```

- 优点：消除泄漏，架构统一，Retrofit 自动处理连接管理
- 缺点：需要修改两个文件
- 风险：低

### 方案 B：修复 OkHttp 使用方式（保留但修正）
若暂时不迁移，至少修复泄漏：

```kotlin
// 使用 use 扩展函数自动关闭
val client = OkHttpClient()
val request = Request.Builder().url(...).build()
client.newCall(request).execute().use { resp ->
    if (!resp.isSuccessful) return@use
    val bound = JSONObject(resp.body!!.string()).getBoolean("bound")
    // 处理逻辑
}
// use 块结束时自动调用 resp.close()
```

此外，`OkHttpClient()` 应为单例，不应在每次轮询时创建新实例。

- 优点：最小改动
- 缺点：仍存在两套 HTTP 客户端的架构问题
- 风险：低

### 方案 C：使用 Kotlin coroutine + Retrofit suspend 函数
BindActivity 使用 `lifecycleScope.launch` + suspend 函数，彻底现代化：
```kotlin
lifecycleScope.launch {
    while (isActive) {
        val status = ApiClient.service.getBindStatus(deviceId)
        if (status.bound) { onBound(); break }
        delay(3000)
    }
}
```
- 优点：协程自动绑定生命周期，无泄漏风险
- 缺点：需要理解协程生命周期
- 风险：低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `android/app/src/main/java/com/simonlei/photoframe/BindActivity.kt` - 约第 60-80 行
- `android/app/src/main/java/com/simonlei/photoframe/data/ApiClient.kt` - 需添加新接口
- `android/app/src/main/java/com/simonlei/photoframe/data/ApiService.kt`（如有）- 需添加 bindStatus 方法

**内存/连接影响:** BindActivity 默认轮询间隔 3 秒，若泄漏未修复，30 分钟内可积累约 600 个挂起连接。

## Acceptance Criteria

- [ ] `BindActivity` 中不再使用独立的 `OkHttpClient` 实例
- [ ] 所有 HTTP 响应体在读取后被正确关闭
- [ ] `BindActivity` 使用 `ApiClient` 统一管理 HTTP 通信
- [ ] 使用 Android Profiler 或 LeakCanary 验证无连接/内存泄漏

## Work Log

- 2026-03-04: code-review 发现，由 code-simplicity-reviewer 代理报告

## Resources

- 相关代码: `android/app/src/main/java/com/simonlei/photoframe/BindActivity.kt`
- OkHttp 资源泄漏说明: https://square.github.io/okhttp/recipes/#synchronous-get-kt-java
