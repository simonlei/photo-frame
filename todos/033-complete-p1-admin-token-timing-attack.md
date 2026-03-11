---
status: pending
priority: p1
issue_id: "033"
tags: [code-review, security, authentication]
---

# Admin Token 使用 != 比较导致 Timing Attack 漏洞

## Problem Statement

`admin_auth.go` 中使用 Go 的 `!=` 运算符比较 token 字符串，该操作在第一个不匹配字节处短路，攻击者可通过测量 HTTP 响应时间统计地逐字节恢复 Admin Token（timing oracle 攻击）。

## Findings

- **File:** `backend/middleware/admin_auth.go:31`
```go
if parts[1] != adminToken {
```
- Admin Token 是静态不轮换的长期密钥，一旦泄露永久有效
- 时间侧信道攻击在局域网或同机房场景下尤其可行

## Proposed Solutions

### Option A: 使用 crypto/subtle.ConstantTimeCompare（推荐）
```go
import "crypto/subtle"

if subtle.ConstantTimeCompare([]byte(parts[1]), []byte(adminToken)) != 1 {
    c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "管理员 Token 无效"})
    return
}
```
- **优点：** 标准修复，一行改动，无副作用
- **风险：** 无

### Option B: 使用 hmac.Equal
```go
import "crypto/hmac"
if !hmac.Equal([]byte(parts[1]), []byte(adminToken)) {
```
- 同样 constant-time，语义更清晰

## Recommended Action

Option A

## Technical Details

- **Affected files:** `backend/middleware/admin_auth.go:31`
- **Severity:** 当 Admin Token 熵不足（如 `ADMIN_TOKEN=admin123`）时风险极高

## Acceptance Criteria

- [ ] 使用 `crypto/subtle.ConstantTimeCompare` 或 `hmac.Equal` 替换 `!=` 比较
- [ ] 添加 import `"crypto/subtle"`

## Work Log

- 2026-03-11: Found by security-sentinel review agent
