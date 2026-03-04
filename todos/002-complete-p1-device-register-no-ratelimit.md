---
status: pending
priority: p1
issue_id: "002"
tags: [code-review, security, dos, rate-limiting]
dependencies: []
---

# P1: 设备注册接口无频率限制（DoS / 资源耗尽攻击）

## Problem Statement

`POST /api/device/register` 是公开接口（无需认证），每次调用都会在数据库中插入一条新的 Device 记录。攻击者可以无限制地调用该接口，导致：
1. MySQL `devices` 表快速膨胀，磁盘耗尽
2. 数据库连接池被耗尽，影响正常用户
3. 相关查询性能下降
4. 服务器 CPU/内存压力上升

## Findings

**受影响文件:** `backend/handlers/device.go`, `backend/main.go`

```go
// main.go: 完全公开，无任何限制
r.POST("/api/device/register", handlers.DeviceRegister(db))

// device.go: 每次调用无条件插入新记录
func DeviceRegister(db *gorm.DB) gin.HandlerFunc {
    return func(c *gin.Context) {
        device := models.Device{
            ID:      uuid.New().String(),
            Name:    "我的相框",
            QrToken: uuid.New().String(),
        }
        if err := db.Create(&device).Error; err != nil { ... }
        // 无任何频率检查
    }
}
```

## Proposed Solutions

### 方案 A（推荐）：IP 速率限制中间件
使用 `golang.org/x/time/rate` 或 `github.com/ulule/limiter` 对该接口添加 IP 级别限流（如每 IP 每分钟最多 3 次）：
```go
// 使用 gin 的 limiter 中间件
deviceAPI := r.Group("/api/device")
deviceAPI.Use(middleware.RateLimit(3, time.Minute)) // 3次/分钟/IP
deviceAPI.POST("/register", handlers.DeviceRegister(db))
```
- 优点：简单有效，不影响正常用户
- 缺点：需要额外依赖或自行实现
- 风险：低

### 方案 B：注册 Token 机制
设备注册需要携带预分发的注册码（如打包在 APK 中的 HMAC 签名），服务端验证合法性：
```go
// 请求体包含 registration_code
// 服务端用共享密钥验证 HMAC
```
- 优点：根本性解决，只有合法 APK 才能注册
- 缺点：密钥硬编码在 APK 中仍可被提取，实现复杂
- 风险：中等

### 方案 C：仅在 Nginx 层限流
在 nginx.conf 中使用 `limit_req_zone` 对 `/api/device/register` 限流：
```nginx
limit_req_zone $binary_remote_addr zone=device_reg:10m rate=3r/m;
location /api/device/register {
    limit_req zone=device_reg burst=2 nodelay;
    ...
}
```
- 优点：无需修改 Go 代码
- 缺点：只在 Nginx 前置的场景有效，直连 Go 服务时无保护
- 风险：低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `backend/main.go` - 路由注册
- `backend/handlers/device.go` - DeviceRegister 函数
- `nginx.conf` - 可选的 Nginx 层限流

**数据库影响:** `devices` 表和 `device_users` 表无限增长

## Acceptance Criteria

- [ ] `/api/device/register` 单 IP 每分钟调用次数不超过设定阈值（推荐 ≤5次）
- [ ] 超限时返回 HTTP 429 Too Many Requests
- [ ] 正常 Android 设备注册流程不受影响（首次启动只注册一次）
- [ ] 添加测试验证限流行为

## Work Log

- 2026-03-04: code-review 发现，由 security-sentinel 和 architecture-strategist 代理报告

## Resources

- 相关代码: `backend/handlers/device.go:13-28`
- Gin Rate Limiting: https://github.com/gin-contrib/limiter
