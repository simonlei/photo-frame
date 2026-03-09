---
title: ViewPager2 内 GestureDetector.onSingleTapConfirmed 无法触发
date: 2026-03-09
category: ui-bugs
tags: [android, viewpager2, touch-events, gesture-detector, recyclerview]
components: [MainActivity, ViewPager2, GestureDetector]
symptoms:
  - 全屏 ViewPager2 界面单击屏幕无响应
  - onSingleTapConfirmed 回调从未被触发
  - setOnTouchListener 注册的手势识别器不工作
related:
  - docs/solutions/multi-category/photo-frame-comprehensive-code-review.md
---

# ViewPager2 内 GestureDetector.onSingleTapConfirmed 无法触发

## 症状

在以 `ViewPager2` 为全屏内容的 `MainActivity` 中，通过 `setOnTouchListener` 注册 `GestureDetector`，单击屏幕时 `onSingleTapConfirmed` 回调从不执行，无法跳转到 `SettingsActivity`。

## 根本原因

`ViewPager2` 内部封装了 `RecyclerView` 用于实现页面滑动。Android 触摸事件分发遵循以下链路：

```
Activity.dispatchTouchEvent
  └→ ViewGroup.dispatchTouchEvent
       └→ ViewPager2.dispatchTouchEvent
            └→ RecyclerView（内部）.onTouchEvent  ← 消费事件，处理水平滑动
```

`RecyclerView` 会消费触摸事件（处理滑动），导致外层通过 `viewPager.setOnTouchListener` 注册的监听器只能收到**不完整的事件序列**。`GestureDetector.onSingleTapConfirmed` 需要完整的 `ACTION_DOWN → ACTION_UP` 序列才能触发，序列中断后永远不会回调。

## 问题代码

```kotlin
// ❌ 无效：ViewPager2 内部 RecyclerView 消费事件，listener 收不到完整序列
val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        return true
    }
})
viewPager.setOnTouchListener { _, event ->
    gestureDetector.onTouchEvent(event)
    false
}
```

## 解决方案

覆盖 Activity 的 `dispatchTouchEvent`。该方法在**所有子 View 处理事件之前**被调用，能收到完整的事件序列，且不影响 ViewPager2 的滑动功能。

```kotlin
// MainActivity.kt

// 1. 提升为成员变量
private lateinit var gestureDetector: GestureDetector

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...

    // 2. 初始化（不需要 viewPager.setOnTouchListener）
    gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isNightMode) {
                screenScheduler.setBrightness(-1f)
                isNightMode = false
                handler.postDelayed({ isNightMode = true }, 30_000)
            } else {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            return true
        }
    })
}

// 3. 在 Activity 层拦截，早于所有子 View
override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    gestureDetector.onTouchEvent(ev)
    return super.dispatchTouchEvent(ev)  // 必须调用 super，否则 ViewPager2 滑动失效
}
```

### 关键点

| 方法 | 调用时机 | 能否收到完整序列 |
|------|---------|----------------|
| `viewPager.setOnTouchListener` | RecyclerView 消费后 | ❌ 序列可能中断 |
| `Activity.dispatchTouchEvent` | 所有子 View 之前 | ✅ 完整保证 |

**`super.dispatchTouchEvent(ev)` 必须调用**——若省略，整个 Activity 的触摸分发链断开，ViewPager2 滑动、按钮点击等所有交互全部失效。

## 预防策略

### Code Review Checklist

- [ ] 需要在 Activity 层监听触摸时，使用 `dispatchTouchEvent` 而非子 View 的 `setOnTouchListener`
- [ ] `dispatchTouchEvent` 中必须 `return super.dispatchTouchEvent(ev)`
- [ ] `GestureDetector` 作为成员变量初始化一次，不在 `onResume` 中重复创建
- [ ] 区分 `onSingleTapUp`（立即触发）和 `onSingleTapConfirmed`（排除拖拽/双击后触发）——全屏场景用后者

### 通用规律

凡是在内部实现了自己的触摸消费逻辑的 View（`ViewPager2`、`RecyclerView`、`MapView`、`WebView` 等），在其**外部**通过 `setOnTouchListener` 注册手势识别器均不可靠。正确模式：

```
需要全局拦截 → Activity.dispatchTouchEvent（在子 View 之前）
需要局部拦截 → ViewGroup.onInterceptTouchEvent（在子 View 之前，仅限该容器内）
```

## 相关文件

- 修复位置：`android/app/src/main/java/com/photoframe/MainActivity.kt:74-87, 140-143`
- 相关审查：`docs/solutions/multi-category/photo-frame-comprehensive-code-review.md`
