package com.photoframe.util

/**
 * 判断 remote 版本号是否比 current 更新。
 * 支持任意长度的语义化版本号，如 "1.0.0", "2.1", "1.0.0.1"。
 */
fun isNewerVersion(remote: String, current: String): Boolean {
    val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(remoteParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val r = remoteParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (r > c) return true
        if (r < c) return false
    }
    return false
}
