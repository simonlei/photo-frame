---
status: pending
priority: p3
issue_id: "031"
tags: [code-review, miniprogram, performance, code-quality]
---

# [P3] upload.js 重试时每次重新压缩，压缩结果未缓存

## Problem Statement

`_uploadOne` 中压缩结果是局部变量 `filePath`，不写回 `items`。用户点击"重试"时重新进入 `_uploadOne`，会再次执行 `wx.compressImage`，对中低端设备这会浪费 800-2000ms 并加重 CPU 压力。

## Proposed Solutions

在 `items` 元素上增加 `compressedPath` 字段，首次压缩成功后写入，重试时复用：

```js
// _uploadOne
const item = this.data.items.find(it => it.tempFilePath === filePath)
let uploadPath = item.compressedPath || filePath

if (!item.compressedPath) {
  const size = await getFileSize(filePath)
  if (size > COMPRESS_THRESHOLD) {
    const res = await compressImage(filePath)
    uploadPath = res.tempFilePath
    this._updateItem(filePath, { compressedPath: uploadPath })  // 缓存
  }
}
// 使用 uploadPath 上传
```

## Acceptance Criteria

- [ ] 同一张图片重试时不再调用 `wx.compressImage`
- [ ] 首次压缩结果存入 `items[n].compressedPath`

## Work Log

- 2026-03-04: 由 performance-oracle 发现；依赖 todo 019（items 改用 filePath 为 key）
