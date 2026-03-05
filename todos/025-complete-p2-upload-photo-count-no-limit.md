---
status: pending
priority: p2
issue_id: "025"
tags: [code-review, miniprogram, ux, input-validation]
---

# [P2] upload 可多次选择累计超过 9 张，剩余数显示负数

## Problem Statement

`onChoosePhoto` 中 `wx.chooseMedia` 的 `count: 9` 只限制单次选择的数量，不限制累计数量。用户多次点击"选择照片"可以无限追加 `items`，导致：
1. 显示"最多可再选 -1 张"（负数）
2. 超出服务端可能的限制或造成内存压力

```xml
<!-- upload.wxml:7 -->
<text class="choose-sub">最多可再选 {{9 - items.length}} 张</text>
```

## Findings

- `upload.js:23` — `count: 9` 仅限单次
- `upload.js:31-35` — `push` 无累计上限
- `upload.wxml:7` — `9 - items.length` 可为负数

## Proposed Solutions

### Option A: 动态 count + 上限防护（推荐）

```js
onChoosePhoto() {
  const remaining = 9 - this.data.items.length
  if (remaining <= 0) {
    wx.showToast({ title: '最多选择 9 张', icon: 'none' })
    return
  }
  wx.chooseMedia({
    count: remaining,  // 动态计算剩余可选数
    mediaType: ['image'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      const newItems = res.tempFiles.map(f => ({
        tempFilePath: f.tempFilePath, status: 'pending', progress: 0, error: ''
      }))
      this.setData({ items: [...this.data.items, ...newItems] })
    }
  })
},
```

## Acceptance Criteria

- [ ] 累计选择超过 9 张时，"选择照片"区域不可点击（或提示并阻止）
- [ ] 显示文字"最多可再选 X 张"中 X 始终 >= 0
- [ ] 已选 9 张后，`count` 传 0 或直接拦截

## Work Log

- 2026-03-04: 由 architecture-strategist 发现
