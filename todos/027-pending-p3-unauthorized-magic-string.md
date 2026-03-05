---
status: pending
priority: p3
issue_id: "027"
tags: [code-review, miniprogram, maintainability, code-quality]
---

# [P3] '未登录' 魔法字符串硬编码在多个 catch 块，与 api.js 耦合

## Problem Statement

`api.js` 第 34 行抛出 `new Error('未登录')`，页面侧通过 `e.message !== '未登录'` 来决定是否显示 toast。这个字符串散落在 3 个文件的 4 个位置，若 api.js 文案改变，所有过滤逻辑静默失效。

## Findings

- `api.js:34` — 错误消息定义
- `index.js:20` — 魔法字符串比较
- `manage.js:25,49` — 同上（两处）

## Proposed Solutions

```js
// utils/api.js 增加导出
const ERR_UNAUTHORIZED = 'ERR_UNAUTHORIZED'

// reject 时设置 code
const err = new Error('未登录')
err.code = ERR_UNAUTHORIZED
reject(err)

// 导出
module.exports = { request, authHeader, getFileSize, ERR_UNAUTHORIZED }

// 各页面
const { request, ERR_UNAUTHORIZED } = require('../../utils/api')
// ...
if (e.code !== ERR_UNAUTHORIZED) {
  wx.showToast({ ... })
}
```

## Acceptance Criteria

- [ ] `ERR_UNAUTHORIZED` 常量从 `api.js` 导出
- [ ] 各页面 catch 块使用 `e.code !== ERR_UNAUTHORIZED` 而非字符串比较
- [ ] 修改 api.js 的错误文案不影响各页面过滤逻辑

## Work Log

- 2026-03-04: 由 architecture-strategist 和 code-simplicity-reviewer 发现
