---
status: pending
priority: p2
issue_id: "007"
tags: [code-review, security, authentication, token]
dependencies: []
---

# P2: 用户 Token 永不过期（认证安全风险）

## Problem Statement

`User` 模型中的 `token` 字段（UUID）在创建后永不过期，且没有刷新机制。一旦 token 泄露（日志记录、中间人攻击、设备丢失），攻击者可以永久访问受害者账号，上传/删除其照片、绑定新相框等。

此外，登录响应中直接返回了 `openid` 字段，openid 是微信平台分配的用户唯一标识，不应暴露给客户端（客户端只需要自定义 token）。

## Findings

**受影响文件:** `backend/models/user.go`, `backend/handlers/auth.go`

```go
// user.go: 无过期时间字段
type User struct {
    ID       uint   `gorm:"primaryKey"`
    Openid   string `gorm:"uniqueIndex"`
    Nickname string
    Token    string `gorm:"uniqueIndex"` // 永不过期
    CreatedAt time.Time
    // 缺少: TokenIssuedAt, TokenExpiresAt
}

// auth.go: 响应中暴露 openid
c.JSON(http.StatusOK, gin.H{
    "token":  user.Token,
    "openid": user.Openid,  // 不应返回
})
```

## Proposed Solutions

### 方案 A（推荐）：添加 token_issued_at，实现滑动过期
添加 `token_issued_at` 字段，在 middleware 中检查 token 年龄，超过阈值（如 30 天）则返回 401，强制重新登录：

```go
// models/user.go 添加字段
TokenIssuedAt time.Time

// middleware/auth.go 中增加检查
if time.Since(user.TokenIssuedAt) > 30*24*time.Hour {
    c.JSON(http.StatusUnauthorized, gin.H{"error": "token 已过期，请重新登录"})
    c.Abort()
    return
}
```

- 优点：实现简单，不破坏现有客户端逻辑（客户端只需处理 401 重新调用 wx-login）
- 缺点：需要数据库迁移
- 风险：低

### 方案 B：保持现状 + 移除 openid 暴露
至少先修复 openid 暴露问题（低风险修改）：
```go
c.JSON(http.StatusOK, gin.H{
    "token": user.Token,
    // 不返回 openid
})
```
- 优点：最小改动
- 缺点：token 仍然不过期
- 风险：极低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `backend/models/user.go`
- `backend/handlers/auth.go`
- `backend/middleware/auth.go`

**需要数据库迁移:** `users` 表增加 `token_issued_at DATETIME` 字段

## Acceptance Criteria

- [ ] 登录响应不包含 `openid` 字段
- [ ] Token 在发放后 N 天（可配置）过期，返回 401
- [ ] 小程序端收到 401 时自动触发重新登录流程
- [ ] 已有用户的 token 过期处理有兼容逻辑

## Work Log

- 2026-03-04: code-review 发现，由 security-sentinel 代理报告
