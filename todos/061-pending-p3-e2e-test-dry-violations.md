---
status: pending
priority: p3
issue_id: "061"
tags:
  - code-review
  - quality
  - testing
  - android
dependencies: []
---

# E2E 测试中多处重复代码：Intents.init/release、绑定状态设置、prefs 未清理

## Problem Statement

E2E 测试中存在多处代码重复和测试隔离问题：
1. 三个测试类重复 `Intents.init()/release()` 样板
2. "已绑定状态"初始化 3 行代码在 3 处重复
3. `MockServerTestBase.tearDown()` 不清理 SharedPreferences
4. Turbine 依赖已添加但未使用

## Proposed Solutions

1. 在 `MockServerTestBase` 中统一管理 `Intents`（通过 `useIntents` flag）
2. 提供 `simulateBoundDevice()` / `simulateUnboundDevice()` 辅助方法
3. `tearDown()` 中添加 prefs 清理
4. 删除未使用的 Turbine 依赖或在测试中使用它
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 无重复的 Intents.init/release 代码
- [ ] 绑定状态设置通过辅助方法完成
- [ ] tearDown 清理 prefs

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Simplicity agent flagged |
