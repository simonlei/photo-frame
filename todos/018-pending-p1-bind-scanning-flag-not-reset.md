---
status: pending
priority: p1
issue_id: "018"
tags: [code-review, miniprogram, state-bug]
---

# [P1] bind.js 绑定成功后 scanning 标志未重置，按钮可能永久禁用

## Problem Statement

`bind.js` 的 `onTapScan` 在开始时将 `scanning` 设为 `true` 防止重入，在失败路径中重置为 `false`，但**绑定成功路径**（第 47-48 行）没有重置：

```js
// bind.js:47-48（当前代码）
wx.showToast({ title: '绑定成功', icon: 'success' })
setTimeout(() => wx.navigateBack(), 1200)
// scanning 仍为 true，直到页面销毁
```

若 `navigateBack()` 未执行（导航栈为空）或用户在 1200ms 内快速返回再进入该页面（页面被 hide 而非 destroy），`scanning: true` 会让扫码按钮被 `disabled` 永久禁用，用户无法再次扫码绑定。

## Findings

- `bind.js:9` — `if (this.data.scanning) return` 是防重入保护
- `bind.js:10` — `setData({ scanning: true })`
- `bind.js:47-48` — 成功路径缺少 `setData({ scanning: false })`
- `bind.js:52,56` — 失败路径均有重置，唯独成功路径遗漏

## Proposed Solutions

### Option A: 补充成功路径的重置（推荐，一行代码）

```js
wx.showToast({ title: '绑定成功', icon: 'success' })
this.setData({ scanning: false })  // 补充此行
setTimeout(() => wx.navigateBack(), 1200)
```

### Option B: 在 onShow 中统一重置

在 `onShow` 钩子中重置 `scanning: false`，保证每次页面可见时状态干净：

```js
onShow() {
  this.setData({ scanning: false })
}
```

**推荐 Option A**：直接、明确，不依赖生命周期副作用。

## Acceptance Criteria

- [ ] 绑定成功后 `scanning` 被重置为 `false`
- [ ] 若绑定成功后未跳转（测试用），扫码按钮仍可点击
- [ ] 失败路径的 `scanning` 重置逻辑保持不变

## Work Log

- 2026-03-04: 由 architecture-strategist 和 code-simplicity-reviewer 双重发现
