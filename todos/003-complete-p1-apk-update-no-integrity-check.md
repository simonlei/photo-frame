---
status: pending
priority: p1
issue_id: "003"
tags: [code-review, security, android, supply-chain, apk-update]
dependencies: []
---

# P1: APK 自动更新无签名校验（供应链攻击风险）

## Problem Statement

`AutoUpdater.kt` 通过 `DownloadManager` 下载新版 APK 后，直接触发安装，未对下载文件进行任何完整性校验。如果：
1. 网络传输被中间人攻击（MITM）
2. 服务器 COS 存储被攻击者替换文件
3. API 响应被篡改（指向恶意 APK）

则设备会自动安装恶意应用，获取相框完整控制权。

## Findings

**受影响文件:** `android/app/src/main/java/com/simonlei/photoframe/updater/AutoUpdater.kt`

```kotlin
// AutoUpdater.kt: 下载完成后直接安装，无任何校验
private val downloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == pendingDownloadId) {
            installApk(context, downloadedFile!!)  // 直接安装，未校验
        }
    }
}

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
    // 无 SHA-256 验证
}
```

**后端版本接口（`backend/handlers/version.go`）返回的数据中无哈希字段：**
```go
// 返回: version, apk_url, changelog
// 缺少: apk_sha256
```

## Proposed Solutions

### 方案 A（推荐）：SHA-256 哈希校验
1. 后端版本表和接口增加 `apk_sha256` 字段
2. Android 下载完成后计算文件 SHA-256，与接口返回值对比，不一致则删除文件并告警

```kotlin
// 下载完成后校验
private fun verifyAndInstall(context: Context, file: File, expectedSha256: String) {
    val actualHash = calculateSha256(file)
    if (actualHash != expectedSha256) {
        file.delete()
        Log.e("AutoUpdater", "APK integrity check failed!")
        return
    }
    installApk(context, file)
}

private fun calculateSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
```

- 优点：简单有效，无需额外依赖，防止文件损坏和中间人替换
- 缺点：需要后端同步提供 SHA-256，且开发者需在发布时计算哈希
- 风险：低

### 方案 B：APK 签名校验
下载后解析 APK 证书，验证签名与当前已安装 APK 的签名一致：
```kotlin
val packageInfo = context.packageManager.getPackageArchiveInfo(
    file.path, PackageManager.GET_SIGNATURES
)
// 对比签名与当前应用签名
```
- 优点：更强的保证（即使服务器被攻破，未经开发者签名的 APK 仍会被拒绝）
- 缺点：实现较复杂，Android API 差异需处理
- 风险：中等（API 兼容性）

### 方案 C：强制 HTTPS + 证书 Pinning
确保下载 URL 强制使用 HTTPS，并在 OkHttpClient 中配置证书 Pinning：
```kotlin
val spec = CertificatePinner.Builder()
    .add("your-server.com", "sha256/...")
    .build()
OkHttpClient.Builder().certificatePinner(spec)
```
- 优点：防 MITM，但不防服务器端文件替换
- 缺点：证书更新时需要重新发版，无法防止服务器被攻陷的场景
- 风险：中等

## Recommended Action

（待 triage 后填写，推荐 A + HTTPS 组合）

## Technical Details

**受影响文件:**
- `android/app/src/main/java/com/simonlei/photoframe/updater/AutoUpdater.kt`
- `backend/handlers/version.go` - 需增加 apk_sha256 字段
- `backend/models/version.go`（如存在）- 数据模型需更新

**需要数据库变更:** `versions` 表增加 `apk_sha256 VARCHAR(64)` 字段

## Acceptance Criteria

- [ ] 后端 `/api/version/latest` 接口返回 `apk_sha256` 字段
- [ ] Android `AutoUpdater` 下载完成后校验 SHA-256
- [ ] 哈希不匹配时删除文件，记录日志，不触发安装
- [ ] 发布流程文档包含计算并填写 SHA-256 的步骤

## Work Log

- 2026-03-04: code-review 发现，由 security-sentinel 代理报告

## Resources

- 相关代码: `android/app/src/main/java/com/simonlei/photoframe/updater/AutoUpdater.kt`
- Android APK 完整性校验实践: https://developer.android.com/guide/topics/security/security-tips
