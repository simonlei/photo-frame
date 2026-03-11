---
status: pending
priority: p2
issue_id: "045"
tags: [code-review, security, android]
dependencies: []
---

# BindActivity 日志将 user_token 明文写入 Android logcat

## Problem Statement

`BindActivity` 轮询 `/api/device/bind-status` 时，将完整的响应体（含 `user_token`）明文写入 Android 系统日志。`user_token` 是所有受保护 API（照片上传/删除/列表）的凭证，任何可运行 `adb logcat` 的人（USB 调试模式或开发者选项开启时）均可读取。相框设备通常长期通电、放置于客厅，开发者选项可能被打开。

## Findings

**位置：** `android/app/src/main/java/com/photoframe/BindActivity.kt` 第 123-125 行

```kotlin
Log.d("BindActivity", "poll response <- ${resp.code} ${resp.message}, headers=${resp.headers}")
Log.d("BindActivity", "poll response body: $bodyStr")   // ← bodyStr 在绑定成功时含 user_token
```

绑定成功时 `bodyStr` 格式为：
```json
{"bound": true, "user_token": "abc123..."}
```

第 125 行直接将整个 `bodyStr` 写入日志，包括 `user_token` 的完整值。

## Proposed Solutions

### 方案 A：绑定成功后不记录响应体（推荐）
```kotlin
Log.d("BindActivity", "poll response <- ${resp.code} ${resp.message}")
// 不记录 bodyStr；或仅记录 bound 字段
if (bound) {
    Log.d("BindActivity", "poll parsed: bound=true, device=$deviceId")
    // 不记录 token
}
```
- **优点：** 完全消除敏感数据泄露，改动最小
- **缺点：** 稍微降低调试信息量

### 方案 B：屏蔽 token 字段后记录
```kotlin
Log.d("BindActivity", "poll response body: ${bodyStr?.replace(Regex("\"user_token\":\"[^\"]+\""), "\"user_token\":\"[MASKED]\"")}")
```
- **优点：** 保留更多调试信息
- **缺点：** 稍复杂，regex 替换有遗漏风险

### 方案 C：仅在 DEBUG build 记录
```kotlin
if (BuildConfig.DEBUG) {
    Log.d("BindActivity", "poll response body: $bodyStr")
}
```
- **优点：** Release build 自动消除
- **缺点：** 需确认 Release build 配置，且开发机上仍会泄露

## Recommended Action

方案 A — 直接删除第 125 行的 `bodyStr` 日志输出，保留第 123 行的状态码日志即可。

## Technical Details

- **受影响文件：** `android/app/src/main/java/com/photoframe/BindActivity.kt:125`
- **敏感字段：** `user_token`（完整 Bearer Token）
- **攻击向量：** USB 调试模式 + `adb logcat | grep BindActivity`

## Acceptance Criteria

- [ ] `user_token` 不出现在任何 `Log.*` 调用的输出中
- [ ] 调试日志中不含完整响应体（当响应包含 token 时）
- [ ] 状态码和绑定状态（bound=true/false）等非敏感信息可保留

## Work Log

- 2026-03-11: 发现于 PR #3 代码审查，security-sentinel agent 标识为 HIGH
