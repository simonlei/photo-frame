---
status: pending
priority: p3
issue_id: "029"
tags: [code-review, miniprogram, ux]
---

# [P3] chooseMedia 缺少 fail 回调，用户拒绝相册权限时静默失败

## Problem Statement

`upload.js` 的 `onChoosePhoto` 只写了 `success` 回调，没有 `fail` 回调。当用户拒绝相册/相机权限时，点击"选择照片"无任何反应，用户不知道原因。

## Proposed Solutions

```js
wx.chooseMedia({
  // ...
  success: (res) => { /* ... */ },
  fail: (err) => {
    if (err.errMsg && err.errMsg.includes('auth deny')) {
      wx.showModal({
        title: '需要相册权限',
        content: '请在设置中允许访问相册',
        confirmText: '去设置',
        success: (r) => {
          if (r.confirm) wx.openSetting()
        }
      })
    }
  }
})
```

## Acceptance Criteria

- [ ] 拒绝权限时显示提示并引导到设置页
- [ ] 用户取消选择（非拒绝权限）不显示错误

## Work Log

- 2026-03-04: 由 architecture-strategist 发现
