package com.photoframe.util

/**
 * 判断当前时间是否在夜间时段内。
 * 支持同日（如 08:00~22:00）和跨午夜（如 22:00~08:00）两种情况。
 */
fun isInNightPeriod(
    currentHour: Int, currentMinute: Int,
    startHour: Int, startMinute: Int,
    endHour: Int, endMinute: Int
): Boolean {
    val current = currentHour * 60 + currentMinute
    val start = startHour * 60 + startMinute
    val end = endHour * 60 + endMinute

    return if (start <= end) {
        current in start until end      // 同日：如 08:00~22:00
    } else {
        current >= start || current < end // 跨午夜：如 22:00~08:00
    }
}
