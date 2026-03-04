package com.photoframe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.photoframe.BindActivity

/**
 * 开机自启：设备重启后自动打开相框 App
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startActivity(Intent(context, BindActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
