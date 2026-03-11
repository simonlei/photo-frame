---
status: pending
priority: p2
issue_id: "036"
tags: [code-review, security, infrastructure]
---

# Backend 端口 8080 直接暴露到公网，绕过 nginx 安全边界

## Problem Statement

`docker-compose.yml` 中 backend `ports: "8080:8080"` 将端口绑定到宿主机公网接口，使所有 admin API 端点可通过 `http://ip:8080/api/admin/...` 无 TLS 访问，完全绕过 nginx 层（包括未来可能添加的限速、IP 白名单等控制）。

## Findings

- **File:** `docker-compose.yml:33-34`
- MySQL 正确地没有发布端口，backend 应遵循同样规范
- 与 todo 032（nginx proxy_pass 问题）密切相关，两者需要同时修复

## Proposed Solutions

### Option A: 移除 backend ports 绑定（推荐）
```yaml
backend:
  # 删除:
  # ports:
  #   - "8080:8080"
  networks:
    - internal
```
同时将 `nginx.conf` 中 `proxy_pass` 改为 `http://backend:8080`（见 todo 032）。

## Acceptance Criteria

- [ ] backend service 移除 `ports` 绑定
- [ ] 宿主机 8080 端口不再对外开放
- [ ] 所有 API 流量通过 nginx（`proxy_pass http://backend:8080`）

## Work Log

- 2026-03-11: Found by security-sentinel and architecture-strategist agents
