---
status: pending
priority: p2
issue_id: "055"
tags:
  - code-review
  - quality
  - android
  - testing
  - flaky
dependencies: []
---

# E2E 测试中 4 处 Thread.sleep 导致测试慢且不稳定

## Problem Statement

4 个 E2E 测试使用 `Thread.sleep()` 等待异步操作，总计 ~13 秒硬等待。代码中已有 TODO 注释承认问题但未解决。这在 CI 中造成不稳定和浪费。

## Findings

1. **BindFlowTest.kt:48** — `Thread.sleep(5_000)` 等待轮询完成
2. **BindFlowTest.kt:59** — `Thread.sleep(1_000)` 等待已绑定跳转
3. **SlideshowFlowTest.kt:52** — `Thread.sleep(5_000)` 等待自动翻页
4. **ErrorFlowTest.kt:40** — `Thread.sleep(2_000)` 等待网络请求

## Proposed Solutions

### Option A: 实现 IdlingResource（推荐）
- 为 OkHttp 请求注册 `OkHttp3IdlingResource`
- 为协程状态注册自定义 `IdlingResource`
- **Effort:** Medium | **Risk:** Low

### Option B: 使用 ConditionWatcher 模式
- 轮询检查条件而非固定等待
- **Effort:** Small | **Risk:** Medium (仍可能 flaky)

## Acceptance Criteria

- [ ] 零 `Thread.sleep` 调用
- [ ] E2E 测试总时间 < 30 秒
- [ ] 连续 10 次运行无 flaky 失败

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | All 4 agents flagged this issue |
