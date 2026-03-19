---
status: pending
priority: p2
issue_id: "059"
tags:
  - code-review
  - testing
  - android
dependencies: []
---

# SlideshowFlowTest 使用 longClick 但 MainActivity 实际注册的是 singleTap

## Problem Statement

`SlideshowFlowTest.slideshow_longPressOpensSettings()` 使用 `perform(longClick())` 测试进入设置页，但 `MainActivity` 注册的手势是 `onSingleTapConfirmed`（单击），不是长按。测试与实际行为不匹配。

## Findings

- **SlideshowFlowTest.kt:66** — `onView(withId(R.id.view_pager)).perform(longClick())`
- **MainActivity.kt:81** — 注册的是 `onSingleTapConfirmed` 而非 `onLongPress`

## Proposed Solutions

### Option A: 修正测试使用 click()（推荐）
- 测试方法名改为 `slideshow_tapOpensSettings`
- 使用 `perform(click())` 替代 `perform(longClick())`
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 测试手势与代码手势一致
- [ ] 测试方法名准确描述行为

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Architecture agent flagged |
