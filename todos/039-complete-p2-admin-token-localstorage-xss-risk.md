---
status: pending
priority: p2
issue_id: "039"
tags: [code-review, security, frontend]
---

# Admin Token 存储在 localStorage，易被 XSS 窃取

## Problem Statement

`localStorage` 中的数据可被同源任意 JS 读取。若 React 组件、Ant Design 或由数据库值渲染的设备名称存在 XSS 漏洞，攻击者可静默窃取 Admin Token。Admin Token 无过期时间，泄露后永久有效。

## Findings

- **File:** `admin-frontend/src/lib/api.ts:8-9`
```typescript
export function saveToken(token: string) {
  localStorage.setItem('admin_token', token)
}
```
- Admin Token 控制破坏性操作（删除所有设备/照片）
- 标准建议：管理员级凭证使用 `httpOnly; Secure; SameSite=Strict` Cookie

## Proposed Solutions

### Option A: 服务端 httpOnly Cookie（推荐，需改后端）
后端新增 `POST /api/admin/login` 接口，验证 Token 后 Set-Cookie，前端不直接持有 Token。
- **优点：** JS 不可读；最安全
- **缺点：** 需要后端改动，部署稍复杂

### Option B: sessionStorage（简单改进）
将 `localStorage` 改为 `sessionStorage`，浏览器关闭后自动清除。
- **优点：** 一行改动，减少暴露窗口
- **缺点：** 仍然对 XSS 可见

### Option C: 保持现状 + 文档说明（最低成本）
在部署文档中明确要求 nginx Content-Security-Policy 头，并说明 Admin 页面仅限内网访问。

## Recommended Action

对于个人家用部署场景，Option C 可接受；如有更高安全要求选 Option A。

## Acceptance Criteria

- [ ] 选择并实现一个选项
- [ ] 更新部署文档说明 Admin 安全要求

## Work Log

- 2026-03-11: Found by security-sentinel agent
