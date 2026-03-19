---
status: pending
priority: p2
issue_id: "060"
tags:
  - code-review
  - architecture
  - android
dependencies: []
---

# Glide AuthGlideModule 初始化时快照 Token，Token 刷新后图片加载 401

## Problem Statement

`AuthGlideModule.registerComponents()` 在 Glide 初始化时取一次 `AppPrefs(context).userToken`，之后 OkHttpClient 拦截器一直使用该快照值。用户重新绑定导致 token 变化后，Glide 仍使用旧 token，图片加载持续 401 直到进程重启。

## Findings

- **AuthGlideModule.kt:22** — `val token = AppPrefs(context).userToken` 在初始化时快照

## Proposed Solutions

### Option A: 拦截器每次请求动态读取 token（推荐）
```kotlin
Interceptor { chain ->
    val token = AppPrefs(context).userToken
    val req = if (token != null) {
        chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
    } else chain.request()
    chain.proceed(req)
}
```
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 重新绑定后图片加载正常（无需杀进程）

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Architecture agent flagged |
