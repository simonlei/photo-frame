package com.photoframe.updater

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.photoframe.data.ApiClient
import com.photoframe.util.isNewerVersion
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

/**
 * App 内自动更新：检查版本 → 下载 APK → SHA-256 校验 → 触发安装
 */
class AutoUpdater(private val activity: Activity) {
    private val TAG = "AutoUpdater"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun checkAndUpdate(currentVersion: String, onNoUpdate: (() -> Unit)? = null) {
        scope.launch {
            try {
                val resp = ApiClient.service.latestVersion()
                val serverVersion = resp.version ?: run {
                    withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                    return@launch
                }
                if (isNewerVersion(serverVersion, currentVersion) && resp.apkUrl != null) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(resp.apkUrl, serverVersion, resp.changelog, resp.apkSha256)
                    }
                } else {
                    withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "版本检查失败: ${e.message}")
                withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
            }
        }
    }

    private fun showUpdateDialog(apkUrl: String, version: String, changelog: String?, apkSha256: String?) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("发现新版本 v$version")
            .setMessage(changelog ?: "有新版本可用，建议更新。")
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(apkUrl, apkSha256) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String, expectedSha256: String?) {
        val destFile = File(
            activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "update.apk"
        )
        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setTitle("下载更新")
            setDescription("正在下载新版本...")
            setDestinationUri(Uri.fromFile(destFile))
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }
        val dm = activity.getSystemService(DownloadManager::class.java)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    activity.unregisterReceiver(this)
                    verifyAndInstall(destFile, expectedSha256)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    /** 校验 SHA-256 后再安装，防止下载过程中的文件篡改 */
    private fun verifyAndInstall(apkFile: File, expectedSha256: String?) {
        if (expectedSha256 != null) {
            val actualSha256 = calculateSha256(apkFile)
            if (actualSha256 != expectedSha256.lowercase()) {
                apkFile.delete()
                Log.e(TAG, "APK 完整性校验失败！期望: $expectedSha256, 实际: $actualSha256")
                activity.runOnUiThread {
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("更新失败")
                        .setMessage("文件完整性校验失败，请重试或手动更新。")
                        .setPositiveButton("确定", null)
                        .show()
                }
                return
            }
        }
        installApk(apkFile)
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

    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            })
            return
        }
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
