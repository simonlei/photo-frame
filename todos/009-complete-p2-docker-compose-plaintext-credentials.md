---
status: pending
priority: p2
issue_id: "009"
tags: [code-review, security, docker, credentials]
dependencies: []
---

# P2: docker-compose.yml 明文密码 + MySQL 端口暴露

## Problem Statement

`docker-compose.yml` 存在两个安全问题：
1. MySQL 密码明文写在 `docker-compose.yml` 中（`MYSQL_ROOT_PASSWORD: rootpassword`），该文件很可能被提交到 Git 仓库，导致密码泄露
2. MySQL 端口 3306 被映射到宿主机（`ports: "3306:3306"`），使数据库直接暴露在公网，存在被扫描攻击的风险

## Findings

**受影响文件:** `docker-compose.yml`

```yaml
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword  # 明文密码
      MYSQL_DATABASE: photoframe
      MYSQL_USER: photoframe
      MYSQL_PASSWORD: photoframe123  # 明文密码
    ports:
      - "3306:3306"  # 数据库端口暴露到公网
```

## Proposed Solutions

### 方案 A（推荐）：迁移到 .env 文件 + 移除数据库端口映射
```yaml
# docker-compose.yml
services:
  db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    # 移除 ports 配置，数据库只在 Docker 内部网络访问
    networks:
      - internal

  backend:
    networks:
      - internal

networks:
  internal:
    driver: bridge
```

`.env` 文件（已在 `.gitignore` 中）保存实际密码，`docker-compose.yml` 只引用变量。

- 优点：密码不进入版本控制，数据库不暴露公网
- 缺点：本地调试时需要手动维护 .env 文件
- 风险：低

### 方案 B：使用 Docker Secrets（生产级方案）
Docker Swarm 模式下使用 secrets：
```yaml
secrets:
  db_password:
    file: ./secrets/db_password.txt
```
- 优点：更安全，密码不以环境变量形式存在内存中
- 缺点：需要 Docker Swarm，对简单单机部署过于复杂
- 风险：中等（引入复杂度）

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `docker-compose.yml`
- `.env.example` - 需更新示例（已存在的文件）

**同时需要更新:** `.gitignore` 确保 `.env` 不被提交（应已包含）

## Acceptance Criteria

- [ ] `docker-compose.yml` 中无任何明文密码
- [ ] MySQL 端口 3306 不再映射到宿主机
- [ ] `.env.example` 包含所有需要配置的数据库变量
- [ ] `docker-compose up` 使用 .env 文件正常启动

## Work Log

- 2026-03-04: code-review 发现，由 security-sentinel 代理报告
