---
status: pending
priority: p3
issue_id: "014"
tags: [code-review, backend, go, logging, performance]
dependencies: []
---

# P3: 后端生产环境日志和性能优化

## Problem Statement

后端存在多项在生产环境运行时应优化的配置：

1. **gin.Default() 在生产环境输出 debug 日志**：`gin.Default()` 等同于 `gin.New() + Logger + Recovery`，Logger 中间件会将每个请求的详细信息打印到 stdout，在高流量场景下显著增加 I/O 开销
2. **GORM 未配置日志级别**：默认 GORM 日志会打印所有 SQL 语句，生产环境应只打印 Warn 及以上
3. **上传接口 32MB 全量内存缓冲**：`r.MaxMultipartMemory = 32 << 20` 意味着最大 32MB 的文件会先完整加载到内存，再上传 COS。高并发场景下内存压力大
4. **photos 表缺少复合索引**：`(device_id, uploaded_at)` 是 ListPhotos 查询的核心条件，但只有 `device_id` 单独的索引（如有），缺少覆盖 `uploaded_at` 排序的复合索引

## Findings

### 问题 1 & 2: 日志配置
**文件:** `backend/main.go`, `backend/database/mysql.go`

```go
// main.go
r := gin.Default()  // 生产环境日志过于详细

// mysql.go: GORM 使用默认日志配置（打印所有 SQL）
db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
```

### 问题 3: 内存缓冲
**文件:** `backend/main.go`
```go
r.MaxMultipartMemory = 32 << 20  // 32MB 全量内存
```
家庭照片典型大小 3-8MB，在多用户同时上传时，每个请求占用 32MB 内存上限会导致 OOM。

### 问题 4: 缺少复合索引
**文件:** `backend/models/photo.go`
```go
type Photo struct {
    DeviceID   string `gorm:"index"`  // 单列索引
    UploadedAt time.Time              // 排序字段，未包含在索引中
    // 缺少: gorm:"index:idx_device_uploaded,composite:1"
}
```

## Proposed Solutions

### 问题 1 & 2 修复
```go
// main.go: 生产环境使用精简日志
if os.Getenv("APP_ENV") == "production" {
    gin.SetMode(gin.ReleaseMode)
}
r := gin.New()
r.Use(gin.Recovery())
if os.Getenv("APP_ENV") != "production" {
    r.Use(gin.Logger())
}

// mysql.go: 配置 GORM 日志级别
logLevel := logger.Info
if os.Getenv("APP_ENV") == "production" {
    logLevel = logger.Warn
}
db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{
    Logger: logger.Default.LogMode(logLevel),
})
```

### 问题 3 修复
将 `MaxMultipartMemory` 从 32MB 降至 8MB，配合流式上传或压缩处理：
```go
r.MaxMultipartMemory = 8 << 20  // 8MB，与小程序压缩阈值一致
```

### 问题 4 修复
```go
type Photo struct {
    DeviceID   string    `gorm:"index:idx_device_uploaded,priority:1"`
    UploadedAt time.Time `gorm:"index:idx_device_uploaded,priority:2"`
    // 复合索引 (device_id, uploaded_at)
}
```

## Technical Details

**受影响文件:**
- `backend/main.go`
- `backend/database/mysql.go`
- `backend/models/photo.go`

**需要数据库迁移:** 添加复合索引 `idx_device_uploaded(device_id, uploaded_at)`

## Acceptance Criteria

- [ ] 生产环境（`APP_ENV=production`）不输出每个请求的 access log
- [ ] GORM 在生产环境只记录 Warn 及以上日志
- [ ] `MaxMultipartMemory` 降至合理值（≤8MB）
- [ ] `photos` 表存在 `(device_id, uploaded_at)` 复合索引
- [ ] 开发环境保留完整日志输出

## Work Log

- 2026-03-04: code-review 发现，由 performance-oracle 代理报告
