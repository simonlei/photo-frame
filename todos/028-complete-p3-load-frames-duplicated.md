---
status: pending
priority: p3
issue_id: "028"
tags: [code-review, miniprogram, code-quality, maintainability]
---

# [P3] _loadFrames 逻辑在 index.js 和 manage.js 中完全重复

## Problem Statement

`index.js` 和 `manage.js` 的 `_loadFrames` 方法逻辑几乎完全相同（调用 `/api/my/frames`，处理错误），唯一差别是 `manage.js` 多了一步 `_loadPhotos`。这违反 DRY 原则，将来修改接口 URL 或错误处理逻辑需要同步修改两处。

## Proposed Solutions

将 `fetchMyFrames` 提取到 `utils/api.js`：

```js
// utils/api.js 新增
async function fetchMyFrames() {
  const app = getApp()
  const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
  return data.frames || []
}
module.exports = { request, authHeader, getFileSize, ERR_UNAUTHORIZED, fetchMyFrames }

// index.js
const { fetchMyFrames, ERR_UNAUTHORIZED } = require('../../utils/api')
async _loadFrames() {
  this.setData({ loading: true })
  try {
    const frames = await fetchMyFrames()
    this.setData({ frames })
  } catch (e) {
    if (e.code !== ERR_UNAUTHORIZED) wx.showToast({ title: e.message || '加载失败', icon: 'none' })
  } finally {
    this.setData({ loading: false })
  }
}
```

## Acceptance Criteria

- [ ] `fetchMyFrames` 从 `utils/api.js` 导出
- [ ] `index.js` 和 `manage.js` 调用同一函数
- [ ] 两处行为与现有一致

## Work Log

- 2026-03-04: 由 code-simplicity-reviewer 发现
