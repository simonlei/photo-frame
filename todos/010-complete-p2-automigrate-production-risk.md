---
status: pending
priority: p2
issue_id: "010"
tags: [code-review, performance, database, migrations]
dependencies: []
---

# P2: 生产环境使用 AutoMigrate（数据库迁移风险）

## Problem Statement

`database/mysql.go` 中每次服务启动都调用 `db.AutoMigrate()` 进行数据库迁移。在开发环境这很方便，但在生产环境会带来：
1. **不可预期的 ALTER TABLE**：GORM AutoMigrate 只会"向前"迁移（添加列/索引），不会删除列，但执行顺序和锁等待不可控
2. **启动时间增加**：对大表执行 schema 变更可能导致服务启动超时
3. **无法回滚**：AutoMigrate 没有回滚机制，迁移失败时难以恢复
4. **并发启动问题**：多实例同时启动时并发执行 ALTER TABLE 可能导致冲突

## Findings

**受影响文件:** `backend/database/mysql.go`

```go
// mysql.go: 每次启动都执行 AutoMigrate
func Init() (*gorm.DB, error) {
    db, err := gorm.Open(mysql.Open(dsn), &gorm.Config{})
    ...
    db.AutoMigrate(
        &models.User{},
        &models.Device{},
        &models.Photo{},
        &models.Version{},
    )
    return db, nil
}
```

**同时发现:** 连接池配置缺少 `ConnMaxLifetime`，长连接可能在 MySQL 服务器关闭连接后仍被使用。

```go
sqlDB.SetMaxOpenConns(20)
sqlDB.SetMaxIdleConns(5)
// 缺少: sqlDB.SetConnMaxLifetime(time.Hour)
```

## Proposed Solutions

### 方案 A（推荐）：环境区分 + 补全连接池配置
开发环境保留 AutoMigrate，生产环境通过环境变量禁用，同时补全连接池配置：

```go
if os.Getenv("APP_ENV") != "production" {
    db.AutoMigrate(&models.User{}, &models.Device{}, &models.Photo{}, &models.Version{})
}

sqlDB.SetMaxOpenConns(20)
sqlDB.SetMaxIdleConns(5)
sqlDB.SetConnMaxLifetime(time.Hour)  // 补充缺失的配置
```

生产环境手动执行 SQL 迁移脚本。

- 优点：开发友好，生产安全，改动最小
- 缺点：需要维护手动迁移脚本
- 风险：低

### 方案 B：引入 golang-migrate 迁移框架
使用 `github.com/golang-migrate/migrate` 管理版本化迁移文件：
```
db/migrations/
├── 000001_create_users.up.sql
├── 000001_create_users.down.sql
├── 000002_create_devices.up.sql
...
```
- 优点：支持回滚，版本管理，生产级标准实践
- 缺点：引入额外依赖，需要编写并维护 SQL 文件
- 风险：低（但改动量中等）

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `backend/database/mysql.go`
- `backend/.env.example` - 添加 `APP_ENV` 配置说明

## Acceptance Criteria

- [ ] 生产环境（`APP_ENV=production`）启动时不执行 AutoMigrate
- [ ] 开发环境保留 AutoMigrate 便捷性
- [ ] 连接池配置补充 `ConnMaxLifetime`
- [ ] README 或部署文档说明生产环境的数据库迁移流程

## Work Log

- 2026-03-04: code-review 发现，由 performance-oracle 和 architecture-strategist 代理报告
