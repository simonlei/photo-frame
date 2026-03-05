---
status: pending
priority: p1
issue_id: "017"
tags: [code-review, miniprogram, auth, race-condition]
---

# [P1] 登录竞态：首次冷启动时 onShow 早于 _login() 完成，必现空 token 请求

## Problem Statement

`app.js` 的 `onLaunch` 中，`_login()` 是异步的（内部有 `wx.login` + `wx.request` 两次网络 IO），但 `onLaunch` 不等待其结果。小程序启动后 `index` 和 `manage` 两个 TabBar 页面几乎同时触发 `onShow`，此时 token 尚未写入 `globalData`，导致：

1. 请求携带空 `Authorization: Bearer ` 头
2. 服务器返回 401
3. `api.js` 触发"登录已过期"提示（而实际是从未登录过）
4. 两个 TabBar 页面同时看到错误，但登录成功后不会自动重试

**首次安装用户 100% 必现此问题。**

## Findings

- `app.js:14` — `this._login()` 无等待机制
- `app.js:18-42` — `_login()` 无 resolve 通知
- `index.js:9-11` — `onShow` 直接请求，无 token 就绪检查
- `manage.js:11-13` — 同上

## Proposed Solutions

### Option A: readyPromise 模式（推荐）

在 `app.js` 中暴露一个 `ready` Promise，页面 `onShow` await 后再发请求：

```js
// app.js
App({
  globalData: { baseUrl: BASE_URL, token: '' },
  onLaunch() {
    let resolve
    this.ready = new Promise(r => { resolve = r })
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
      resolve()
    } else {
      this._login(resolve)
    }
  },
  _login(resolve) {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${BASE_URL}/api/wx-login`, method: 'POST', data: { code },
          success: (res) => {
            if (res.statusCode === 200 && res.data.token) {
              this.globalData.token = res.data.token
              try { wx.setStorageSync('token', res.data.token) } catch(e) {}
            }
            resolve() // 无论成功失败都 resolve，让页面继续
          },
          fail() { resolve() }
        })
      },
      fail() { resolve() }
    })
  }
})

// index.js / manage.js
async _loadFrames() {
  await getApp().ready  // 等待登录完成
  // ...
}
```

**优点**: 页面完全等待登录，无竞态；失败时 resolve 而非 reject 确保页面始终能渲染
**缺点**: 需要修改 app.js 和两个页面

### Option B: 页面侧轮询检查

`onShow` 中检查 token，若为空则延迟 500ms 重试（最多 5 次）。

**缺点**: 实现复杂，体验差（有可见延迟），不推荐。

## Acceptance Criteria

- [ ] 首次安装冷启动，不出现"登录已过期"提示
- [ ] 首次安装后，首页相框列表（或空状态）正常展示
- [ ] token 就绪后两个 TabBar 页面均能加载数据
- [ ] 登录失败时页面展示对应的错误或空状态，不卡死

## Work Log

- 2026-03-04: 由 architecture-strategist 和 security-sentinel 双重发现
