---
status: pending
priority: p2
issue_id: "038"
tags: [code-review, performance, ux]
---

# AdminDeleteDevice 串行逐一调用 COS 删除，导致 HTTP 响应超时

## Problem Statement

删除含大量照片的设备时，handler 在返回 HTTP 响应前串行调用每张照片的 COS Delete API。1000 张照片 × 50ms/次 = 约 50 秒，超过所有常见 HTTP 超时设置。

## Findings

- **File:** `backend/handlers/admin.go:105-110`
```go
for _, p := range photos {
    if err := cos.Delete(c.Request.Context(), p.CosKey); err != nil {
        log.Printf("COS 清理失败 key=%s: %v", p.CosKey, err)
    }
}
```
- 全量 `models.Photo` struct 被加载进内存（仅需 `CosKey`）
- COS 清理已是 best-effort（失败只打日志），无需阻塞响应

## Proposed Solutions

### Option A: 响应后后台 goroutine + 并发限制（推荐）
```go
// 仅查 cos_key
var cosKeys []string
db.Model(&models.Photo{}).Where("device_id = ?", deviceID).Pluck("cos_key", &cosKeys)

// ... 事务删除 DB ...

c.JSON(http.StatusOK, gin.H{"message": "设备已删除"})

// 后台并发清理 COS
go func() {
    const workers = 10
    sem := make(chan struct{}, workers)
    var wg sync.WaitGroup
    for _, key := range cosKeys {
        sem <- struct{}{}
        wg.Add(1)
        go func(k string) {
            defer func() { <-sem; wg.Done() }()
            _ = cos.Delete(context.Background(), k)
        }(key)
    }
    wg.Wait()
}()
```

## Acceptance Criteria

- [ ] HTTP 响应在 DB 操作完成后立即返回，不等待 COS 清理
- [ ] COS 清理在后台 goroutine 中并发执行（限并发数 ≤ 10）
- [ ] 使用 `Pluck("cos_key", &cosKeys)` 代替全量 Find

## Work Log

- 2026-03-11: Found by performance-oracle agent
