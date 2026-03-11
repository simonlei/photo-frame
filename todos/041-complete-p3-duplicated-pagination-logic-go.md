---
status: pending
priority: p3
issue_id: "041"
tags: [code-review, performance, go, refactor]
---

# AdminListDevicePhotos 和 AdminListPhotos 分页逻辑重复

## Problem Statement

两个 handler 中 8 行分页解析代码完全一致（`strconv.Atoi` 两次 + 两次 clamp + offset 计算），可提取为辅助函数消除重复。

## Findings

- **File:** `backend/handlers/admin.go:120-128` 和 `backend/handlers/admin.go:199-207`

## Proposed Solutions

### Option A: 提取 parsePage 辅助函数
```go
func parsePage(c *gin.Context) (page, pageSize, offset int) {
    page, _ = strconv.Atoi(c.DefaultQuery("page", "1"))
    pageSize, _ = strconv.Atoi(c.DefaultQuery("page_size", "50"))
    if page < 1 { page = 1 }
    if pageSize < 1 || pageSize > 200 { pageSize = 50 }
    offset = (page - 1) * pageSize
    return
}
```
每个 handler 改为 `page, pageSize, offset := parsePage(c)`。净减少约 8 行。

## Acceptance Criteria

- [ ] 提取 `parsePage` 函数
- [ ] 两处调用点使用新函数
- [ ] 行为与原代码完全一致

## Work Log

- 2026-03-11: Found by code-simplicity-reviewer agent
