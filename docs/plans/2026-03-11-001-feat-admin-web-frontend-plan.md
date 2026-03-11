---
title: feat: 管理后台前端页面
type: feat
status: completed
date: 2026-03-11
origin: docs/brainstorms/2026-03-04-photo-frame-brainstorm.md
---

# feat: 管理后台前端页面

## Overview

为电子相框系统构建一个 Web 管理后台，管理员可通过浏览器管理所有相框设备、查看和删除相册中的照片、统计系统用量。

当前系统没有任何管理界面——所有管理操作均依赖直接操作数据库。本功能填补这一空白。

---

## Problem Statement / Motivation

- 系统运行后，无法在不登录服务器的情况下查看有哪些用户/设备、照片上传情况
- 无法方便地清理问题照片或失效设备
- 无法直观了解存储用量、活跃度等关键指标

---

## Proposed Solution

两个部分并行交付：

1. **后端新增 Admin API**（Go + Gin）：添加独立的 `/api/admin/` 路由组，通过环境变量 `ADMIN_TOKEN` 鉴权（简单固定 Token，无需数据库）
2. **前端管理页面**（React + Vite，静态文件）：打包后由 nginx 以 `/admin/` 路径提供服务，无需修改 Go 服务

---

## Technical Considerations

### 鉴权方案

后端已有 WeChat Bearer Token 体系，不适合管理员使用。推荐方案：

- 环境变量 `ADMIN_TOKEN=<随机长字符串>` 配置管理员密钥
- 前端登录页输入此 Token，存入 `localStorage`，后续请求带 `Authorization: Bearer <admin_token>` 头
- 后端 admin 中间件只检查此固定 Token，**与普通用户 Token 完全分开**

> 优点：零数据库改动，部署简单，足够安全（内网/VPN 场景）

### Album（相册）概念

当前 schema 无 `albums` 表，照片仅通过 `device_id` 分组。**MVP 阶段以"设备 = 相册"处理**，无需迁移数据库。每个设备的照片池就是它的"相册"。

> 后续若有相册分组需求，可独立迭代（见 brainstorm 未来规划）

### 前端技术选型

- **React 18 + Vite**（轻量，构建产物为静态文件，适合 nginx 直接 serve）
- **Ant Design** 或 **shadcn/ui**（管理后台 UI 组件库）
- 打包输出到 `admin-frontend/dist/`，nginx 配置：
  ```nginx
  location /admin/ {
      alias /app/admin-frontend/dist/;
      try_files $uri $uri/ /admin/index.html;
  }
  ```

### 存储用量统计

Tencent COS 提供存储量查询 API，但调用有延迟。MVP 阶段通过数据库 `photos` 表的记录数作为近似用量（条数统计），暂不计算字节数。

---

## System-Wide Impact

- **新增后端路由**：`/api/admin/*` 路由组，不影响现有 `/api/` 路由
- **新增环境变量**：`ADMIN_TOKEN`，需更新 `.env.example` 和 CI/CD 配置
- **nginx 变更**：新增 `/admin/` 静态文件路由
- **无数据库 schema 变更**（MVP 阶段）

---

## Acceptance Criteria

### 登录

- [x] 访问 `/admin/` 显示登录页，输入 Admin Token 后进入管理界面
- [x] Token 错误返回 401，前端提示错误
- [x] Token 存入 `localStorage`，刷新页面不需重新登录

### 概览/用量统计

- [x] 首页展示：总用户数、总设备数、总照片数
- [x] 各设备照片数量排行（Top 10）

### 设备（相册）管理

- [x] 列表展示所有设备：设备 ID、名称、绑定用户数、照片数、创建时间
- [x] 可查看某设备下所有照片（缩略图网格）
- [x] 可删除某张照片（调用现有 DELETE 接口或新增 admin 删除接口）
- [x] 可删除整个设备（级联删除其所有照片，含 COS 清理）

### 用户管理

- [x] 列表展示所有用户：昵称、创建时间、绑定设备数、上传照片数
- [x] 可查看某用户上传的所有照片

### 照片浏览

- [x] 跨设备浏览所有照片（按上传时间倒序，分页）
- [x] 点击缩略图可预览大图
- [x] 可批量删除照片

---

## New Backend Endpoints Required

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/stats` | 总览统计：用户数、设备数、照片数 |
| GET | `/api/admin/devices` | 设备列表（含绑定用户数、照片数） |
| DELETE | `/api/admin/devices/:id` | 删除设备及其所有照片（含 COS） |
| GET | `/api/admin/devices/:id/photos` | 某设备下的照片列表（分页） |
| GET | `/api/admin/users` | 用户列表（含绑定设备数、照片数） |
| GET | `/api/admin/photos` | 所有照片列表（分页，按上传时间倒序） |
| DELETE | `/api/admin/photos/:id` | 管理员强制删除照片（含 COS） |

Admin 中间件文件：`backend/middleware/admin_auth.go`

---

## Implementation Phases

### Phase 1：后端 Admin API

文件变更：
- `backend/middleware/admin_auth.go` — 新增 admin token 鉴权中间件
- `backend/handlers/admin.go` — 所有 admin 接口 handler
- `backend/main.go` — 注册 `/api/admin/` 路由组

验收：用 curl 可以调通全部 admin 接口

### Phase 2：前端脚手架 + 登录页

文件：
- `admin-frontend/` — Vite + React 项目目录
- `admin-frontend/src/pages/Login.tsx` — Token 登录页
- `admin-frontend/src/lib/api.ts` — API 请求封装（带 admin token）

### Phase 3：核心页面

文件：
- `admin-frontend/src/pages/Dashboard.tsx` — 概览统计
- `admin-frontend/src/pages/Devices.tsx` — 设备列表
- `admin-frontend/src/pages/DevicePhotos.tsx` — 设备相册浏览
- `admin-frontend/src/pages/Users.tsx` — 用户列表
- `admin-frontend/src/pages/Photos.tsx` — 全量照片浏览

### Phase 4：部署集成

文件：
- `nginx.conf` — 新增 `/admin/` 静态文件路由
- `docker-compose.yml` — 挂载 `admin-frontend/dist`（或构建步骤集成）
- `.env.example` — 新增 `ADMIN_TOKEN=`
- `.github/workflows/` — CI 构建前端并打包（可选）

---

## Dependencies & Risks

| 风险 | 可能性 | 缓解措施 |
|------|--------|----------|
| 删除设备时 COS 清理部分失败 | 中 | 先软删除 DB 记录，COS 清理失败时记录日志，不阻断响应 |
| Admin Token 泄漏 | 低 | 文档说明仅在内网/VPN 暴露 `/admin/` 路径；nginx 可加 IP 白名单 |
| 前端与后端 API 格式不一致 | 低 | Phase 1 优先完成并用 curl 验证，前端基于验证后的实际响应开发 |

---

## Success Metrics

- 管理员可在 1 分钟内找到任意用户上传的照片并删除
- 首页统计数据加载时间 < 1 秒（数据库查询，无外部依赖）
- 无需 SSH 登录服务器即可完成所有日常管理操作

---

## Sources & References

- **Origin brainstorm:** [docs/brainstorms/2026-03-04-photo-frame-brainstorm.md](../brainstorms/2026-03-04-photo-frame-brainstorm.md)
  - 决策：后端自托管，照片存于 Tencent COS
  - 决策：多用户共用相框，通过 `device_id` 关联
  - 未来规划：相册分组（本 MVP 跳过）
- 现有后端路由：`backend/main.go`
- Photo 模型：`backend/models/photo.go`
- 鉴权中间件参考：`backend/middleware/auth.go`
- 小程序照片管理参考：`miniprogram/pages/manage/manage.js`
- nginx 配置：`nginx.conf`
