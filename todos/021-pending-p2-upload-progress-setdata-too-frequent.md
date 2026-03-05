---
status: pending
priority: p2
issue_id: "021"
tags: [code-review, miniprogram, performance, setdata]
---

# [P2] onProgressUpdate 高频触发 setData，3张并发上传时 UI 每秒 90次更新

## Problem Statement

`_updateItem` 每次进度事件都克隆整个 `items` 数组并通过 `setData` 传递给渲染层。3 张并发上传时每秒最多触发 90 次 setData（30次/张），远超微信建议的每秒 20 次上限，在中低端设备上会造成明显卡顿。

```js
// 当前：每次进度事件全量替换 items 数组
_updateItem(idx, patch) {
  const items = [...this.data.items]  // 克隆整个数组
  items[idx] = Object.assign({}, items[idx], patch)
  this.setData({ items })             // 全量传递
}
```

## Findings

- `upload.js:122-124` — `onProgressUpdate` 无节流
- `upload.js:128-134` — `_updateItem` 全量替换 items 数组
- 微信官方文档：`setData` 建议每秒不超过 20 次，数据量不超过 1024KB

## Proposed Solutions

### Option A: 路径更新 + 时间节流（推荐）

```js
// 使用 setData 路径语法，只更新单个字段
_updateItemProgress(idx, progress) {
  this.setData({ [`items[${idx}].progress`]: progress })
}

// 节流：同一张图 300ms 内最多更新一次
_throttledProgressUpdate: {},
onProgressUpdate(idx, progress) {
  const key = `prog_${idx}`
  if (this._throttledProgressUpdate[key]) return
  this._throttledProgressUpdate[key] = setTimeout(() => {
    delete this._throttledProgressUpdate[key]
    this._updateItemProgress(idx, progress)
  }, 300)
}
```

**优点**: 传输量从完整数组降为单个数字，频率从 90次/秒降为 10次/秒
**缺点**: 进度条更新有 300ms 延迟，视觉上稍显不流畅（可接受）

## Acceptance Criteria

- [ ] 3 张并发上传时，setData 调用频率不超过 15 次/秒
- [ ] 进度条仍有可见更新，不是一直为 0 然后突然完成
- [ ] 低端设备上传过程中页面保持流畅滚动

## Work Log

- 2026-03-04: 由 performance-oracle 发现
