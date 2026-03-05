---
status: pending
priority: p1
issue_id: "019"
tags: [code-review, miniprogram, concurrency, data-integrity]
---

# [P1] upload.js 并发重试无保护，下标偏移导致状态更新错位

## Problem Statement

`upload.js` 存在两个相关的并发安全问题：

**问题 1：onRetryItem 与批量上传无并发互锁**

`onRetryItem` 直接调用 `_uploadOne(idx)`，没有检查 `uploading` 标志。用户在批量上传进行中点击某张失败图片的"重试"，会对同一张图片同时发起两个上传请求，服务器可能产生重复记录。

**问题 2：上传中删除条目导致下标偏移**

`pendingIndexes` 在 `onStartUpload` 开始时快照（第 56-59 行），保存的是数组下标。若用户在上传中通过 `onRemoveItem` 删除了某个条目，`items` 数组 splice 后下标发生偏移，`_updateItem(idx, ...)` 会更新到错误的条目，导致进度/状态错位。

## Findings

- `upload.js:46-49` — `onRetryItem` 无 `uploading` 检查
- `upload.js:56-59` — `pendingIndexes` 是下标快照，删除后失效
- `upload.js:80-81` — `if (!item) return` 检查了空，但下标偏移后 item 不为空只是指向错误条目
- `upload.js:132-134` — `_updateItem` 按数组下标操作

## Proposed Solutions

### Option A: 用 tempFilePath 作为稳定 ID（推荐）

将 `items` 的状态追踪从下标改为以 `tempFilePath` 为 key：

```js
// items 结构不变，但 _updateItem 改用 path 查找
_updateItem(filePath, patch) {
  const items = this.data.items.map(it =>
    it.tempFilePath === filePath ? Object.assign({}, it, patch) : it
  )
  this.setData({ items })
}

// _uploadOne 签名改为接受 filePath
async _uploadOne(filePath) { ... }
```

同时在 `onRetryItem` 中：
```js
onRetryItem(e) {
  if (this.data.uploading) return  // 批量上传中禁止单独重试
  const filePath = e.currentTarget.dataset.filepath
  this._uploadOne(filePath)
}
```

**优点**: 根本解决下标偏移问题；逻辑更清晰
**缺点**: 需要改动 wxml 的 `data-idx` 为 `data-filepath`，改动范围稍大

### Option B: 上传期间禁用删除（快速修复）

在 `onRemoveItem` 中增加 `uploading` 检查，上传期间禁止删除：
```js
onRemoveItem(e) {
  if (this.data.uploading) return
  // ...
}
```

同时在 `onRetryItem` 中增加 `uploading` 检查。不解决根本问题，但防止触发。

## Acceptance Criteria

- [ ] 批量上传进行中，点击单张"重试"无效果（或有提示）
- [ ] 批量上传进行中，删除条目不影响其他条目的进度更新
- [ ] 单张上传失败不触发对其他条目的状态污染

## Work Log

- 2026-03-04: 由 architecture-strategist 和 performance-oracle 发现
