---
date: 2026-03-11
topic: version-management
---

# 版本管理重构：后端代理 GitHub Releases API

## What We're Building

将当前"GitHub Actions 写入数据库"的方案替换为"后端代理 GitHub Releases API"的方案。

**当前问题：** GitHub Actions 在 CI 网络环境中尝试用 `mysql` 命令直接写入生产数据库，由于网络不通，这一步总是失败。

**目标：** GitHub Actions 只负责构建 APK 并上传到 GitHub Release，版本信息从 GitHub Releases API 自动获取，不再需要手动写数据库。

## Why This Approach

考虑了以下方案后选择了后端代理：

- **方案 A（后台手工录入）**：需要管理员每次发版后手动操作，容易遗漏
- **方案 D（后端代理 GitHub API）**：✅ 选择此方案，完全自动化，Android 端无需改动
- **方案 F（Android 直调 GitHub API）**：需要改动 Android 代码，且与现有架构不符

## Key Decisions

- **去掉 SHA256 校验**：简化流程，内网设备风险可接受
- **后端代理而非 Android 直调**：Android 端 `ApiClient.service.latestVersion()` 无需改动
- **不再需要 `versions` 数据库表**：版本信息实时从 GitHub 获取，不做本地缓存（或只做短时缓存）
- **GitHub Actions 移除"Register version in database"步骤**：只保留构建和上传 APK

## Scope of Changes

### 后端（Go）
1. `backend/handlers/version.go`：重写 `VersionLatest`，改为调用 GitHub Releases API（`https://api.github.com/repos/{owner}/{repo}/releases/latest`），解析并返回 `version`、`apk_url`、`changelog`，`apk_sha256` 返回 `null`
2. `backend/main.go`：`/api/version/latest` 路由无需改动（不再传 `db` 参数）
3. 可选：增加短时缓存（如 5 分钟），避免频繁请求 GitHub API

### GitHub Actions
4. `.github/workflows/android-release.yml`：删除"Register version in database"步骤（第 64-84 行），删除相关 secrets（DB_HOST 等）

### 数据库
5. `versions` 表可以保留（不影响）或在后续清理迁移中删除

### Android
无需改动。

## Open Questions

（已无未解决问题）

## Resolved Questions

- **SHA256 是否保留**：去掉，简化流程
- **架构选择**：后端代理 GitHub API
- **URL 如何获取**：从 GitHub Releases API 的 `assets[].browser_download_url` 字段获取

## Next Steps

→ `/ce:plan` 查看具体实现步骤
