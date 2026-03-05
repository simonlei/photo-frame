---
title: "refactor: 小程序从 uni-app 迁移到原生微信小程序"
type: refactor
status: active
date: 2026-03-04
---

# refactor: 小程序从 uni-app 迁移到原生微信小程序

## 背景

当前小程序使用 uni-app + Vue3 + TypeScript 实现，构建工具链为 Vite + `@dcloudio/vite-plugin-uni`。实际运行中遭遇严重的依赖兼容问题：

- `@dcloudio/uni-app` 只发布了 `alpha` 版本，无稳定正式版
- alpha 版本内部 `import { isInSSRComponentSetup } from 'vue'` 引用了 Vue 的非公开内部 API，导致构建失败
- Vue 版本与 `@vue/*` 子包版本冲突，无法通过 `overrides` 完全解决

本系统功能极其简单，引入 uni-app 完全是过度设计。直接使用**原生微信小程序**（JS + WXML + WXSS）开发，零依赖、零构建步骤、开箱即调试。

## 功能范围

保留现有全部功能，不新增任何能力：

1. **自动静默登录**：启动时用 `wx.login()` 换 token，存 storage
2. **相框列表**：展示已绑定相框，空状态引导绑定
3. **扫码绑定相框**：`wx.scanCode` 解析 `photoframe://bind?qr_token=xxx`
4. **上传照片**：选图→压缩（>2MB）→并发3张上传→进度展示→失败重试
5. **管理照片**：3列网格浏览，长按/点击删除，多相框切换

服务器地址通过 `app.js` 全局常量配置，不再有 `initBaseUrl()` 这种运行时初始化的问题。

## 目录结构

原 `miniprogram/` 目录整体替换为：

```
miniprogram/
├── app.js            # App() 入口，onLaunch 静默登录，全局 BASE_URL
├── app.json          # 页面注册、tabBar、全局窗口配置
├── app.wxss          # 全局样式（极少量，主要用于字体/颜色变量）
├── project.config.json   # 微信开发者工具项目配置（appid 等）
├── sitemap.json      # 微信索引配置（默认允许）
└── pages/
    ├── index/
    │   ├── index.js      # 相框列表逻辑
    │   ├── index.wxml    # 相框列表模板
    │   └── index.wxss    # 页面样式
    ├── bind/
    │   ├── bind.js
    │   ├── bind.wxml
    │   └── bind.wxss
    ├── upload/
    │   ├── upload.js
    │   ├── upload.wxml
    │   └── upload.wxss
    └── manage/
        ├── manage.js
        ├── manage.wxml
        └── manage.wxss
```

删除：`package.json`、`vite.config.ts`、`index.html`、`src/` 目录、`node_modules/`

## 关键实现说明

### app.js — 全局配置 + 登录

```javascript
// app.js
const BASE_URL = 'https://your-server.com'  // 填入真实地址

App({
  globalData: {
    baseUrl: BASE_URL,
    token: ''
  },
  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
    } else {
      this._login()
    }
  },
  _login() {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${BASE_URL}/api/wx-login`,
          method: 'POST',
          data: { code },
          success: (res) => {
            if (res.statusCode === 200 && res.data.token) {
              this.globalData.token = res.data.token
              wx.setStorageSync('token', res.data.token)
            }
          }
        })
      }
    })
  }
})
```

### app.json — 页面和 TabBar

```json
{
  "pages": [
    "pages/index/index",
    "pages/bind/bind",
    "pages/upload/upload",
    "pages/manage/manage"
  ],
  "tabBar": {
    "color": "#666666",
    "selectedColor": "#1976D2",
    "list": [
      { "pagePath": "pages/index/index", "text": "相框" },
      { "pagePath": "pages/manage/manage", "text": "管理" }
    ]
  },
  "window": {
    "backgroundTextStyle": "light",
    "navigationBarBackgroundColor": "#fff",
    "navigationBarTitleText": "家庭相框",
    "navigationBarTextStyle": "black"
  }
}
```

### utils/api.js — 统一请求封装

```javascript
// utils/api.js
const app = getApp()

function authHeader() {
  return { Authorization: `Bearer ${app.globalData.token}` }
}

function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      ...options,
      header: { ...authHeader(), ...(options.header || {}) },
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          reject(new Error('未登录'))
        } else {
          reject(new Error(res.data?.error || `请求失败 (${res.statusCode})`))
        }
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络请求失败'))
      }
    })
  })
}

module.exports = { request, authHeader }
```

### upload/upload.js — 上传核心逻辑

并发3张、>2MB 先压缩，与现有后端 API 完全兼容：

```javascript
const CONCURRENCY = 3
const COMPRESS_THRESHOLD = 2 * 1024 * 1024  // 2MB

async function uploadItem(item) {
  // 1. 检查文件大小，超过阈值压缩
  let filePath = item.tempFilePath
  const { size } = await getFileSize(filePath)
  if (size > COMPRESS_THRESHOLD) {
    const res = await new Promise((resolve, reject) =>
      wx.compressImage({ src: filePath, quality: 80, success: resolve, fail: reject })
    )
    filePath = res.tempFilePath
  }
  // 2. 上传
  return new Promise((resolve, reject) => {
    const task = wx.uploadFile({
      url: `${app.globalData.baseUrl}/api/upload`,
      filePath,
      name: 'file',
      formData: { device_id: this.data.deviceId },
      header: { Authorization: `Bearer ${app.globalData.token}` },
      success(res) {
        res.statusCode === 200 ? resolve(JSON.parse(res.data)) : reject(new Error('上传失败'))
      },
      fail: reject
    })
    task.onProgressUpdate(({ progress }) => {
      // 更新该条目进度
    })
  })
}
```

## 验收标准

- [ ] `npm install` 命令不再需要（无依赖）
- [ ] 用微信开发者工具直接导入 `miniprogram/` 目录可正常预览
- [ ] 微信登录：启动后自动完成，首页能正确展示相框列表或空状态
- [ ] 绑定：扫描 `photoframe://bind?qr_token=xxx` 格式二维码成功绑定
- [ ] 上传：选 9 张图片，>2MB 的自动压缩，3 并发上传，进度条正常显示
- [ ] 管理：照片 3 列网格显示，可删除，删除后实时更新
- [ ] 401 响应自动清 token 并提示重新登录
- [ ] `BASE_URL` 改为真实域名后全功能可用

## 迁移步骤

1. **删除旧文件**：清空 `miniprogram/` 目录（保留 `miniprogram/` 目录本身）
2. **创建 app.js / app.json / app.wxss**
3. **创建 utils/api.js**
4. **实现 4 个页面**（按优先级：index → upload → bind → manage）
5. **创建 project.config.json**（填入 appid）
6. **验证**：微信开发者工具导入并真机调试

## 相关文件

- 当前小程序代码：`miniprogram/src/`（删除）
- 后端接口不变：`backend/handlers/` 所有接口保持兼容
- 参考实现：`miniprogram/src/api/index.ts`（迁移时参考 API 封装逻辑）
- 代码审查方案文档：`docs/solutions/multi-category/photo-frame-comprehensive-code-review.md`
