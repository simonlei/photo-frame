---
title: "refactor: 版本检查改为后端代理 GitHub Releases API"
type: refactor
status: completed
date: 2026-03-11
origin: docs/brainstorms/2026-03-11-version-management-brainstorm.md
---

# refactor: 版本检查改为后端代理 GitHub Releases API

## Overview

将 GitHub Actions 直写数据库的方案改为后端实时代理 GitHub Releases API，彻底解决 CI 网络不通导致写库失败的问题，同时简化整体架构。

## Problem Statement

GitHub Actions 构建 APK 后，尝试通过 `mysql` 命令直接写入生产数据库的 `versions` 表：

```yaml
# .github/workflows/android-release.yml:64-84
- name: Register version in database
  run: |
    mysql -h "$DB_HOST" ... <<SQL
      INSERT INTO versions (version, apk_url, apk_sha256, changelog, created_at)
      VALUES (...);
    SQL
```

**根本原因**：GitHub Actions 运行在 GitHub 托管的 runner 上，网络无法连通部署在内网/私有网络的 MySQL 数据库。此步骤每次必然失败。

## Proposed Solution

（见 brainstorm: docs/brainstorms/2026-03-11-version-management-brainstorm.md）

后端 `/api/version/latest` 接口改为实时请求 GitHub Releases API，不再查数据库：

```
Android 客户端
    ↓ GET /api/version/latest  (不变)
后端 Go 服务
    ↓ GET https://api.github.com/repos/simonlei/photo-frame/releases/latest
GitHub API
```

## Key Decisions（来自 brainstorm）

- **去掉 SHA256 校验**：`apk_sha256` 字段返回 `null`，Android 端 `if (expectedSha256 != null)` 已有兼容处理，直接跳过校验
- **后端代理而非 Android 直调**：Android 端 `ApiClient.service.latestVersion()` 调用无需改动
- **不引入数据库缓存**：版本信息实时从 GitHub 获取，可选 5 分钟内存缓存降低 GitHub API 调用频率
- **移除 GitHub Actions 写库步骤**：只保留构建和上传 APK

## Acceptance Criteria

- [x] Android 客户端发起版本检查请求，后端正确返回 GitHub 最新 Release 的版本号、APK 下载 URL、changelog
- [x] `apk_sha256` 字段返回 `null`，Android 跳过校验直接安装
- [x] GitHub Actions 不再包含数据库连接步骤，工作流正常完成
- [x] `versions` 数据库表和相关代码清理干净，不残留死代码
- [x] GitHub API 请求失败时，接口返回合理错误（如 `{"version": null}`），客户端静默跳过更新

## Implementation Plan

### Step 1：重写 `backend/handlers/version.go`

**改动**：删除 GORM 查数据库逻辑，改为调用 GitHub API。

```go
// backend/handlers/version.go（重写后）
package handlers

import (
    "encoding/json"
    "net/http"
    "time"

    "github.com/gin-gonic/gin"
)

const githubReleaseURL = "https://api.github.com/repos/simonlei/photo-frame/releases/latest"

var (
    cachedRelease    *githubRelease
    cacheExpireAt    time.Time
    cacheTTL         = 5 * time.Minute
)

type githubRelease struct {
    TagName string         `json:"tag_name"`
    Body    string         `json:"body"`
    Assets  []githubAsset  `json:"assets"`
}

type githubAsset struct {
    Name               string `json:"name"`
    BrowserDownloadURL string `json:"browser_download_url"`
}

func VersionLatest() gin.HandlerFunc {
    return func(c *gin.Context) {
        rel, err := fetchLatestRelease()
        if err != nil || rel == nil {
            c.JSON(http.StatusOK, gin.H{"version": nil})
            return
        }
        version := rel.TagName
        if len(version) > 0 && version[0] == 'v' {
            version = version[1:] // 去掉前缀 "v"
        }
        apkURL := ""
        for _, a := range rel.Assets {
            if a.Name == "app-release.apk" {
                apkURL = a.BrowserDownloadURL
                break
            }
        }
        c.JSON(http.StatusOK, gin.H{
            "version":    version,
            "apk_url":    apkURL,
            "apk_sha256": nil,
            "changelog":  rel.Body,
        })
    }
}

func fetchLatestRelease() (*githubRelease, error) {
    if cachedRelease != nil && time.Now().Before(cacheExpireAt) {
        return cachedRelease, nil
    }
    req, _ := http.NewRequest("GET", githubReleaseURL, nil)
    req.Header.Set("Accept", "application/vnd.github+json")
    req.Header.Set("User-Agent", "photo-frame-backend")
    resp, err := http.DefaultClient.Do(req)
    if err != nil {
        return cachedRelease, err // 失败时返回过期缓存兜底
    }
    defer resp.Body.Close()
    if resp.StatusCode != 200 {
        return cachedRelease, nil
    }
    var rel githubRelease
    if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
        return nil, err
    }
    cachedRelease = &rel
    cacheExpireAt = time.Now().Add(cacheTTL)
    return cachedRelease, nil
}
```

**注意**：5 分钟内存缓存，避免每次客户端心跳都打 GitHub API（GitHub 未认证 Rate Limit 60 次/小时）。缓存失效时返回旧缓存作为兜底，防止 GitHub 临时抖动影响用户更新检查。

### Step 2：更新 `backend/main.go`

**改动**：`VersionLatest` 不再需要 `db` 参数。

```go
// 修改前
r.GET("/api/version/latest", handlers.VersionLatest(db))

// 修改后
r.GET("/api/version/latest", handlers.VersionLatest())
```

### Step 3：清理 `backend/models/photo.go`

**改动**：删除 `Version` struct（第 18-25 行）。

```go
// 删除这段
type Version struct {
    ID        uint      `gorm:"primaryKey;autoIncrement" json:"id"`
    Version   string    `gorm:"type:varchar(20);not null" json:"version"`
    ApkURL    string    `gorm:"type:varchar(1024);not null" json:"apk_url"`
    ApkSha256 string    `gorm:"type:varchar(64)" json:"apk_sha256"`
    Changelog string    `gorm:"type:text" json:"changelog"`
    CreatedAt time.Time `json:"created_at"`
}
```

### Step 4：清理 `backend/database/mysql.go`

**改动**：从 `AutoMigrate` 列表中移除 `&models.Version{}`（第 50 行）。

```go
// 修改前
db.AutoMigrate(
    &models.Device{},
    &models.User{},
    &models.Photo{},
    &models.Version{},  // ← 删除此行
)

// 修改后
db.AutoMigrate(
    &models.Device{},
    &models.User{},
    &models.Photo{},
)
```

### Step 5：更新 `.github/workflows/android-release.yml`

**改动**：删除"Register version in database"步骤（第 64-84 行）以及"Compute SHA-256"步骤（第 57-62 行，此后无用）。

删除以下两个步骤：

```yaml
# 删除：Compute SHA-256（第 57-62 行）
- name: Compute SHA-256
  id: sha256
  run: |
    HASH=$(sha256sum android/app/build/outputs/apk/release/app-release.apk | cut -d' ' -f1)
    echo "hash=$HASH" >> "$GITHUB_OUTPUT"

# 删除：Register version in database（第 64-84 行）
- name: Register version in database
  env:
    DB_HOST: ...
  run: |
    mysql ...
```

同时可以在仓库 Settings → Secrets 中删除 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`APK_BASE_URL` 这 6 个不再使用的 Secrets（可选，不影响构建）。

### Step 6（可选）：数据库清理

生产环境 `versions` 表不会被自动删除（代码中已无 `AutoMigrate`），如需清理手动执行：

```sql
DROP TABLE IF EXISTS versions;
```

## Technical Considerations

### GitHub API Rate Limit

未认证请求限额 60 次/小时（按 IP）。后端部署在固定服务器 IP，5 分钟缓存下每小时最多 12 次请求，远低于限制。如未来并发量大，可配置 `GITHUB_TOKEN` 环境变量提升到 5000 次/小时。

### 私有仓库

当前仓库为公开仓库（`git@github.com:simonlei/photo-frame.git`），GitHub Releases API 无需认证即可访问。

### Android 客户端兼容性

`AutoUpdater.kt:93-108`：`verifyAndInstall` 中已有 `if (expectedSha256 != null)` 守卫，`null` 时直接调用 `installApk`，无需改动 Android 代码。

## Files Changed

| 文件 | 操作 |
|------|------|
| `backend/handlers/version.go` | 重写：删除 GORM 逻辑，改为代理 GitHub API |
| `backend/main.go` | 修改：`VersionLatest(db)` → `VersionLatest()` |
| `backend/models/photo.go` | 删除：`Version` struct（第 18-25 行）|
| `backend/database/mysql.go` | 删除：`AutoMigrate` 中的 `&models.Version{}` |
| `.github/workflows/android-release.yml` | 删除：SHA-256 计算步骤和写库步骤 |

**Android / admin-frontend 无需改动。**

## Sources

- **Origin brainstorm:** [docs/brainstorms/2026-03-11-version-management-brainstorm.md](../brainstorms/2026-03-11-version-management-brainstorm.md) — 关键决策：去掉 SHA256、后端代理方案、Android 端不改动
- `backend/handlers/version.go` — 当前实现（查数据库）
- `backend/models/photo.go:18-25` — Version struct 定义
- `backend/database/mysql.go:46-53` — AutoMigrate 列表
- `.github/workflows/android-release.yml:57-84` — 待删除步骤
- `android/app/src/main/java/com/photoframe/updater/AutoUpdater.kt:93-108` — SHA256 null 兼容处理
- GitHub Releases API: `https://docs.github.com/en/rest/releases/releases#get-the-latest-release`
