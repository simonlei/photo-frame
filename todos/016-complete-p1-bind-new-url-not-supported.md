---
status: pending
priority: p1
issue_id: "016"
tags: [code-review, miniprogram, runtime-crash]
---

# [P1] bind.js 使用了小程序不支持的 `new URL()`，二维码解析逻辑永远失效

## Problem Statement

`pages/bind/bind.js` 第 20 行使用了 `new URL()` 来解析扫码结果：

```js
const url = new URL(rawData.replace('photoframe://', 'https://dummy.com/'))
qrToken = url.searchParams.get('qr_token') || ''
```

微信小程序的 JS 运行环境（V8/JavaScriptCore）**不提供浏览器的 `URL` 全局类**，执行时会抛出 `ReferenceError: URL is not defined`。虽然外层有 `try/catch` 兜底，但这意味着正常解析路径永远不会执行，代码会 fallback 到把整个扫码原始内容当作 qr_token，只是侥幸能工作。

## Findings

- **文件**: `miniprogram/pages/bind/bind.js:20`
- **影响**: 二维码解析逻辑 100% 走 fallback 路径，`photoframe://bind?qr_token=xxx` 格式无法正确提取 token
- **表现**: 如果用户扫描标准格式的二维码，fallback 会把整个 `photoframe://bind?qr_token=<token>` 字符串当作 token 发给服务器，服务器会因 token 不匹配而报错

## Proposed Solutions

### Option A: 正则解析（推荐）

**优点**: 无外部依赖，代码简洁，完全兼容所有基础库版本
**缺点**: 无
**实现**:
```js
function parseQrToken(rawData) {
  if (!rawData || !rawData.startsWith('photoframe://bind')) return ''
  const match = rawData.match(/[?&]qr_token=([^&]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}
```

### Option B: 手动字符串解析

```js
function parseQrToken(rawData) {
  const prefix = 'photoframe://bind?'
  if (!rawData.startsWith(prefix)) return ''
  const query = rawData.slice(prefix.length)
  for (const pair of query.split('&')) {
    const [k, v] = pair.split('=')
    if (k === 'qr_token') return decodeURIComponent(v || '')
  }
  return ''
}
```

## Acceptance Criteria

- [ ] `bind.js` 不再使用 `new URL()`
- [ ] 扫描 `photoframe://bind?qr_token=abc123` 能正确提取出 `abc123`
- [ ] 不符合格式的二维码（如普通 URL、文本）不发起绑定请求，显示"无效的二维码"
- [ ] 保留对 qrToken 的格式校验（见 todo 021）

## Work Log

- 2026-03-04: 代码审查发现，由 security-sentinel 和 code-simplicity-reviewer 双重确认
