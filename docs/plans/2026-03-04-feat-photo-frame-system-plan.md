---
title: "电子相框系统 - MVP 完整实现"
type: feat
status: active
date: 2026-03-04
origin: docs/brainstorms/2026-03-04-photo-frame-brainstorm.md
---

# 电子相框系统 - MVP 完整实现

## 概述

将老 Android 平板变成智能家庭电子相框，家庭成员通过微信小程序上传照片，相框自动展示。系统由三部分组成：Android 相框 App、Go 后端 API、Vue3（uni-app）微信小程序。

**起源：** 见 [头脑风暴文档](../brainstorms/2026-03-04-photo-frame-brainstorm.md)

---

## 系统架构

```
微信小程序（uni-app/Vue3）
    │ HTTPS POST /api/upload（multipart/form-data）
    │ HTTPS POST /api/wx-login
    ↓
Go 后端 API（Gin）────── 照片文件 ──→ 腾讯云 COS
    │                                      ↑
    │ GET /api/photos（JSON 列表）          │ COS URL
    ↓                                      │
Android 相框 App ────── 定时轮询（60s）───┘
（Kotlin，Glide 缓存 COS 图片）
    │
    └── 元数据 ──→ MySQL 8
```

**关键决策（来自头脑风暴）：**
- Android 原生 App：控制屏幕常亮、亮度、横竖屏适配
- 定时轮询而非 WebSocket：简单可靠，60 秒延迟可接受
- 扫码绑定：相框显示小程序码，用户扫码后绑定，无需手动输入 ID
- 后端公网部署，需要域名 + HTTPS（微信小程序强制要求）

---

## 目录结构

```
photo-frame/
├── backend/                    # Go 后端
│   ├── main.go
│   ├── handlers/
│   │   ├── auth.go            # 微信登录、token 管理
│   │   ├── device.go          # 相框设备注册、绑定
│   │   ├── photo.go           # 照片上传、列表、删除
│   │   └── version.go         # APK 版本检查
│   ├── middleware/
│   │   └── auth.go            # JWT/Token 认证中间件
│   ├── models/
│   │   ├── device.go          # 设备模型
│   │   ├── user.go            # 用户模型（微信 openid）
│   │   └── photo.go           # 照片模型
│   ├── database/
│   │   └── mysql.go           # MySQL 8 初始化、迁移（使用 GORM）
│   ├── storage/
│   │   └── cos.go             # 腾讯云 COS 上传/删除封装
│   ├── Dockerfile
│   └── go.mod
├── miniprogram/                # 微信小程序（uni-app Vue3）
│   ├── src/
│   │   ├── pages/
│   │   │   ├── index/         # 首页：选择相框
│   │   │   ├── upload/        # 上传照片页
│   │   │   ├── bind/          # 扫码绑定页
│   │   │   └── manage/        # 管理已上传照片
│   │   ├── stores/
│   │   │   ├── user.ts        # 用户 openid、token
│   │   │   └── frame.ts       # 已绑定相框信息
│   │   ├── api/
│   │   │   └── index.ts       # API 封装（uploadFile、login 等）
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.ts
└── android/                    # Android 相框 App（Kotlin）
    ├── app/src/main/
    │   ├── java/com/photoframe/
    │   │   ├── MainActivity.kt         # 主界面（幻灯片展示）
    │   │   ├── SettingsActivity.kt     # 设置页
    │   │   ├── BindActivity.kt         # 首次启动绑定页（显示小程序码）
    │   │   ├── service/
    │   │   │   ├── PhotoSyncService.kt # 后台轮询获取新照片
    │   │   │   └── ScreenScheduler.kt  # 定时黑屏/亮屏
    │   │   ├── adapter/
    │   │   │   └── SlideShowAdapter.kt # ViewPager2 幻灯片 Adapter
    │   │   └── updater/
    │   │       └── AutoUpdater.kt      # 自动检查/下载/安装 APK
    │   └── res/
    │       ├── layout/
    │       └── xml/
    │           └── file_provider_paths.xml
    └── build.gradle
```
│   ├── src/
│   │   ├── pages/
│   │   │   ├── index/         # 首页：选择相框
│   │   │   ├── upload/        # 上传照片页
│   │   │   ├── bind/          # 扫码绑定页
│   │   │   └── manage/        # 管理已上传照片
│   │   ├── stores/
│   │   │   ├── user.ts        # 用户 openid、token
│   │   │   └── frame.ts       # 已绑定相框信息
│   │   ├── api/
│   │   │   └── index.ts       # API 封装（uploadFile、login 等）
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.ts
└── android/                    # Android 相框 App（Kotlin）
    ├── app/src/main/
    │   ├── java/com/photoframe/
    │   │   ├── MainActivity.kt         # 主界面（幻灯片展示）
    │   │   ├── SettingsActivity.kt     # 设置页
    │   │   ├── BindActivity.kt         # 首次启动绑定页（显示小程序码）
    │   │   ├── service/
    │   │   │   ├── PhotoSyncService.kt # 后台轮询获取新照片
    │   │   │   └── ScreenScheduler.kt  # 定时黑屏/亮屏
    │   │   ├── adapter/
    │   │   │   └── SlideShowAdapter.kt # ViewPager2 幻灯片 Adapter
    │   │   └── updater/
    │   │       └── AutoUpdater.kt      # 自动检查/下载/安装 APK
    │   └── res/
    │       ├── layout/
    │       └── xml/
    │           └── file_provider_paths.xml
    └── build.gradle
```

---

## Phase 1：Go 后端 API

### 数据模型（MySQL 8）

```sql
-- devices 表：相框设备
CREATE TABLE devices (
    id          VARCHAR(36) PRIMARY KEY,   -- UUID，相框唯一标识
    name        VARCHAR(100),              -- 相框名称（如"客厅相框"）
    qr_token    VARCHAR(64) UNIQUE,        -- 用于生成绑定二维码的 token
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- users 表：微信用户
CREATE TABLE users (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    openid      VARCHAR(64) UNIQUE NOT NULL,  -- 微信 openid
    nickname    VARCHAR(100),
    token       VARCHAR(64) UNIQUE,           -- 自定义登录态 token
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- device_users 表：相框与用户的绑定关系（多对多）
CREATE TABLE device_users (
    device_id   VARCHAR(36),
    user_id     BIGINT,
    PRIMARY KEY (device_id, user_id),
    FOREIGN KEY (device_id) REFERENCES devices(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- photos 表：照片记录
CREATE TABLE photos (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id   VARCHAR(36) NOT NULL,
    user_id     BIGINT NOT NULL,
    cos_key     VARCHAR(512) NOT NULL,      -- COS 对象 key（路径），如 photos/{device_id}/{uuid}.jpg
    cos_url     VARCHAR(1024) NOT NULL,     -- COS 访问 URL（可用 CDN 域名）
    taken_at    DATETIME NULL,              -- 拍摄时间（EXIF，可为空）
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_uploaded (device_id, uploaded_at),
    FOREIGN KEY (device_id) REFERENCES devices(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- versions 表：APK 版本管理
CREATE TABLE versions (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    version     VARCHAR(20) NOT NULL,       -- 如 "1.0.2"
    apk_url     VARCHAR(1024) NOT NULL,     -- APK 下载地址（可存在 COS）
    changelog   TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Go 侧使用 GORM + MySQL 8 驱动：**
```go
// go.mod 依赖
// gorm.io/gorm v1.25+
// gorm.io/driver/mysql v1.5+

dsn := "user:pass@tcp(127.0.0.1:3306)/photoframe?charset=utf8mb4&parseTime=True&loc=Local"
db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
```

### API 接口设计

```
# 认证
POST /api/wx-login          # 微信 code 换 token（公开）
POST /api/device/register   # 相框设备注册（返回 device_id 和 qr_token）

# 绑定（需要用户 token）
POST /api/bind              # 用户扫码绑定相框 {qr_token: "xxx"}
GET  /api/my/frames         # 获取我绑定的相框列表

# 照片（需要用户 token）
POST /api/upload            # 上传照片到 COS，元数据写 MySQL {file, device_id}
GET  /api/photos?device_id= # 获取相框照片列表（相框 App 轮询用）
DELETE /api/photos/:id      # 删除照片（同时删除 COS 对象和 MySQL 记录）

# 版本更新（无需认证）
GET  /api/version/latest    # 获取最新 APK 版本信息
```

### 关键实现要点

**相框设备认证：**
相框 App 首次启动时，调用 `/api/device/register` 获取 `device_id`，本地持久化存储。后续请求在 Header 中携带 `X-Device-ID`。设备注册无需密码，通过 `device_id` 唯一标识。

**用户 Token：**
小程序登录后，后端返回自定义 `token`（UUID），存储在小程序本地。后续请求 Header 携带 `Authorization: Bearer <token>`。Token 不设过期（简单方案），用户重新登录才更新。

**照片上传流程（COS）：**
1. 小程序将图片 multipart 上传到后端 `/api/upload`
2. 后端接收文件后，使用腾讯云 COS Go SDK（`github.com/tencentyun/cos-go-sdk-v5`）将文件流上传到 COS
3. 上传成功后，将 `cos_key`（对象路径）和 `cos_url`（访问 URL）写入 MySQL photos 表
4. 返回给客户端 `cos_url`，Android 相框 App 用 Glide 直接加载

```go
// storage/cos.go 核心逻辑
func (s *COSStorage) Upload(ctx context.Context, key string, reader io.Reader) (string, error) {
    _, err := s.client.Object.Put(ctx, key, reader, nil)
    if err != nil {
        return "", err
    }
    url := fmt.Sprintf("https://%s.cos.%s.myqcloud.com/%s",
        s.bucket, s.region, key)
    return url, nil
}

func (s *COSStorage) Delete(ctx context.Context, key string) error {
    _, err := s.client.Object.Delete(ctx, key)
    return err
}
```

**COS 对象 Key 命名规则：**
```
photos/{device_id}/{yyyy}/{mm}/{uuid}.jpg
```
按设备 + 年月分目录，便于管理和按需清理。

**照片列表接口：**
相框 App 轮询时，可带 `since` 参数（最后一张照片的 `uploaded_at`），只返回新照片，减少 MySQL 查询量。

```go
// GET /api/photos?device_id=xxx&since=2026-03-04T10:00:00Z
// 返回：[{id, cos_url, taken_at, uploader_name, uploaded_at}]
```

---

## Phase 2：Android 相框 App（Kotlin）

### 核心功能实现

**屏幕常亮（防系统休眠）：**
```kotlin
// MainActivity.onCreate() 中
window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
```

**亮度控制（定时黑屏）：**
```kotlin
fun setScreenBrightness(brightness: Float) {
    // brightness: 0.01f = 近乎黑屏，-1f = 跟随系统
    window.attributes = window.attributes.also {
        it.screenBrightness = brightness
    }
}
```
注意：`WindowManager.LayoutParams.screenBrightness` 控制的是当前 Activity 亮度，不影响其他 App，无需系统权限。

**定时黑屏/亮屏：**
使用 `Handler.postDelayed` 实现日内定时，每天在设定时间调整亮度。若 App 进程被杀（不常见，因为 `FLAG_KEEP_SCREEN_ON` 防止系统杀进程），使用 `AlarmManager` 兜底重启。

**幻灯片展示：**
- `ViewPager2` + `RecyclerView.Adapter`
- 图片加载：`Glide`（支持 URL、本地文件，自带内存/磁盘缓存）
- 切换动画：通过 `ViewPager2.setPageTransformer()` 实现多种效果（淡入淡出、滑动、缩放）
- 照片顺序/随机：本地维护 `List<Photo>`，随机模式时打乱顺序

**本地缓存：**
Glide 自动缓存 COS URL 图片到磁盘，无需额外处理。新照片轮询到后追加到列表末尾。COS URL 如果启用了 CDN 加速，Glide 加载速度更快。

**自动更新 APK：**
1. App 启动时调用 `/api/version/latest` 检查版本
2. 若服务端版本 > 当前版本，弹窗提示更新
3. 使用 `DownloadManager` 下载 APK 到外部存储
4. 下载完成后通过 `FileProvider` 触发系统安装

**首次绑定流程：**
1. App 首次启动检查本地是否有 `device_id`
2. 若无，调用后端注册接口获取 `device_id` 和 `qr_token`
3. 显示绑定页：用 `qr_token` 在本地生成二维码图片（使用 `zxing-android-embedded` 库）
4. 用户用微信扫码，小程序获取 `qr_token`，调用绑定接口
5. 相框 App 检测到绑定成功后（轮询或短暂轮询绑定状态）进入主界面

### 设置页配置项

- 每张照片展示时长（秒）：数字输入，默认 15
- 播放模式：顺序 / 随机（单选）
- 切换动画：淡入淡出 / 左右滑动 / 缩放（单选）
- 显示照片信息：开/关（日期、上传者）
- 定时黑屏：开/关，配置黑屏时间和亮屏时间（时间选择器）
- 当前版本 + 检查更新按钮
- 相框 ID（只读，用于调试）

---

## Phase 3：微信小程序（uni-app + Vue3）

### 页面结构

```
pages/
├── index/index.vue         # 首页：显示已绑定相框，入口
├── upload/index.vue        # 上传照片：选图 + 上传进度
├── bind/index.vue          # 扫码绑定新相框
└── manage/index.vue        # 管理照片：查看/删除已上传
```

### 关键流程

**登录流程（App 启动时）：**
```javascript
// App.vue onLaunch
const token = uni.getStorageSync('token')
if (!token) {
  uni.login({ provider: 'weixin', success: ({ code }) => {
    // POST /api/wx-login { code } → 返回 token
    uni.setStorageSync('token', res.data.token)
  }})
}
```

**上传照片：**
```javascript
// 1. 选择照片（最多9张）
uni.chooseMedia({ count: 9, mediaType: ['image'], ... })

// 2. 逐个上传（显示进度）
uni.uploadFile({
  url: `${BASE_URL}/api/upload`,
  filePath: tempFilePath,
  name: 'file',
  formData: { device_id: currentDeviceId },
  header: { Authorization: `Bearer ${token}` }
})
```

**注意：** 微信小程序 `uni.uploadFile` 单个文件无硬性大小限制（官方未明确），但实际建议单文件不超过 10MB，否则上传超时风险高。原图过大时建议用 `uni.compressImage` 压缩后再上传。

**扫码绑定：**
小程序通过 `wx.scanCode` 扫描相框显示的二维码，获取 `qr_token`，调用 `/api/bind` 完成绑定。

---

## 非功能要求

### HTTPS 配置

微信小程序强制要求 API 域名为 HTTPS，且证书需有效（不能自签）。推荐：
- 使用 Let's Encrypt 免费证书（`certbot` 自动续期）
- 在 Go 服务前放 Nginx 做 SSL 终止，Go 只监听 localhost:8080
- 照片文件由 COS 直接提供，无需 Nginx 转发静态资源

```nginx
# nginx.conf 核心配置
server {
    listen 443 ssl;
    server_name your-domain.com;
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
    }
    # 照片由 COS 直接提供，此处无需 /static/ 配置
}
```

### 腾讯云 COS 配置要点

- **Bucket 权限**：设置为私有读写，通过后端签名 URL 或直接用公开读（取决于安全需求；家用场景公开读即可）
- **防盗链**：可在 COS 控制台开启 Referer 白名单（可选，MVP 暂不做）
- **跨域（CORS）**：如果小程序未来需要直传 COS，需配置跨域规则；当前方案走后端中转，无需配置
- **Go SDK 依赖**：`github.com/tencentyun/cos-go-sdk-v5`
- **环境变量**：`COS_SECRET_ID`、`COS_SECRET_KEY`、`COS_BUCKET`、`COS_REGION`

### 安全考虑

- 上传接口必须校验用户 token，防止匿名上传
- 上传的文件只允许图片格式（后端校验 MIME type 和文件头）
- `device_id` 公开可知，但绑定操作需要用户 token（防止陌生人绑定相框）
- APK 下载 URL 需要防盗链（可选，MVP 可暂不做）

---

## 验收标准

### 功能验收

- [ ] 相框 App 首次启动显示绑定二维码
- [ ] 用户扫码后，相框自动进入展示界面
- [ ] 微信小程序可以选择本地照片并成功上传
- [ ] 相框在 60 秒内展示新上传的照片
- [ ] 相框按设定时长切换照片（顺序/随机两种模式）
- [ ] 照片切换时有过渡动画（至少 2 种可选）
- [ ] 屏幕角落显示拍摄日期和上传者昵称（可开关）
- [ ] 到达黑屏时间后屏幕变暗，到达亮屏时间后恢复
- [ ] App 检测到新版本后提示更新并自动安装
- [ ] 多个家庭成员可以绑定同一相框并各自上传照片

### 稳定性验收

- [ ] 相框 App 连续运行 24 小时不崩溃、不黑屏
- [ ] 断网后相框继续展示已缓存照片，重新联网后自动恢复轮询
- [ ] 上传失败时小程序给出明确提示，支持重试

---

## 成功标准

- 家人拍了照片，打开小程序，选择照片，上传，**60 秒内**在相框上看到
- 相框 7x24 小时运行，不被系统强制休眠（`FLAG_KEEP_SCREEN_ON`）
- 定时黑屏/亮屏按配置准时执行
- 老人也能用微信小程序完成上传（操作步骤 ≤ 3 步）

---

## 依赖和风险

| 风险 | 说明 | 缓解方案 |
|------|------|----------|
| 微信小程序需要 HTTPS | 上线前必须配置域名和 SSL 证书 | 开发阶段在微信开发者工具关闭域名校验 |
| 微信小程序需要备案 | 正式发布需通过微信审核 | MVP 阶段可用体验版，邀请特定用户使用 |
| Android 版本碎片 | 老平板可能是 Android 5-7 | 最低兼容 Android 5.0（API 21），慎用新 API |
| APK 安装权限 | Android 8.0+ 需要用户手动开启"安装未知来源" | 自动更新时引导用户开启，或首次安装时预先开启 |
| COS 费用 | 存储 + 流量费用随照片增多而增加 | 家用场景量小，月费用极低（几毛到几元）；可设置生命周期规则自动清理超旧照片 |
| COS 上传失败 | 网络抖动导致上传到 COS 失败 | 后端需处理 COS SDK 错误并返回 5xx，小程序侧提示重试 |
| MySQL 连接管理 | Go 服务重启后连接池需重建 | 使用 GORM 连接池配置，设置合理的 MaxOpenConns 和 MaxIdleConns |

---

## 实现顺序建议

1. **先搭后端**（Go + MySQL 8 + COS）：实现设备注册、照片上传/列表接口，用 curl 测试通过
2. **再做 Android App**：先实现静态照片展示，再接入后端轮询，Glide 加载 COS URL
3. **最后做小程序**：微信登录 + 上传照片，接入后端
4. 三端联调：完成扫码绑定、端到端上传展示完整流程
5. 实现定时黑屏、自动更新等进阶功能

---

## 参考资料

- **起源文档：** [docs/brainstorms/2026-03-04-photo-frame-brainstorm.md](../brainstorms/2026-03-04-photo-frame-brainstorm.md)
  - 关键决策：Android 原生 > Web（屏幕控制）、定时轮询（简单可靠）、扫码绑定、插电运行 → 亮度 0 等效黑屏
- uni-app Vue3 文档：https://uniapp.dcloud.net.cn/
- Gin 框架文档：https://gin-gonic.com/zh-cn/docs/
- GORM MySQL 文档：https://gorm.io/docs/connecting_to_the_database.html#MySQL
- 腾讯云 COS Go SDK：https://cloud.tencent.com/document/product/436/31215
- Android ViewPager2：https://developer.android.com/jetpack/androidx/releases/viewpager2
- Glide 图片加载：https://bumptech.github.io/glide/
- 微信小程序 wx.uploadFile：https://developers.weixin.qq.com/miniprogram/dev/api/network/upload/wx.uploadFile.html
