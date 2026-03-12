---
status: pending
priority: p2
issue_id: "048"
tags: [code-review, agent-native, backend]
dependencies: []
---

# 无专用 API 供 Agent 获取设备绑定码（Agent 原生缺口）

## Problem Statement

设置页的 QR 码完全由 Android 本地生成（读 SharedPreferences → ZXing 渲染），没有对应的 API 端点。Agent 被要求"帮用户生成邀请家人绑定相框的链接"时，无法以程序方式获取绑定码，只能让用户去物理查看屏幕——违背 Agent 原生设计原则（用户能做的，Agent 也应能做）。

目前 `GET /api/my/frames` 响应中意外包含了 `qr_token` 字段（`Device` 模型未用 `json:"-"` 隐藏），这是一个"意外的侧门"，但没有明确定义权限语义，也未文档化。

## Findings

**qr_token 意外暴露（backend/models/device.go 第 8 行）：**
```go
QrToken string `gorm:"type:varchar(64);uniqueIndex" json:"qr_token"`
// 没有 json:"-"，会出现在 /api/my/frames 响应中
```

**缺失的专用端点：** 没有 `GET /api/my/frames/:device_id/bind-token` 或等效接口。

**Agent 行动链断点：**
1. Agent 可调用 `GET /api/my/frames` 意外获取 `qr_token` ✅（侧门，未文档化）
2. Agent 无法通过明确定义的 API 获取绑定链接 ❌
3. Agent 调用 `POST /api/bind` 帮助新用户绑定 ✅

## Proposed Solutions

### 方案 A：明确 GET /api/my/frames 已含 qr_token 为正式行为（最小改动）
1. 在 API 文档中明确说明 `qr_token` 字段包含在响应中
2. 确认权限已校验（仅已绑定用户可获取）
3. 无需新增端点，Agent 通过已有接口获取绑定码
- **优点：** 0 后端代码改动
- **缺点：** 语义略混乱（相框列表接口顺带返回安全敏感的绑定码）

### 方案 B：新增专用端点（推荐）
```
GET /api/my/frames/:device_id/bind-token
Authorization: Bearer {user_token}
→ {"qr_token": "xxx", "bind_url": "photoframe://bind?qr_token=xxx"}
```
同时将 `Device.QrToken` 的 json tag 改为 `json:"-"`，从列表响应中隐藏。
- **优点：** 语义清晰，权限明确，同时修复意外暴露问题
- **缺点：** 需新增 Go handler 和路由

### 方案 C：隐藏 qr_token，不提供 API
将 `json:"qr_token"` 改为 `json:"-"`，彻底从所有响应中移除。Agent 无法获取绑定码，但也消除了意外暴露问题。
- **优点：** 安全性更好
- **缺点：** Agent 原生缺口依然存在

## Recommended Action

方案 A（短期）→ 方案 B（长期）。先明确当前行为是有意设计，再在后续迭代中提供规范的专用端点。

## Technical Details

- **受影响文件：**
  - `backend/models/device.go:8`（json tag）
  - `backend/handlers/frame.go` 或 `device.go`（新端点，如选方案 B）
  - `backend/main.go`（路由注册，如选方案 B）

## Acceptance Criteria

- [ ] Agent 可以通过明确定义的 API 获取设备绑定码（qr_token 或 bind_url）
- [ ] 该 API 需要用户认证，且校验调用方已绑定该设备
- [ ] qr_token 的 API 可访问性在文档或代码注释中有说明

## Work Log

- 2026-03-11: 发现于 PR #3 agent-native-reviewer 审查，标识为 NEEDS WORK
