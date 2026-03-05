---
status: pending
priority: p2
issue_id: "023"
tags: [code-review, miniprogram, performance, ux]
---

# [P2] manage 照片列表无分页，全量渲染，积累大量照片后首屏严重卡顿

## Problem Statement

`manage.js` 的 `_loadPhotos` 一次性拉取全部照片，`manage.wxml` 全量渲染所有节点。后端接口也没有分页参数支持。当用户长期使用积累大量照片时，首屏渲染时间和内存占用线性增长：

| 照片数量 | 估算首屏时间 | DOM 节点数 |
|---------|------------|----------|
| 20 张   | < 200ms    | ~60 个   |
| 200 张  | ~1000ms    | ~600 个  |
| 1000 张 | > 5000ms   | ~3000 个（触发微信内存警告）|

## Findings

- `manage.js:41-43` — `request({ data: { device_id: deviceId } })` 无分页参数
- `manage.wxml:33-38` — `wx:for="{{photos}}"` 全量渲染
- `backend/handlers/photo.go` — `ListPhotos` 支持 `since` 增量参数和 `limit`（最大200），前端未使用
- 后端实际已有 `limit` 上限 200，说明超过 200 张时前端只收到前 200 张，无法看到更老的照片

## Proposed Solutions

### Option A: 下拉加载更多（推荐，利用已有后端接口）

后端 `ListPhotos` 已支持 `since=<photo_id>&limit=<n>`，前端实现分页：

```js
// manage.js
data: {
  photos: [],
  lastPhotoId: 0,
  hasMore: true,
  loadingMore: false
},

async _loadPhotos(deviceId, append = false) {
  if (!append) this.setData({ photos: [], lastPhotoId: 0, hasMore: true })
  this.setData({ loadingMore: true })
  const data = await request({
    url: `${app.globalData.baseUrl}/api/photos`,
    data: { device_id: deviceId, since: this.data.lastPhotoId, limit: 30 }
  })
  const newPhotos = data.photos || []
  this.setData({
    photos: append ? [...this.data.photos, ...newPhotos] : newPhotos,
    lastPhotoId: newPhotos.length > 0 ? newPhotos[newPhotos.length - 1].id : this.data.lastPhotoId,
    hasMore: newPhotos.length === 30,
    loadingMore: false
  })
},

onReachBottom() {
  if (this.data.hasMore && !this.data.loadingMore) {
    this._loadPhotos(currentDeviceId, true)
  }
}
```

## Acceptance Criteria

- [ ] 首次加载最多 30 张照片
- [ ] 滚动到底部自动加载更多
- [ ] 全部加载完毕后底部不再触发加载
- [ ] 切换相框时照片列表重置

## Work Log

- 2026-03-04: 由 performance-oracle 发现；后端接口已支持分页，仅需前端改动
