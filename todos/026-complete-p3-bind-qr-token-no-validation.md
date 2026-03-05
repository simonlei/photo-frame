---
status: pending
priority: p3
issue_id: "026"
tags: [code-review, miniprogram, security, input-validation]
---

# [P3] bind.js 扫码内容无格式校验，任意二维码内容可作为 qr_token 发送

## Problem Statement

修复 todo 016（`new URL()` 替换）后，仍存在一个安全问题：代码没有对提取出的 `qrToken` 做格式校验，也没有收紧降级路径。若用户扫描了非 `photoframe://` 协议的二维码，应明确拒绝而不是将扫码内容当 token 发送。

## Findings

- `bind.js:18-29` — 降级逻辑将任意扫码内容作为 qrToken

## Proposed Solutions

```js
// 修复 016 后，在提取 token 后增加校验
const qrToken = parseQrToken(rawData)
if (!qrToken || !/^[A-Za-z0-9_-]{8,64}$/.test(qrToken)) {
  wx.showToast({ title: '无效的相框二维码', icon: 'none' })
  this.setData({ scanning: false })
  return
}
// 移除 fallback 降级逻辑（不符合格式直接拒绝）
```

## Acceptance Criteria

- [ ] 扫描普通 URL 二维码显示"无效的相框二维码"，不发请求
- [ ] `qrToken` 长度超过 64 字符时拒绝
- [ ] 符合格式的 token 正常绑定

## Work Log

- 2026-03-04: 由 security-sentinel 发现；依赖 todo 016 先完成
