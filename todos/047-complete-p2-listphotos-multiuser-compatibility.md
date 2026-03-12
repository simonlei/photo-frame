---
status: pending
priority: p2
issue_id: "047"
tags: [code-review, architecture, backend]
dependencies: []
---

# 多用户绑定后 ListPhotos 查询逻辑是否正确未经确认

## Problem Statement

PR #3 的核心目标是"多人绑定同一相框，照片进入同一相册池"，但设备端使用 `users[0].Token` 调用 `GET /api/photos`，而后端 `ListPhotos` 的查询逻辑未经确认。如果 `ListPhotos` 按 `user_id`（认证用户）过滤，则只有第一个绑定用户上传的照片能同步到设备，其他用户的照片永远不会出现——功能目标实际上没有达成。

## Findings

**后端设备认证模型（device.go 第 63-86 行）：**
```go
// DeviceBindStatus 只返回第一个绑定用户的 token
c.JSON(http.StatusOK, gin.H{
    "bound":      true,
    "user_token": users[0].Token,
})
```

设备使用 `users[0].Token` 调用所有受保护接口。`ListPhotos` 的查询取决于后端实现：
- **按 device_id 查询** → 返回所有绑定用户的照片 ✅ 多用户功能正确
- **按 user_id（认证用户）查询** → 只返回 users[0] 的照片 ❌ 多用户功能静默失败

**需要检查的文件：** `backend/handlers/photo.go` — `ListPhotos` handler 的 WHERE 条件

## Proposed Solutions

### 方案 A：确认 ListPhotos 按 device_id 查询（无需改动）
读取 `backend/handlers/photo.go`，验证查询包含 `WHERE device_id = ?`（或等效）而非 `WHERE user_id = ?`。若是，则当前实现已经正确，关闭此 issue。

### 方案 B：修改 ListPhotos 按 device_id 聚合
若当前按 user_id 查询，改为：
```go
// 按设备 ID 查询该设备下所有照片
db.Where("device_id = ?", deviceID).Order("uploaded_at DESC").Find(&photos)
```
需要确认 middleware 中如何将 device_id 关联到认证用户。

## Recommended Action

先执行方案 A（读代码确认），若有问题再执行方案 B。

## Technical Details

- **关键文件：** `backend/handlers/photo.go`（ListPhotos handler）
- **关联问题：** `DeviceBindStatus` 返回 `users[0].Token` 是更深层的架构问题（设备应有自己的 token），但当前阶段的最小验证是确认 ListPhotos 查询正确

## Acceptance Criteria

- [ ] 确认 `ListPhotos` 按 `device_id` 聚合查询所有绑定用户的照片
- [ ] 若不是，修改查询并验证多用户场景下照片正确展示
- [ ] 用户 A 和用户 B 各上传一张照片后，相框端能看到两张

## Work Log

- 2026-03-11: 发现于 PR #3 架构审查，architecture-strategist agent 标识为 P3（确认后可关闭）
