---
status: pending
priority: p1
issue_id: "004"
tags: [code-review, quality, dead-code, go]
dependencies: []
---

# P1: photo.go 中存在死代码（_ = count）

## Problem Statement

`backend/handlers/photo.go` 的 `UploadPhoto` 函数中包含无用的死代码 `_ = count`。这段代码查询了照片数量但将结果丢弃，同时可能掩盖了原本应有的业务逻辑（例如：限制每个设备的最大照片数量），导致：
1. 代码可读性下降，让 reviewer 疑惑该检查是否有意义
2. 浪费一次数据库查询（性能损耗）
3. 可能遗漏了本应实现的照片数量上限功能

## Findings

**受影响文件:** `backend/handlers/photo.go`

```go
// UploadPhoto 函数中（约第35-43行）：
var count int64
db.Model(&models.Photo{}).Where("device_id = ?", deviceID).Count(&count)
_ = count  // 死代码：查询了但完全没用这个值，疑似遗漏了 if count >= MAX_PHOTOS 的判断
```

此外，同一函数中还存在 3 次冗余 DB 查询：
1. 查询 count（死代码）
2. 查询 device（校验设备存在性）
3. 查询 device 的 users 关联（校验用户绑定关系）

这 3 次查询中，第 2 和第 3 次可以合并为一次 JOIN 查询。

## Proposed Solutions

### 方案 A（推荐）：删除死代码，补充照片数量限制
如果原意是限制每个设备最多存 N 张照片，则补全该逻辑：
```go
const maxPhotosPerDevice = 500

var count int64
db.Model(&models.Photo{}).Where("device_id = ?", deviceID).Count(&count)
if count >= maxPhotosPerDevice {
    c.JSON(http.StatusBadRequest, gin.H{"error": "照片数量已达上限"})
    return
}
```
- 优点：消除死代码，同时补充合理的业务限制防止磁盘滥用
- 缺点：需要确定合理的上限值
- 风险：低

### 方案 B：直接删除死代码（如无上限需求）
如果确实不需要照片数量限制，直接删除这 3 行：
```go
// 删除以下内容
var count int64
db.Model(&models.Photo{}).Where("device_id = ?", deviceID).Count(&count)
_ = count
```
- 优点：最简单，消除混乱
- 缺点：缺少对照片数量的保护
- 风险：低

### 方案 C：同时优化冗余查询
合并设备存在性校验和用户绑定关系校验为一次 JOIN：
```go
var device models.Device
err := db.Joins("JOIN device_users ON device_users.device_id = devices.id").
    Where("devices.id = ? AND device_users.user_id = ?", deviceID, user.ID).
    First(&device).Error
if err != nil {
    c.JSON(http.StatusForbidden, gin.H{"error": "无权上传到该相框"})
    return
}
```
- 优点：减少 DB 往返，提升性能
- 缺点：SQL 更复杂
- 风险：低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `backend/handlers/photo.go` - UploadPhoto 函数，约第 35-43 行

## Acceptance Criteria

- [ ] 删除 `_ = count` 及其相关的冗余 DB 查询
- [ ] 若有照片数量上限需求，补充相应逻辑并在 README/文档中说明上限值
- [ ] `go vet ./...` 无警告
- [ ] 代码审查通过

## Work Log

- 2026-03-04: code-review 发现，由 code-simplicity-reviewer 和 performance-oracle 代理报告

## Resources

- 相关代码: `backend/handlers/photo.go`
