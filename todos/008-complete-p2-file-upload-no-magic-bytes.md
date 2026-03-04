---
status: pending
priority: p2
issue_id: "008"
tags: [code-review, security, file-upload, validation]
dependencies: []
---

# P2: 文件上传缺少 Magic Bytes 校验（文件类型伪造）

## Problem Statement

`UploadPhoto` 接口仅依赖 HTTP Content-Type header 或文件扩展名判断文件类型，未对文件内容进行 Magic Bytes（文件头）校验。攻击者可以将 HTML、JavaScript、可执行文件等伪装成图片上传到 COS，然后通过 COS 直链访问，可能导致 XSS 或其他攻击。

## Findings

**受影响文件:** `backend/handlers/photo.go`

```go
// UploadPhoto: 只检查了文件是否存在，未验证文件内容类型
file, header, err := c.Request.FormFile("photo")
// 直接上传到 COS，无 Magic Bytes 检查
contentType := header.Header.Get("Content-Type")  // 客户端可随意伪造
```

**项目已有 mime 相关依赖可利用。**

## Proposed Solutions

### 方案 A（推荐）：读取文件头字节进行 Magic Bytes 校验
```go
// 读取前 512 字节用于检测
buf := make([]byte, 512)
n, _ := file.Read(buf)
contentType := http.DetectContentType(buf[:n])
if !strings.HasPrefix(contentType, "image/") {
    c.JSON(http.StatusBadRequest, gin.H{"error": "只允许上传图片文件"})
    return
}
// 重置文件读取位置
file.Seek(0, io.SeekStart)
```

使用 Go 标准库 `net/http.DetectContentType()`，无需额外依赖，检测 JPEG、PNG、GIF、WebP 等格式。

- 优点：简单有效，无额外依赖
- 缺点：`DetectContentType` 的检测能力有限（仅基于前512字节）
- 风险：低

### 方案 B：结合扩展名 + Magic Bytes 双重校验
同时校验文件扩展名和文件头，两者必须一致：
```go
allowedExts := map[string]string{
    ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
    ".png": "image/png", ".heic": "image/heic",
}
ext := strings.ToLower(filepath.Ext(header.Filename))
expectedType, ok := allowedExts[ext]
if !ok || contentType != expectedType {
    // 拒绝
}
```
- 优点：更严格的双重校验
- 缺点：HEIC 格式的 Magic Bytes 检测可能不准
- 风险：低

## Recommended Action

（待 triage 后填写）

## Technical Details

**受影响文件:**
- `backend/handlers/photo.go` - UploadPhoto 函数

## Acceptance Criteria

- [ ] 上传非图片文件时返回 400 Bad Request
- [ ] 合法的 JPEG、PNG 文件正常上传
- [ ] 修改扩展名的伪装文件被拒绝
- [ ] 添加相关测试用例

## Work Log

- 2026-03-04: code-review 发现，由 security-sentinel 代理报告
