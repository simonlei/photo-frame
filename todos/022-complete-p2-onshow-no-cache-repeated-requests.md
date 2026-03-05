---
status: pending
priority: p2
issue_id: "022"
tags: [code-review, miniprogram, performance, ux]
---

# [P2] onShow 每次 Tab 切换都发出网络请求，无任何缓存策略

## Problem Statement

`index.js` 和 `manage.js` 均在 `onShow` 中无条件发起 `/api/my/frames` 请求。用户每次切换 Tab 触发 1-2 个网络请求，弱网环境下每次切换有 200-2000ms 加载延迟。此外 `manage.js` 的 `onShow` 会重置 `currentFrameIdx: 0`，用户切换到其他相框后，切换回来会丢失选择状态并重新加载第一个相框的照片。

## Findings

- `index.js:9-11` — `onShow` 无条件调用 `_loadFrames()`
- `manage.js:11-13` — 同上，且触发级联 `_loadPhotos()`
- `manage.js:17-20` — `setData({ frames, currentFrameIdx: 0 })` 重置选择状态
- 两个页面对同一接口发出独立请求，无共享缓存

## Proposed Solutions

### Option A: app.globalData 缓存 + TTL（推荐）

```js
// app.js globalData 增加
framesCache: null,
framesCacheTime: 0,

// 公共函数（可放 utils/api.js）
async function getFrames(force = false) {
  const app = getApp()
  const ttl = 30 * 1000  // 30秒
  if (!force && app.globalData.framesCache && Date.now() - app.globalData.framesCacheTime < ttl) {
    return app.globalData.framesCache
  }
  const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
  app.globalData.framesCache = data.frames || []
  app.globalData.framesCacheTime = Date.now()
  return app.globalData.framesCache
}
```

- `bind.js` 绑定成功后调用 `getApp().globalData.framesCache = null` 使缓存失效
- `manage.js` 记住 `currentFrameIdx`，`onShow` 时若缓存有效不重置

## Acceptance Criteria

- [ ] 30 秒内切换 Tab 不发出新网络请求
- [ ] 绑定新相框后，下次进入相框列表能看到新相框
- [ ] `manage.js` 切换 Tab 后返回，仍显示上次选择的相框

## Work Log

- 2026-03-04: 由 performance-oracle 和 architecture-strategist 发现
