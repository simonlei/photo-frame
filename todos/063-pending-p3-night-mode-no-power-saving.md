---
status: pending
priority: p3
issue_id: "063"
tags:
  - code-review
  - performance
  - android
dependencies: []
---

# 夜间模式下 autoSlide/PhotoSync 仍全速运行，浪费电量

## Problem Statement

屏幕亮度降到 0.01f 的夜间模式下，autoSlideRunnable（每 15s）和 PhotoSyncService（每 60s 含网络请求）仍以正常频率运行。屏幕几乎全黑时翻页和照片同步毫无意义。

## Proposed Solutions

夜间模式下暂停 autoSlideRunnable 和降低 PhotoSyncService 频率（如 5 分钟一次）。
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 夜间模式下 autoSlide 暂停
- [ ] 夜间模式下 sync 频率降低

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Performance agent flagged |
