---
status: pending
priority: p3
issue_id: "062"
tags:
  - code-review
  - quality
  - android
dependencies: []
---

# 三个 ViewModelFactory 类是纯样板代码，可用 viewModelFactory DSL 消除

## Problem Statement

三个 Factory 共 41 行结构完全一致的代码。Lifecycle 2.7.0 已支持 `viewModelFactory` DSL，可以内联创建。

## Proposed Solutions

使用 `viewModelFactory { initializer { ... } }` DSL 内联到 Activity 中，删除三个 Factory 文件。
- **Effort:** Small | **Risk:** Low

## Acceptance Criteria

- [ ] 删除三个 Factory 文件
- [ ] Activity 中使用 `viewModels<T> { viewModelFactory { ... } }` 模式

## Work Log

| Date | Action | Learnings |
|------|--------|-----------|
| 2026-03-19 | Created from code review | Simplicity + Architecture agents flagged |
