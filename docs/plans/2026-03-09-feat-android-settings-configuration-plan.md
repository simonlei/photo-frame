---
title: "feat: Android 端补全配置能力"
type: feat
status: completed
date: 2026-03-09
---

# feat: Android 端补全配置能力

## 概述

安卓相框 App 的基础功能已完成，但设置页存在若干**规划中但未实现**的配置项，同时服务器地址硬编码、Token 无刷新机制等问题影响了实际部署能力。本计划梳理并补全这些缺口。

---

## 配置项现状全景

| 配置项 | AppPrefs Key | UI 控件 | 代码中应用 | 状态 |
|--------|-------------|---------|-----------|------|
| 照片切换间隔 | `slide_duration_sec` | EditText | `handler.postDelayed(…, sec * 1000L)` | ✅ 已实现 |
| 播放顺序 | `play_mode` | RadioGroup | `allPhotos.shuffle()` | ✅ 已实现 |
| 切换动画 | `transition_effect` | Spinner | `FadePageTransformer` / `ZoomPageTransformer` | ✅ 已实现 |
| 显示照片信息 | `show_photo_info` | Switch | TextView 可见性 | ✅ 已实现 |
| 定时黑屏开关 | `night_mode_enabled` | Switch | ScreenScheduler 判断 | ✅ 已实现 |
| 黑屏开始时间 | `night_start_hour/minute` | TimePicker | ScreenScheduler 计算分钟数 | ✅ 已实现 |
| 亮屏时间 | `night_end_hour/minute` | TimePicker | ScreenScheduler 计算分钟数 | ✅ 已实现 |
| **服务器地址** | — | **无** | 硬编码于 strings.xml | ❌ 未实现 |
| **版本号显示** | — | **无** | — | ❌ 未实现 |
| **检查更新按钮** | — | **无** | AutoUpdater 仅自动触发 | ❌ 未实现 |
| **Token 过期处理** | — | — | 401 静默失败 | ❌ 未实现 |

---

## 问题陈述

### 1. 服务器地址无法运行时配置（高优先级）

`strings.xml` 中硬编码了开发地址：

```xml
<!-- android/app/src/main/res/values/strings.xml -->
<string name="server_base_url">http://10.0.2.2:8080</string>
```

- 每次部署到新服务器都必须重新构建 APK
- 用户无法通过 UI 修改连接目标
- **影响**：相框无法连接到生产服务器

### 2. 设置页缺少版本信息与手动更新入口（中优先级）

`AutoUpdater` 仅在 `MainActivity.onCreate()` 时自动检查一次，用户无法：
- 查看当前 App 版本号
- 手动触发检查更新

原始设计文档（`docs/plans/2026-03-04-feat-photo-frame-system-plan.md`）明确规划了这两项，但未实现。

### 3. ScreenScheduler 与 MainActivity 夜间模式状态不同步（低优先级）

```kotlin
// android/.../MainActivity.kt 第 35 行
private var isNightMode = false
// ScreenScheduler 调整亮度时不通知 MainActivity，导致触摸唤醒逻辑可能判断错误
```

### 4. Token 无主动刷新机制（中优先级）

后端已为 Token 设置 30 天过期，但安卓端：
- 没有检测 Token 过期（401 响应）后重新绑定的逻辑
- Token 过期后 App 会静默失败，用户看不到任何提示

---

## 提议方案

### 功能 A：SettingsActivity 补全缺失配置项

在 `SettingsActivity` 中新增以下 UI 控件：

| 控件 | 位置 | 功能 |
|------|------|------|
| `TextView` 版本号 | "关于" 区块 | 显示 `BuildConfig.VERSION_NAME` |
| `Button` 检查更新 | "关于" 区块下方 | 触发 `AutoUpdater.checkAndUpdate()` |
| `EditText` 服务器地址 | 顶部 "连接" 区块 | 允许修改服务器地址，保存到 `AppPrefs` |

**服务器地址修改流程：**
1. 用户编辑地址并保存
2. 写入 `AppPrefs.serverBaseUrl`（新增字段，默认读取 `strings.xml`）
3. 重新初始化 `ApiClient` 单例
4. 清除已缓存的照片列表，触发重新绑定（导航到 `BindActivity`）

### 功能 B：AppPrefs 新增服务器地址配置

```kotlin
// android/.../data/AppPrefs.kt
var serverBaseUrl: String
    get() = prefs.getString("server_base_url", context.getString(R.string.server_base_url)) ?: context.getString(R.string.server_base_url)
    set(value) = prefs.edit { putString("server_base_url", value) }
```

`ApiClient` 改为从 `AppPrefs` 读取 URL：

```kotlin
// android/.../data/ApiClient.kt
fun init(context: Context) {
    val baseUrl = AppPrefs(context).serverBaseUrl
    // ... 使用 baseUrl 构建 Retrofit
}
```

### 功能 C：Token 过期处理（401 拦截器）

在 `ApiClient` 的 OkHttp 拦截器中处理 401 响应：

```kotlin
// android/.../data/ApiClient.kt
.addInterceptor { chain ->
    val response = chain.proceed(chain.request())
    if (response.code == 401) {
        // 清除 Token，通知 App 重新绑定
        AppPrefs(context).apply {
            userToken = null
            isBound = false
        }
        // 发送广播或调用全局状态让 MainActivity 跳转 BindActivity
    }
    response
}
```

### 功能 D：ScreenScheduler 回调同步夜间状态

```kotlin
// android/.../service/ScreenScheduler.kt
interface NightModeListener {
    fun onNightModeChanged(isNight: Boolean)
}

// MainActivity 实现此接口，ScreenScheduler 持有弱引用回调
```

---

## 技术考量

### 架构影响

- `ApiClient` 从静态/懒加载单例改为支持重新初始化（修改服务器地址场景需要）
- `AppPrefs` 增加 `serverBaseUrl` 字段，读取时有默认值回退逻辑

### 安全考量

- 服务器地址输入需做基本 URL 格式校验（避免空字符串/非法字符）
- 修改服务器地址后需清除旧 Token（防止 Token 被发送到错误服务器）
- 不要在日志中打印 Token 明文

### 兼容性

- `AppPrefs.serverBaseUrl` 的默认值回退到 `strings.xml` 中的值，保证升级后无感兼容

---

## 验收标准

### 功能 A（设置页补全）

- [x] `SettingsActivity` 显示当前 App 版本号（格式：`v1.0.0`）
- [x] 点击"检查更新"按钮触发 `AutoUpdater.checkAndUpdate()`，有加载反馈
- [x] `SettingsActivity` 顶部有可编辑的"服务器地址"输入框，回显当前地址

### 功能 B（运行时服务器地址）

- [x] 修改服务器地址并保存后，App 重新执行设备注册/绑定流程
- [x] 服务器地址格式非法时，显示错误提示，不保存
- [x] App 重启后，服务器地址从 `AppPrefs` 读取，不再依赖硬编码值

### 功能 C（Token 过期处理）

- [x] 后端返回 401 时，App 清除 Token 并自动跳转到 `BindActivity`
- [x] `BindActivity` 显示"登录已过期，请重新绑定"提示文字

### 功能 D（夜间模式状态同步）

- [x] `ScreenScheduler` 调整亮度时，`MainActivity.isNightMode` 同步更新
- [x] 夜间模式下触摸屏幕，亮度恢复逻辑正常工作

---

## 系统级影响

### 交互图

```
用户修改服务器地址
  └→ SettingsActivity.saveSettings()
       └→ AppPrefs.serverBaseUrl = newUrl
            └→ ApiClient.reinit(context)   ← 需新增方法
                 └→ BindActivity 启动（清除绑定状态）
                      └→ 重新执行注册 + 扫码绑定流程
```

```
PhotoSyncService 收到 401
  └→ OkHttp Authenticator / 拦截器
       └→ AppPrefs.isBound = false, userToken = null
            └→ EventBus/广播 → MainActivity
                 └→ startActivity(BindActivity)
```

### 错误传播

- `ApiClient.reinit()` 失败（URL 格式错误）：在 `SettingsActivity` 层捕获并展示 Toast，不写入 `AppPrefs`
- `AutoUpdater.checkAndUpdate()` 失败：显示"检查更新失败，请稍后重试"，不影响其他功能

### 状态生命周期风险

- `ApiClient` 重新初始化期间，`PhotoSyncService` 可能发起请求：需在重初始化前停止 Service，完成后重启
- 旧 Token 清除后，Glide 的 `AuthGlideModule` 仍在内存中持有旧 Header 拦截器 → 需触发 Glide 清除内存缓存

---

## 依赖与风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| `ApiClient` 重初始化竞态 | 中 | 高 | 使用 `@Synchronized` 或重初始化前停止 `PhotoSyncService` |
| Glide 内存缓存持有旧 Token | 低 | 中 | 修改服务器地址后调用 `Glide.get(context).clearMemory()` |
| 用户填入错误 URL 导致无法绑定 | 高 | 高 | URL 格式验证 + 提供"重置为默认"选项 |

---

## 实现文件清单

| 文件 | 修改类型 | 修改内容 |
|------|----------|----------|
| `android/app/src/main/java/com/photoframe/data/AppPrefs.kt` | 修改 | 新增 `serverBaseUrl` 属性 |
| `android/app/src/main/java/com/photoframe/data/ApiClient.kt` | 修改 | 从 `AppPrefs` 读取 URL，新增 `reinit()` 方法，401 处理拦截器 |
| `android/app/src/main/java/com/photoframe/SettingsActivity.kt` | 修改 | 新增版本号 TextView、检查更新 Button、服务器地址 EditText |
| `android/app/src/main/java/com/photoframe/service/ScreenScheduler.kt` | 修改 | 新增 `NightModeListener` 回调接口 |
| `android/app/src/main/java/com/photoframe/MainActivity.kt` | 修改 | 实现 `NightModeListener`，处理 401 广播跳转逻辑 |
| `android/app/src/main/res/layout/activity_settings.xml` | 修改 | 新增服务器地址输入框、版本号、检查更新按钮 |

---

## 成功指标

- 相框 App 无需重新打包即可切换到任意服务器
- 用户可在 UI 中查看版本号并手动触发更新检查
- Token 过期不再导致静默失败，有明确的重新绑定引导
- 夜间模式触摸唤醒行为一致

---

## 来源与参考

### 内部参考

- 原始系统设计计划：[docs/plans/2026-03-04-feat-photo-frame-system-plan.md](../plans/2026-03-04-feat-photo-frame-system-plan.md)
- 原始头脑风暴：[docs/brainstorms/2026-03-04-photo-frame-brainstorm.md](../brainstorms/2026-03-04-photo-frame-brainstorm.md)
- 代码审查记录：[docs/solutions/multi-category/photo-frame-comprehensive-code-review.md](../solutions/multi-category/photo-frame-comprehensive-code-review.md)

### 关键代码位置

- `AppPrefs`：`android/app/src/main/java/com/photoframe/data/AppPrefs.kt`
- `ApiClient`：`android/app/src/main/java/com/photoframe/data/ApiClient.kt`
- `SettingsActivity`：`android/app/src/main/java/com/photoframe/SettingsActivity.kt`
- `AutoUpdater`：`android/app/src/main/java/com/photoframe/updater/AutoUpdater.kt`
- `ScreenScheduler`：`android/app/src/main/java/com/photoframe/service/ScreenScheduler.kt`
- 服务器地址硬编码：`android/app/src/main/res/values/strings.xml`
