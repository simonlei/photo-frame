---
status: pending
priority: p2
issue_id: "057"
tags:
  - code-review
  - security
  - android
dependencies: []
---

# RemoteDeviceRepository checkBindStatus 中 deviceId 未经 URL 编码直接拼接

## Problem Statement

`RemoteDeviceRepository.checkBindStatus()` 将 `deviceId` 直接拼入 URL 查询参数字符串，未使用 URL 编码。在 rooted 设备上，SharedPreferences 中的 `device_id` 可被篡改，导致参数注入。

## Findings

- **RemoteDeviceRepository.kt:35** — `val url = "$serverBaseUrl/api/device/bind-status?device_id=$deviceId"`
- 未使用 `URLEncoder.encode()` 或 `HttpUrl.Builder`
- **RemoteDeviceRepository.kt:22-24** — body 和 status code 检查顺序有 bug（先读 body 再检查 isSuccessful）

## Proposed Solutions

### Option A: 使用 OkHttp HttpUrl.Builder（推荐）
```kotlin
val url = serverBaseUrl.toHttpUrl().newBuilder()
    .addPathSegments("api/device/bind-status")
    .addQueryParameter("device_id", deviceId)
    .build()
```
同时修复 registerDevice 中的 status code 检查顺序。
- **Effort:** Small | **Risk:** Low

### Option B: 迁移到 Retrofit（长期）
- 统一使用 Retrofit 接口，自动处理 URL 编码
- **Effort:** Medium | **Risk:** Low

## Acceptance Criteria

- [ ] 所有 URL 参数使用安全的构建方式
- [ ] registerDevice 先检查 status code 再解析 body

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Security + Simplicity agents flagged |
