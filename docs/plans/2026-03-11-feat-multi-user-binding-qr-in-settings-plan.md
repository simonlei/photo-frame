---
title: "feat: 设置页展示绑定二维码，支持多人绑定同一相框"
type: feat
status: completed
date: 2026-03-11
origin: docs/brainstorms/2026-03-04-photo-frame-brainstorm.md
---

# feat: 设置页展示绑定二维码，支持多人绑定同一相框

## Overview

在 Android 相框 App 的设置页（`SettingsActivity`）新增"邀请家人"区块，展示当前设备的绑定二维码，让家庭成员随时扫码加入，无需设备重置。后端数据层已完整支持多用户绑定，本次改动主要集中在 Android 前端。

（见 brainstorm: docs/brainstorms/2026-03-04-photo-frame-brainstorm.md — 核心决策：多人共用相框通过扫码邀请，所有绑定用户照片进入同一相册池）

## Problem Statement

目前绑定二维码只在 `BindActivity`（初始化页）显示，且每次启动都会重新注册设备、生成新 token。一旦设备完成初始化进入正常播放模式，就没有任何地方可以获取绑定码，导致：

- 第二个家庭成员无法扫码加入，必须由管理员重置设备
- 每次打开 `BindActivity` 都会注册新 token，旧截图中的二维码失效
- 用户没有办法分享"加入相框"的链接给家人

## Proposed Solution

1. **设置页新增"邀请家人"区块**：读取已存储的 `qrToken`，用 ZXing 在本地生成二维码，嵌入设置页展示
2. **修复 BindActivity 重复注册问题**：如果设备已有 `deviceId` 和 `qrToken`，注册接口改为幂等行为（先检查再注册）
3. **修复服务器地址变更时未清除 qrToken**：防止设置页显示过期的 token

## Technical Approach

### 现状分析

- **数据库层**：`device_users` 多对多表 + GORM `Association.Append` 天然支持 N 个用户绑定同一台设备，无需改动
- **后端 Bind 接口**：`POST /api/bind` 幂等，同一用户重复绑定同一设备安全（INSERT IGNORE 语义）
- **qrToken 持久化**：`AppPrefs.qrToken` 已有，设备注册成功后写入，随时可取用
- **ZXing 依赖**：`BarcodeEncoder` 已在 `BindActivity` 中使用，无需新增依赖

### 改动清单

#### 1. `activity_settings.xml` — 新增"邀请家人"区块

在"相框 ID"行下方、保存按钮上方，插入：

```xml
<!-- activity_settings.xml — 邀请家人区块示意 -->
<TextView
    android:id="@+id/tv_invite_title"
    android:text="邀请家人加入" />

<ImageView
    android:id="@+id/iv_invite_qr"
    android:layout_width="200dp"
    android:layout_height="200dp"
    android:contentDescription="绑定二维码" />

<TextView
    android:id="@+id/tv_invite_hint"
    android:text="扫码添加此相框，与家人共享照片" />
```

#### 2. `SettingsActivity.kt` — 加载并展示二维码

```kotlin
// SettingsActivity.kt — onCreate 中调用
private fun loadInviteQrCode() {
    val qrToken = prefs.qrToken
    if (qrToken.isNullOrBlank()) {
        ivInviteQr.visibility = View.GONE
        return
    }
    val content = "photoframe://bind?qr_token=$qrToken"
    try {
        val bitmap = BarcodeEncoder().encodeBitmap(
            content, BarcodeFormat.QR_CODE, 512, 512
        )
        ivInviteQr.setImageBitmap(bitmap)
        ivInviteQr.visibility = View.VISIBLE
    } catch (e: Exception) {
        ivInviteQr.visibility = View.GONE
    }
}
```

#### 3. `SettingsActivity.kt` — 服务器地址变更时清除 qrToken

```kotlin
// SettingsActivity.kt 第 99-103 行，现有清除逻辑处
prefs.isBound = false
prefs.userToken = null
prefs.deviceId = null
prefs.lastSyncTime = 0L
prefs.qrToken = null   // ← 新增：防止展示过期 token
```

#### 4. `BindActivity.kt` — 幂等注册（如果已有 deviceId 则复用）

```kotlin
// BindActivity.kt — registerDevice() 中
private fun registerDevice() {
    val existingDeviceId = prefs.deviceId
    val existingQrToken = prefs.qrToken
    if (!existingDeviceId.isNullOrBlank() && !existingQrToken.isNullOrBlank()) {
        // 已注册，直接复用，展示现有 QR
        showQrCode(existingQrToken)
        startPollingBindStatus(existingDeviceId)
        return
    }
    // 否则，走现有的 /api/device/register 流程
    // ...现有逻辑...
}
```

## System-Wide Impact

### Interaction Graph

- 用户进入 `SettingsActivity` → `onCreate` → `loadInviteQrCode()` → 读 `AppPrefs.qrToken` → ZXing 生成位图 → `ImageView.setImageBitmap()`（无网络请求）
- 家人用微信小程序扫码 → `bind.js` 调用 `POST /api/bind` → 后端 `Association.Append(user)` → `device_users` 插入记录 → 下次 `PhotoSyncService` 轮询时新用户照片开始同步

### Error Propagation

- `BarcodeEncoder.encodeBitmap()` 失败时（内存不足/非法输入）→ try/catch → 隐藏 `ImageView`，不崩溃
- `qrToken == null` → 直接隐藏 QR 区块，不显示错误（qrToken 仅在服务器地址变更后为空，此时整个设置页已有"需要重新绑定"的视觉提示）

### State Lifecycle Risks

- 服务器地址变更 → 同步清除 `qrToken`，避免设置页展示旧 token
- `BindActivity` 幂等注册 → 复用已有 token，避免现有家人的旧截图失效

### API Surface Parity

- 无新增 API 接口，复用现有 `/api/bind` 端点
- 后端 `device_users` 多对多关系已支持多用户，无需改动

## Acceptance Criteria

- [x] 进入设置页时，"邀请家人"区块显示 200dp×200dp 的 QR 码
- [x] QR 码内容格式为 `photoframe://bind?qr_token={token}`，可被微信扫描识别
- [x] 微信小程序扫描该 QR 后，能成功完成绑定（调用现有 `/api/bind` 接口）
- [x] 多个不同用户扫描同一 QR 后，均能在小程序"我的相框"列表中看到该设备
- [x] 变更服务器地址并保存后，设置页 QR 区块不显示（qrToken 已清除，等待重新注册）
- [x] 设备重启后再次进入设置页，QR 码内容与变更前一致（token 持久化正确）
- [x] `BindActivity` 不再每次重新注册：已有 `deviceId` 时直接复用，展示现有 QR
- [x] QR 生成失败（ZXing 异常）时，设置页不崩溃，QR 区块静默隐藏

## Dependencies & Risks

| 风险 | 可能性 | 影响 | 缓解方案 |
|------|--------|------|----------|
| `BindActivity` 复用逻辑引入 token 失效 bug（服务器重置后 token 不再有效） | 低 | 扫码时报错 | 保持现有"强制重注册"作为降级路径：用户若扫码失败，可手动更改服务器地址触发重注册 |
| 旧版 App 截图中的 QR 因新注册逻辑而失效（用户已截图保存旧 QR） | 中 | 用户体验 | 文案上说明"截图后直接分享，无需重新截图"；token 不再轮换后旧截图永久有效 |
| ZXing `encodeBitmap` 在低内存设备上 OOM | 低 | 静默失败 | try/catch 已覆盖，不影响设置页其他功能 |

## Implementation Notes

**不需要改动：**
- 后端任何代码（绑定逻辑已完整支持多用户）
- 微信小程序代码（扫码绑定流程不变）
- 数据库 schema（`device_users` 已是多对多）

**唯一新增的 Android 依赖：**
- 无（ZXing `BarcodeEncoder` 已在 `BindActivity` 中引入）

**关于"已绑定用户列表"：**
- 本次 MVP 不显示已绑定用户数量（需要后端新接口），后续迭代再加

**关于安全：**
- `qrToken` 对所有能看到设备屏幕的人可见，这是设计决策（家庭场景，无需严格访问控制）
- 解决方案 docs 提到 `ConstantTimeCompare`，但本功能不涉及 token 比较逻辑，无需处理

## Sources & References

### Origin

- **Brainstorm 文档：** [docs/brainstorms/2026-03-04-photo-frame-brainstorm.md](../brainstorms/2026-03-04-photo-frame-brainstorm.md)
  - 关键决策继承：多人共用相框通过扫码邀请；小程序扫码用 `qr_token` 建立绑定关系；`qrToken` 持久存储

### Internal References

- Android 设置页逻辑：`android/app/src/main/java/com/photoframe/SettingsActivity.kt`
- Android 设置页布局：`android/app/src/main/res/layout/activity_settings.xml`
- Android 二维码参考实现：`android/app/src/main/java/com/photoframe/BindActivity.kt`
- Android 本地存储（含 qrToken）：`android/app/src/main/java/com/photoframe/data/AppPrefs.kt`
- 后端绑定 handler：`backend/handlers/device.go`
- 后端数据模型：`backend/models/device.go`、`backend/models/user.go`
- 小程序绑定页：`miniprogram/pages/bind/bind.js`

### Institutional Learnings

- QR Token 格式：`photoframe://bind?qr_token=...`，微信小程序使用 regex 解析（不支持 `new URL()`）（见 `docs/solutions/miniprogram-patterns/native-miniprogram-migration-and-patterns.md`）
- 所有资源访问接口需校验 `isUserBoundToDevice()` — 本次无新接口，不影响现有安全模型（见 `docs/solutions/multi-category/photo-frame-comprehensive-code-review.md`）
