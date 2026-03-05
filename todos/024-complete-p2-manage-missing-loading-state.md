---
status: pending
priority: p2
issue_id: "024"
tags: [code-review, miniprogram, ux, state-management]
---

# [P2] manage.js _loadFrames 缺少 loading 状态，加载期间显示错误的空状态

## Problem Statement

`manage.js` 的 `_loadFrames` 不更新 `loading` 状态，而 `manage.wxml` 的条件渲染依赖 `loading` 来决定是否显示空状态。当 `_loadFrames` 正在执行时（`loading: false`），若 `frames` 为旧数据或空，模板会短暂显示"还没有绑定相框"的误导性空状态，等数据返回后才突然切换。

## Findings

- `manage.js:15-29` — `_loadFrames` 无 loading 管理
- `manage.wxml:19-28` — `wx:elif="{{frames.length === 0}}"` 在 loading 期间也可能触发
- `index.js:14,24` — 对比：index 页正确在 `_loadFrames` 前后管理 loading
- `manage.js:19-20` — 成功后 `setData({ frames })` 没有清空旧 `photos`，导致短暂显示旧相框的照片

## Proposed Solutions

### Option A: 对齐 index.js 的 loading 管理模式

```js
// manage.js
async _loadFrames() {
  this.setData({ loading: true, frames: [], photos: [] })  // 清空旧数据
  try {
    const app = getApp()
    const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
    const frames = data.frames || []
    this.setData({ frames, currentFrameIdx: 0 })
    if (frames.length > 0) {
      this._loadPhotos(frames[0].id)
    }
  } catch (e) {
    if (e.message !== '未登录') {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  } finally {
    this.setData({ loading: false })
  }
},
```

**4 行改动，消除两个 bug（loading 状态 + 旧数据残留）。**

## Acceptance Criteria

- [ ] `manage` 页首次进入显示加载指示，不显示误导性空状态
- [ ] 切换 Tab 回到 manage 页时，旧照片不闪现
- [ ] 成功加载后，`loading` 正确重置为 `false`

## Work Log

- 2026-03-04: 由 architecture-strategist 发现
