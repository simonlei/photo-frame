---
status: pending
priority: p2
issue_id: "012"
tags: [code-review, miniprogram, typescript, error-handling]
dependencies: []
---

# P2: 小程序 API 函数未检查 HTTP 状态码（静默失败）

## Problem Statement

`miniprogram/src/api/index.ts` 中的 `getMyFrames()` 和 `getPhotos()` 等函数使用 `uni.request` 封装，但只处理 `fail` 回调（网络级错误），未检查 HTTP 状态码（如 400、401、403、500）。当服务端返回错误状态码时，Promise 仍会 resolve，调用方收到的是错误响应体数据，而非 rejected Promise，导致：
1. 调用方无法区分成功和失败
2. 错误被静默忽略，用户看不到任何提示
3. 数据被当成正常数据处理，可能导致 UI 显示异常

## Findings

**受影响文件:** `miniprogram/src/api/index.ts`

```typescript
// 典型问题示例
export function getMyFrames(): Promise<any[]> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/my/frames`,
      header: { Authorization: `Bearer ${token}` },
      success: (res) => {
        // 问题：只解构 data，未检查 res.statusCode
        resolve((res.data as any).frames || [])
      },
      fail: reject  // 只处理网络错误
    })
  })
}
// 当服务器返回 401/403 时，success 回调仍然被调用
// res.data 是 { "error": "..." }，但 .frames 为 undefined，resolve([]) 静默失败
```

**同时发现:** `BASE_URL` 硬编码在源码中，不同环境（开发/生产）需要手动修改：
```typescript
const BASE_URL = 'https://your-server.com'  // 硬编码
```

## Proposed Solutions

### 方案 A（推荐）：封装统一 request 函数，检查状态码
```typescript
function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      ...options,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data as T)
        } else if (res.statusCode === 401) {
          // Token 失效，跳转登录
          uni.navigateTo({ url: '/pages/login/index' })
          reject(new Error('请重新登录'))
        } else {
          reject(new Error((res.data as any)?.error || `请求失败 (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg))
    })
  })
}
```

同时通过 `uni.getStorageSync('SERVER_URL')` 或小程序管理后台配置 `BASE_URL`，避免硬编码。

- 优点：消除所有函数中的重复状态码检查，统一错误处理，减少代码重复
- 缺点：需要重构现有5个 API 函数
- 风险：低

### 方案 B：逐个函数修复
在每个函数中添加状态码检查：
```typescript
success: (res) => {
  if (res.statusCode !== 200) {
    reject(new Error((res.data as any)?.error || '请求失败'))
    return
  }
  resolve(...)
}
```
- 优点：改动范围最小，风险低
- 缺点：重复代码，5个函数各自修改
- 风险：极低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `miniprogram/src/api/index.ts` - 所有 API 函数

**影响的函数:** `getMyFrames`, `getPhotos`, `deletePhoto`, `bindFrame`, `uploadPhoto`（共5个）

## Acceptance Criteria

- [ ] 所有 API 函数在 HTTP 4xx/5xx 时返回 rejected Promise
- [ ] 401 响应触发自动重新登录流程
- [ ] `BASE_URL` 可通过配置或环境变量设置，不硬编码
- [ ] 调用方 catch 到的错误有明确的 message

## Work Log

- 2026-03-04: code-review 发现，由 code-simplicity-reviewer 和 architecture-strategist 代理报告
