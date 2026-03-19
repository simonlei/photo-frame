---
status: ready
priority: p1
issue_id: "051"
tags:
  - code-review
  - security
  - android
  - known-pattern
dependencies: []
---

# Logcat Token 前缀泄露 — 新代码重复引入已知安全问题

## Problem Statement

本次 PR 新增的 `BindActivity.goMain()` 中使用 `prefs.userToken?.take(8)` 将 token 前 8 个字符输出到 logcat。这是一个 **已被文档记录且修复的已知安全问题**（参见 `docs/solutions/multi-category/android-qrcode-main-thread-token-leak-config-issues.md`），但在新代码中被重新引入。

## Findings

1. **BindActivity.kt:99** — `Log.d("BindActivity", "goMain() reinit ApiClient with token=${prefs.userToken?.take(8) ?: "null"}")`
   - 新增代码，直接使用 `take(8)` 暴露 token 前缀
   - 对于 JWT token，前 8 字符通常是 `eyJhbGci`（base64 header），虽信息量有限但违反安全最佳实践
   - 短 token 场景下可能泄露关键信息

2. **已有代码 ApiClient.kt:67,74** — `init()` 和 `reinit()` 也使用 `token.take(8)`，本次 PR 未修复

**Known Pattern:** `docs/solutions/multi-category/android-qrcode-main-thread-token-leak-config-issues.md` 已明确建议使用 `[PRESENT]` 替代 `take(8)`。

## Proposed Solutions

### Option A: 修复所有 token 日志（推荐）
- 将所有 `token.take(8)` 或 `token?.take(8)` 替换为 `if (token != null) "[PRESENT]" else "null"`
- **Pros:** 一次性解决所有已知 token 泄露点
- **Cons:** 调试时缺少 token 标识
- **Effort:** Small
- **Risk:** Low

### Option B: 仅修复新增代码
- 只修复 BindActivity 第 99 行
- **Pros:** 最小改动
- **Cons:** ApiClient 中的已知问题仍存在
- **Effort:** Small
- **Risk:** Low

## Recommended Action

(Filled during triage)

## Technical Details

**Affected Files:**
- `android/app/src/main/java/com/photoframe/BindActivity.kt` (line 99)
- `android/app/src/main/java/com/photoframe/data/ApiClient.kt` (lines 67, 74)

## Acceptance Criteria

- [ ] 所有 `Log.*` 调用中不包含 token 明文或前缀
- [ ] 使用 `[PRESENT]`/`null` 模式替代 `take(8)` 
- [ ] `adb logcat | grep -i token` 不输出任何敏感凭据内容

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Known pattern from docs/solutions/ |

## Resources

- PR: refactor/android-test-automation
- Related: `docs/solutions/multi-category/android-qrcode-main-thread-token-leak-config-issues.md`
