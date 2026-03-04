---
status: pending
priority: p3
issue_id: "015"
tags: [code-review, miniprogram, typescript, performance]
dependencies: []
---

# P3: 小程序端多项代码质量改进

## Problem Statement

微信小程序端（uni-app Vue3）存在多个影响用户体验和代码质量的问题：

1. **上传为串行**：`upload/index.vue` 对多张照片串行上传，选 9 张时总时间是 9 张各自时间之和，可改为有限并发提升上传速度
2. **文件大小获取失败时返回 0**：`getFileSize` 失败回调 `resolve(0)`，导致大文件可能跳过压缩步骤直接上传
3. **压缩阈值过高**：8MB 压缩阈值对手机照片偏高，主流手机照片 3-5MB 就会失真，建议降至 2MB
4. **manage 页面图片未添加懒加载**：照片列表在照片量大时会同时加载所有图片，影响内存

## Findings

### 问题 1: 串行上传
**文件:** `miniprogram/src/pages/upload/index.vue`
```javascript
// 串行上传（约第51行）
for (const item of pendingItems) {
    item.status = 'uploading'
    await uploadPhoto(filePath, deviceId.value, ...)  // 一张一张等
    item.status = 'done'
}
```

### 问题 2: getFileSize 失败处理
**文件:** `miniprogram/src/pages/upload/index.vue`
```javascript
function getFileSize(path: string): Promise<number> {
  return new Promise(resolve => {
    const fs = uni.getFileSystemManager()
    fs.getFileInfo({
      filePath: path,
      success: (i) => resolve(i.size),
      fail: () => resolve(0)  // 失败时返回0，会导致跳过压缩
    })
  })
}
```

### 问题 3: 压缩阈值
```javascript
if (fileInfo > 8 * 1024 * 1024) {  // 8MB 阈值过高
    const compressed = await compressImage(item.path)
```

### 问题 4: 缺少懒加载
**文件:** `miniprogram/src/pages/manage/index.vue`
```html
<image :src="photo.url" mode="aspectFill" class="photo-thumb" />
<!-- 缺少 lazy-load 属性 -->
```

## Proposed Solutions

### 问题 1 修复：有限并发上传
```javascript
async function startUpload() {
    const pendingItems = items.value.filter(i => i.status === 'pending')
    const CONCURRENCY = 3  // 最多3张并发

    // 分批并发上传
    for (let i = 0; i < pendingItems.length; i += CONCURRENCY) {
        const batch = pendingItems.slice(i, i + CONCURRENCY)
        await Promise.all(batch.map(item => uploadItem(item)))
    }
}
```

### 问题 2 修复：失败时抛错而非返回0
```javascript
fail: (err) => reject(new Error('获取文件大小失败'))
// 调用方 catch 后跳过该文件或使用默认行为（假设大于阈值，始终压缩）
```

### 问题 3 修复
```javascript
if (fileInfo > 2 * 1024 * 1024) {  // 2MB 阈值
```

### 问题 4 修复
```html
<image :src="photo.url" mode="aspectFill" class="photo-thumb" lazy-load />
```

## Technical Details

**受影响文件:**
- `miniprogram/src/pages/upload/index.vue`
- `miniprogram/src/pages/manage/index.vue`

## Acceptance Criteria

- [ ] 多张照片上传时使用并发（≤3个并发），总时间显著减少
- [ ] `getFileSize` 失败时不静默返回0
- [ ] 压缩阈值改为 2MB
- [ ] manage 页面图片列表添加 `lazy-load` 属性

## Work Log

- 2026-03-04: code-review 发现，由 code-simplicity-reviewer 和 performance-oracle 代理报告
