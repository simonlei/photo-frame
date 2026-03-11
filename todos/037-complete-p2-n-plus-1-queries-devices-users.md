---
status: pending
priority: p2
issue_id: "037"
tags: [code-review, performance, database]
---

# AdminListDevices 和 AdminListUsers 存在 N+1 查询且无分页

## Problem Statement

两个 handler 先 `db.Find()` 全量加载，再在循环中对每条记录执行 2 次 COUNT 查询，产生 1+2N 个数据库请求。同时没有分页，当数据量增大时响应将超时。

## Findings

- **File:** `backend/handlers/admin.go:56-76` (AdminListDevices)
- **File:** `backend/handlers/admin.go:174-193` (AdminListUsers)
- 同文件的 `AdminStats` 已正确使用 GROUP BY 聚合，可作为参考
- 前端 `api.devices()` 和 `api.users()` 无分页参数

| 记录数 | DB 查询数 | 预估耗时 |
|--------|-----------|---------|
| 50     | 101       | ~100ms  |
| 500    | 1,001     | ~1s     |
| 5,000  | 10,001    | 超时     |

## Proposed Solutions

### Option A: 单条 GROUP BY 聚合 + 分页（推荐）
```go
type deviceRow struct {
    ID         string
    Name       string
    CreatedAt  time.Time
    UserCount  int64
    PhotoCount int64
}
var rows []deviceRow
db.Table("devices").
    Select("devices.id, devices.name, devices.created_at, "+
        "COUNT(DISTINCT du.user_id) as user_count, "+
        "COUNT(DISTINCT p.id) as photo_count").
    Joins("LEFT JOIN device_users du ON du.device_id = devices.id").
    Joins("LEFT JOIN photos p ON p.device_id = devices.id").
    Group("devices.id, devices.name, devices.created_at").
    Offset(offset).Limit(pageSize).
    Scan(&rows)
```
添加 `page`/`page_size` 参数（参考 `AdminListDevicePhotos` 已有实现）。

## Acceptance Criteria

- [ ] 使用单条 GROUP BY 查询替换 N+1 循环
- [ ] 添加分页参数 `page`/`page_size`
- [ ] 前端 Devices 和 Users 页面支持服务端分页
- [ ] 添加 `device_users.device_id`、`device_users.user_id` 索引

## Work Log

- 2026-03-11: Found by performance-oracle and architecture-strategist agents
