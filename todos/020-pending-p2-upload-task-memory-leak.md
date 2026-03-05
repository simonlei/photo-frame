---
status: pending
priority: p2
issue_id: "020"
tags: [code-review, miniprogram, performance, memory-leak]
---

# [P2] upload.js 的 UploadTask 未在页面卸载时取消，存在内存泄漏

## Problem Statement

`_uploadOne` 中通过 `wx.uploadFile` 获得的 `task` 对象是局部变量，页面没有维护任何引用。当用户在上传过程中退出页面（按返回键），upload 请求仍在后台继续执行，回调中的 `this._updateItem` 会尝试操作已销毁的页面实例，产生错误日志。`onProgressUpdate` 的回调持有 `this` 引用，阻止 Page 实例被垃圾回收。

## Findings

- `upload.js:107-125` — task 是局部变量，页面无法管理
- `upload.js:122-124` — `onProgressUpdate` 回调持有 `this` 引用
- `upload.js` — 没有 `onUnload` 钩子

## Proposed Solutions

### Option A: 维护 _tasks 数组 + onUnload 清理（推荐）

```js
Page({
  data: { ... },
  _tasks: [],  // 不用 setData，直接挂在实例上

  onUnload() {
    this._tasks.forEach(t => { try { t.abort() } catch(e) {} })
    this._tasks = []
  },

  async _uploadOne(filePath) {
    // ...
    return new Promise((resolve) => {
      const task = wx.uploadFile({ ... })
      this._tasks.push(task)
      task.onProgressUpdate(({ progress }) => {
        if (!this.data) return  // 页面已卸载，跳过
        this._updateItem(filePath, { progress })
      })
      task.on('success/fail', () => {
        this._tasks = this._tasks.filter(t => t !== task)
        // ...
      })
    })
  }
})
```

## Acceptance Criteria

- [ ] 上传过程中返回上一页，不产生"cannot read property of undefined"日志
- [ ] 页面有 `onUnload` 钩子，调用所有活跃 task 的 `abort()`
- [ ] 回调执行前检查 `this.data` 是否存在

## Work Log

- 2026-03-04: 由 performance-oracle 发现
