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
import kotlinx.coroutines.*
import java.io.File

/**
 * App 内自动更新：检查版本 → 下载 APK → 触发安装
 */
class AutoUpdater(private val activity: Activity) {
    private val TAG = "AutoUpdater"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun checkAndUpdate(currentVersion: String) {
        scope.launch {
            try {
                val resp = ApiClient.service.latestVersion()
                val serverVersion = resp.version ?: return@launch
                if (serverVersion.isNewerThan(currentVersion) && resp.apkUrl != null) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(resp.apkUrl, serverVersion, resp.changelog)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "版本检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateDialog(apkUrl: String, version: String, changelog: String?) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("发现新版本 v$version")
            .setMessage(changelog ?: "有新版本可用，建议更新。")
            .setPositiveButton("立即更新") { _, _ -> downloadAndInstall(apkUrl) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String) {
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
                    installApk(destFile)
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

private fun String.isNewerThan(other: String): Boolean {
    fun parts(v: String) = v.split(".").mapNotNull { it.toIntOrNull() }
    val a = parts(this)
    val b = parts(other)
    for (i in 0 until maxOf(a.size, b.size)) {
        val ai = a.getOrElse(i) { 0 }
        val bi = b.getOrElse(i) { 0 }
        if (ai != bi) return ai > bi
    }
    return false
}
