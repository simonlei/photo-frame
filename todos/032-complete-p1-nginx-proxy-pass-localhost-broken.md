---
status: pending
priority: p1
issue_id: "032"
tags: [code-review, architecture, deployment, docker]
---

# nginx proxy_pass 指向 127.0.0.1 在容器化部署中不可用

## Problem Statement

`nginx.conf` 中 `proxy_pass http://127.0.0.1:8080` 在 nginx 容器内无法到达 backend 容器。`127.0.0.1` 是 nginx 容器自身的 loopback，而不是 backend 服务，导致所有 `/api/` 请求返回 502 Bad Gateway。整个 API（包括现有用户端接口）在容器化部署下完全不可用。

## Findings

- **File:** `nginx.conf:19` — `proxy_pass http://127.0.0.1:8080;`
- **File:** `docker-compose.yml` — nginx 和 backend 都在 `internal` bridge 网络中，应通过服务名 `backend` 互相访问
- 此 PR 之前 nginx 运行在宿主机上，`127.0.0.1:8080` 指向宿主机上 backend 的 published port，是正确的。容器化 nginx 后没有同步修改 `proxy_pass`。

## Proposed Solutions

### Option A: 修改 proxy_pass 为服务名（推荐）
```nginx
proxy_pass http://backend:8080;
```
同时从 backend service 中移除 `ports: "8080:8080"` 绑定（仅保留 `networks: - internal`）。
- **优点：** 标准 Docker 网络实践；关闭 8080 公开暴露；一行改动
- **风险：** 低

### Option B: 保持宿主机 nginx（回退）
不引入 docker-compose nginx service，继续用宿主机上的 nginx，`127.0.0.1:8080` 仍然有效。
- **优点：** 无需改动 nginx.conf
- **缺点：** 破坏了此 PR 的 docker-compose 集成目标

## Recommended Action

Option A

## Technical Details

- **Affected files:** `nginx.conf:19`, `docker-compose.yml:33-34`
- **Impact:** 100% API failure in containerized deployment

## Acceptance Criteria

- [ ] `proxy_pass` 改为 `http://backend:8080`
- [ ] backend service 的 `ports: "8080:8080"` 移除
- [ ] `docker-compose up` 后 `curl http://localhost/api/version/latest` 返回正常响应

## Work Log

- 2026-03-11: Found by architecture-strategist review agent
