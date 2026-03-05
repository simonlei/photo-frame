---
title: 从 uni-app 迁移到原生微信小程序及16个质量问题修复
problem_type: integration-issues
component: miniprogram
symptoms:
  - npm install 报错：No matching version found for @dcloudio/uni-app@^3.0.0
  - uni-app alpha 版构建报错：isInSSRComponentSetup is not exported from vue
  - 重写后代码审查发现 4 个 P1 / 6 个 P2 / 6 个 P3 问题
tags: [uni-app, wechat-miniprogram, migration, build-error, race-condition, memory-leak, performance, state-management]
date: 2026-03-04
severity: high
---

# 从 uni-app 迁移到原生微信小程序及16个质量问题修复

## 问题描述

### 第一阶段：uni-app 构建失败（触发因素）

在使用 uni-app 框架开发微信小程序时，遇到两个阻塞性错误：

**错误 1 — npm 版本解析失败：**
```
npm error notarget No matching version found for @dcloudio/uni-app@^3.0.0
```
uni-app 从未发布过稳定版本，所有版本均为 alpha，无法满足 `^3.0.0` semver 要求。

**错误 2 — Vue 内部 API 缺失：**
```
isInSSRComponentSetup is not exported from vue
```
uni-app 的 alpha 版在构建时依赖 Vue 的私有内部 API，这些 API 从未对外导出，导致所有 alpha 版本构建均失败。尝试通过 `overrides` 锁定 Vue 子包版本也无法解决，问题属于上游 bug。

### 第二阶段：原生重写后的代码审查问题

放弃 uni-app、重写为原生微信小程序后，代码审查发现 16 个问题（提交 `437dd16`）：

**P1 关键（4 个）：**
- `new URL()` 在小程序 JS 运行时不可用（bind.js 二维码解析永远失效）
- 冷启动登录竞态：`onShow` 在 `_login()` 完成前执行，导致空 token 请求
- `scanning` 标志绑定成功后未重置，扫码按钮可能永久卡死
- 并发上传使用数组下标追踪状态，删除元素时下标偏移

**P2 重要（6 个）：**
- `UploadTask` 引用未清理，页面卸载时内存泄漏
- `onProgressUpdate` 无节流，3 个并发最高 90 次 setData/秒
- Tab 切换每次触发网络请求，无缓存策略
- 照片列表无分页，照片多时加载慢
- `_loadFrames` 缺少 loading 状态管理
- 多次调用 `chooseMedia` 可累计超过 9 张上限

**P3 优化（6 个）：**
- qrToken 无格式校验
- `'未登录'` 魔法字符串分散在多文件
- `_loadFrames` 逻辑在 index.js / manage.js 重复
- `chooseMedia` 缺少 fail 回调，权限拒绝时静默失败
- `sitemap.json` 所有页面均可被索引
- 重试时重复压缩图片，未缓存压缩结果

---

## 根本原因

| 问题 | 根本原因 |
|------|---------|
| uni-app 构建失败 | 框架从未发布稳定版；alpha 版依赖 Vue 私有 API |
| `new URL()` 失效 | 微信小程序 V8 沙箱不提供浏览器 Web API |
| 登录竞态 | `App.onLaunch` 中登录是异步的，页面 `onShow` 不等待 |
| 并发上传下标错乱 | 以数组下标作为状态 key，并发操作会使下标失效 |
| 内存泄漏 | `UploadTask` 持有 Page 实例引用，未在 `onUnload` 清理 |
| setData 频率过高 | 进度事件按原始频率触发，未节流 |

---

## 解决方案

### 方案 A：放弃 uni-app，改用原生微信小程序

**决策理由：** 项目功能简单（上传照片 + 查看历史），不需要跨平台能力。原生方案零依赖、零构建步骤，彻底消除框架层风险。

**目录结构：**
```
miniprogram/
├── app.js              # 全局登录逻辑 + readyPromise
├── app.json            # 页面路由配置
├── app.wxss            # 全局样式
├── project.config.json
├── sitemap.json        # 只允许首页被索引
├── utils/
│   └── api.js          # HTTP 封装 + 认证 + 缓存
└── pages/
    ├── index/          # 相框列表首页
    ├── bind/           # 扫码绑定相框
    ├── upload/         # 多图并发上传
    └── manage/         # 照片管理（分页 + 删除）
```

无 `package.json`，无 `node_modules`，无构建配置，直接部署。

---

### 方案 B：16 个具体修复

#### 1. readyPromise 模式（修复登录竞态）

**miniprogram/app.js：**
```javascript
App({
  globalData: {
    baseUrl: 'https://your-server.com',
    token: '',
    framesCache: null,
    framesCacheTime: 0
  },

  onLaunch() {
    // ready 是一个 Promise，登录完成（成功或失败）后 resolve
    // 页面 onShow 应 await getApp().ready，避免竞态
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
          url: `${this.globalData.baseUrl}/api/wx-login`,
          method: 'POST',
          data: { code },
          success: (res) => {
            if (res.statusCode === 200 && res.data && res.data.token) {
              this.globalData.token = res.data.token
              try { wx.setStorageSync('token', res.data.token) } catch (e) {}
            }
            resolve()  // 无论成败都 resolve，不阻塞页面
          },
          fail() { resolve() }
        })
      },
      fail() { resolve() }
    })
  }
})
```

**页面中使用：**
```javascript
async _loadFrames() {
  await getApp().ready  // 等待登录完成，避免首次冷启动竞态
  this.setData({ loading: true })
  // ... 继续网络请求
}
```

#### 2. 正则替换 new URL()（修复二维码解析）

微信小程序运行时不提供 `URL` 类（浏览器 Web API）：

```javascript
// ❌ 错误：小程序不支持
const url = new URL(rawData)
const qrToken = url.searchParams.get('qr_token')

// ✅ 正确：使用正则解析
let qrToken = ''
if (rawData.startsWith('photoframe://bind')) {
  const match = rawData.match(/[?&]qr_token=([^&]+)/)
  qrToken = match ? decodeURIComponent(match[1]) : ''
}

// 附加格式校验，防止空字符串或恶意 token
if (!qrToken || !/^[A-Za-z0-9_-]{8,64}$/.test(qrToken)) {
  wx.showToast({ title: '无效的相框二维码', icon: 'none' })
  this.setData({ scanning: false })
  return
}
```

#### 3. 稳定 ID 模式（修复并发上传状态错乱）

```javascript
// ✅ 以 tempFilePath 作为稳定 ID
const newItems = res.tempFiles.map(f => ({
  id: f.tempFilePath,   // 稳定 key，永不变化
  tempFilePath: f.tempFilePath,
  compressedPath: '',
  status: 'pending',
  progress: 0,
  error: ''
}))

// 所有状态更新通过 id 查找，而非数组下标
_updateItem(itemId, patch) {
  const items = this.data.items.map(it =>
    it.id === itemId ? Object.assign({}, it, patch) : it
  )
  this.setData({ items })
}

// 删除也用 id
onRemoveItem(e) {
  if (this.data.uploading) return  // 上传中禁止删除
  const itemId = e.currentTarget.dataset.id
  const items = this.data.items.filter(it => it.id !== itemId)
  this.setData({ items })
}
```

#### 4. UploadTask 清理 + 进度节流（修复内存泄漏和 setData 过频）

```javascript
Page({
  _tasks: [],          // UploadTask 引用列表
  _progressTimers: {}, // 节流计时器 Map，key 为 item.id

  onUnload() {
    // 取消所有上传，防止内存泄漏和回调悬挂
    this._tasks.forEach(t => { try { t.abort() } catch (e) {} })
    this._tasks = []
    Object.values(this._progressTimers).forEach(timer => clearTimeout(timer))
    this._progressTimers = {}
  },

  _uploadOne(item) {
    return new Promise((resolve) => {
      const task = wx.uploadFile({ /* ... */ })
      this._tasks.push(task)

      // 300ms 节流 + setData 路径语法（只更新单个字段）
      task.onProgressUpdate(({ progress }) => {
        if (!this.data) return
        const key = item.id
        if (this._progressTimers[key]) return
        this._progressTimers[key] = setTimeout(() => {
          delete this._progressTimers[key]
          if (!this.data) return
          const idx = this.data.items.findIndex(it => it.id === key)
          if (idx >= 0) {
            this.setData({ [`items[${idx}].progress`]: progress })
          }
        }, 300)
      })
    })
  }
})
```

#### 5. fetchMyFrames 30 秒缓存（消除重复网络请求）

```javascript
// utils/api.js
const ERR_UNAUTHORIZED = 'ERR_UNAUTHORIZED'

// 401 时设置 err.code，方便页面区分认证错误
// 在 request() 的 success 回调中：
if (res.statusCode === 401) {
  const err = new Error('未登录')
  err.code = ERR_UNAUTHORIZED
  reject(err)
}

// 带 30s TTL 的缓存封装
async function fetchMyFrames(force) {
  const app = getApp()
  const ttl = 30 * 1000
  if (!force && app.globalData.framesCache &&
      Date.now() - app.globalData.framesCacheTime < ttl) {
    return app.globalData.framesCache
  }
  const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
  app.globalData.framesCache = data.frames || []
  app.globalData.framesCacheTime = Date.now()
  return app.globalData.framesCache
}

module.exports = { request, authHeader, getFileSize, ERR_UNAUTHORIZED, fetchMyFrames }
```

绑定成功时使缓存失效：
```javascript
// pages/bind/bind.js
await request({ url: `${app.globalData.baseUrl}/api/bind`, method: 'POST', ... })
getApp().globalData.framesCache = null  // 清除缓存，让首页显示新绑定的相框
this.setData({ scanning: false })       // 重置扫码标志
```

页面中使用：
```javascript
// pages/manage/manage.js
} catch (e) {
  if (e.code !== ERR_UNAUTHORIZED) {  // 认证错误静默处理
    wx.showToast({ title: e.message || '加载失败', icon: 'none' })
  }
}
```

#### 6. 照片列表分页（修复大量数据时加载慢）

```javascript
// pages/manage/manage.js
async _loadPhotos(deviceId, append) {
  if (!append) {
    this.setData({ photos: [], hasMore: true, _currentDeviceId: deviceId })
  }
  this.setData({ loadingMore: true })
  const lastId = append && this.data.photos.length > 0
    ? this.data.photos[this.data.photos.length - 1].id
    : 0
  const data = await request({
    url: `${app.globalData.baseUrl}/api/photos`,
    data: { device_id: deviceId, since: lastId, limit: 30 }
  })
  const newPhotos = data.photos || []
  this.setData({
    photos: append ? [...this.data.photos, ...newPhotos] : newPhotos,
    hasMore: newPhotos.length === 30
  })
  this.setData({ loadingMore: false })
},

onReachBottom() {
  if (this.data.hasMore && !this.data.loadingMore && this.data._currentDeviceId) {
    this._loadPhotos(this.data._currentDeviceId, true)
  }
}
```

#### 7. 压缩结果缓存（修复重试时重复压缩）

```javascript
async _uploadOne(item) {
  // 使用缓存的压缩路径，避免重试时重复压缩（每次 800-2000ms）
  let uploadPath = item.compressedPath || item.tempFilePath
  if (!item.compressedPath) {
    try {
      const size = await getFileSize(item.tempFilePath)
      if (size > COMPRESS_THRESHOLD) {  // 2MB
        const res = await new Promise((resolve, reject) =>
          wx.compressImage({ src: item.tempFilePath, quality: 80,
                             success: resolve, fail: reject })
        )
        uploadPath = res.tempFilePath
        this._updateItem(item.id, { compressedPath: uploadPath })  // 缓存
      }
    } catch (e) {
      // 压缩失败时继续用原图
    }
  }
  // ... 继续上传
}
```

---

## 预防策略

### 框架选型检查清单

选用第三方小程序框架前，需验证以下条件：

- [ ] **稳定版存在**：npm registry 中有非 alpha/beta 的稳定版本
- [ ] **不依赖宿主框架私有 API**：检查 `node_modules` 中是否引用 `vue` 内部模块（如 `@vue/shared` 私有导出）
- [ ] **构建可复现**：在 CI 环境从零安装后能成功构建
- [ ] **功能匹配**：如果只需要单平台，原生方案往往更简单可靠
- [ ] **框架活跃度**：最近 6 个月内有稳定版发布

### 微信小程序运行时 Gotchas

小程序使用 V8/JavaScriptCore 沙箱，**以下浏览器 API 不可用：**

| 浏览器 API | 小程序替代方案 |
|-----------|--------------|
| `new URL()` | 正则表达式手动解析 |
| `fetch()` | `wx.request()` |
| `WebSocket` | `wx.connectSocket()` |
| `localStorage` | `wx.getStorageSync()` |
| `FormData` | `wx.uploadFile()` multipart |
| `Blob / File` | 无直接替代，使用文件路径 |

### 并发操作安全规则

```
✅ 用稳定 ID（如 tempFilePath）作为 item.key，绝不用数组下标
✅ 在 onUnload 中取消所有 UploadTask 并清除计时器
✅ 进度更新节流：每个 item 300ms 内最多一次 setData
✅ 使用 setData 路径语法（items[n].progress）替代全量替换
✅ 批量上传中禁止删除/单独重试（防下标偏移）
```

### setData 性能准则

- WeChat 建议 setData 频率不超过 **20 次/秒**
- 3 个并发上传不节流时最高 **90 次/秒**，造成 UI 卡顿
- 路径语法 `items[n].field` 比替换整个数组开销低 5-10 倍
- 批量相关更新合并为一次 setData 调用

---

## 受影响的文件

| 文件 | 修改内容 |
|------|---------|
| `miniprogram/app.js` | 新增 readyPromise；framesCache 全局存储 |
| `miniprogram/utils/api.js` | ERR_UNAUTHORIZED 常量；fetchMyFrames 缓存；authHeader fallback |
| `miniprogram/pages/bind/bind.js` | 正则替换 new URL()；scanning 标志重置；缓存失效；token 格式校验 |
| `miniprogram/pages/index/index.js` | await getApp().ready；使用 fetchMyFrames |
| `miniprogram/pages/manage/manage.js` | await getApp().ready；分页加载；保持 currentFrameIdx；loading 状态 |
| `miniprogram/pages/manage/manage.wxml` | 加载更多 + 已显示全部照片提示 |
| `miniprogram/pages/manage/manage.wxss` | .load-more 样式 |
| `miniprogram/pages/upload/upload.js` | 完整重构：稳定 ID、onUnload 清理、进度节流、照片上限、权限引导、压缩缓存 |
| `miniprogram/pages/upload/upload.wxml` | data-idx → data-id；剩余张数显示 |
| `miniprogram/sitemap.json` | 只允许首页被索引 |

---

## 相关文档

- **迁移计划**：`docs/plans/2026-03-04-refactor-miniprogram-native-wechat-plan.md`
- **原始系统计划**：`docs/plans/2026-03-04-feat-photo-frame-system-plan.md`
- **全组件代码审查**（后端/Android/小程序安全问题）：`docs/solutions/multi-category/photo-frame-comprehensive-code-review.md`
- **相关 todos**：`todos/016-031-complete-*.md`（16 个已完成的小程序问题）
- **提交记录**：
  - `437dd16` — 原生小程序初始实现（32 文件）
  - `6209e82` — 16 个 todo 文件创建
  - `b5513c2` — 所有 16 个问题修复完成
