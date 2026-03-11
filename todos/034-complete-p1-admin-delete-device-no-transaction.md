---
status: pending
priority: p1
issue_id: "034"
tags: [code-review, architecture, data-integrity, database]
---

# AdminDeleteDevice 删除操作无事务保护，可导致数据不一致

## Problem Statement

`AdminDeleteDevice` 中三个删除操作（device_users、photos、device）分别独立执行，无 DB 事务包裹。若第三步 `db.Delete(&device)` 失败，前两步已不可逆地删除了关联数据，设备记录残留但无用户无照片，数据库进入不一致状态。

## Findings

- **File:** `backend/handlers/admin.go:94-101`
```go
db.Exec("DELETE FROM device_users WHERE device_id = ?", deviceID)  // 错误被忽略
db.Where("device_id = ?", deviceID).Delete(&models.Photo{})         // 错误被忽略
if err := db.Delete(&device).Error; err != nil {                      // 只有最后一步被检查
    c.JSON(http.StatusInternalServerError, ...)
    return
}
```
- 中间两步的错误返回值被静默丢弃
- 使用了混合风格：原生 SQL 和 GORM ORM

## Proposed Solutions

### Option A: 使用 db.Transaction 包裹全部删除（推荐）
```go
cosKeysToDelete := make([]string, 0)
err := db.Transaction(func(tx *gorm.DB) error {
    // 先查出 cos keys
    var photos []models.Photo
    if err := tx.Where("device_id = ?", deviceID).Find(&photos).Error; err != nil {
        return err
    }
    for _, p := range photos {
        cosKeysToDelete = append(cosKeysToDelete, p.CosKey)
    }
    if err := tx.Exec("DELETE FROM device_users WHERE device_id = ?", deviceID).Error; err != nil {
        return err
    }
    if err := tx.Where("device_id = ?", deviceID).Delete(&models.Photo{}).Error; err != nil {
        return err
    }
    return tx.Delete(&device).Error
})
if err != nil {
    c.JSON(http.StatusInternalServerError, gin.H{"error": "删除设备失败"})
    return
}
// 事务成功后才清理 COS
for _, key := range cosKeysToDelete {
    ...
}
```

## Recommended Action

Option A

## Acceptance Criteria

- [ ] 三个 DB 删除操作包裹在同一个 `db.Transaction` 中
- [ ] 事务中每步错误均被检查和返回
- [ ] COS 清理在事务提交成功后才执行

## Work Log

- 2026-03-11: Found by architecture-strategist and security-sentinel review agents
