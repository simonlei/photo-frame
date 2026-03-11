---
status: pending
priority: p2
issue_id: "040"
tags: [code-review, security, nginx]
---

# nginx /admin/ 缺少安全响应头且 try_files fallback 路径可能循环

## Problem Statement

1. `/admin/` location 无安全 HTTP 头（无 CSP、X-Frame-Options、X-Content-Type-Options），增加 XSS 和 Clickjacking 攻击面。
2. `try_files` fallback `/admin/index.html` 在 `alias` 指令下是绝对 URI 而非文件路径，可能触发内部重定向循环。

## Findings

- **File:** `nginx.conf:27-30`
```nginx
location /admin/ {
    alias /app/admin-frontend/dist/;
    try_files $uri $uri/ /admin/index.html;  # 问题：/admin/index.html 是 URI 而非文件路径
}
```

## Proposed Solutions

### Option A: 修复 try_files + 添加安全头（推荐）
```nginx
location /admin/ {
    alias /app/admin-frontend/dist/;
    try_files $uri $uri/ /app/admin-frontend/dist/index.html;  # 绝对文件路径

    add_header X-Frame-Options "DENY";
    add_header X-Content-Type-Options "nosniff";
    add_header Referrer-Policy "strict-origin-when-cross-origin";
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline';";
}
```

## Acceptance Criteria

- [ ] `try_files` fallback 改为绝对文件路径
- [ ] 添加 X-Frame-Options、X-Content-Type-Options、Referrer-Policy 头
- [ ] 验证 SPA 路由刷新不产生 404

## Work Log

- 2026-03-11: Found by security-sentinel and architecture-strategist agents
