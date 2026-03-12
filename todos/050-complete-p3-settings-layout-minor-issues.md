---
status: pending
priority: p3
issue_id: "050"
tags: [code-review, quality, android]
dependencies: []
---

# 设置页布局小问题：无用 ID 和硬编码颜色

## Problem Statement

PR #3 在布局文件中引入了两处小问题：
1. `tv_invite_hint` 有 `android:id` 属性，但 `SettingsActivity.kt` 中从未通过 `findViewById` 引用，是误导性的无用 ID
2. 新增了硬编码颜色值 `#EEEEEE`（分割线），整个布局文件中 `#888888` 出现 3 次（均无对应资源），延续了旧的技术债

## Findings

**无用 ID（activity_settings.xml 第 123 行）：**
```xml
<TextView android:id="@+id/tv_invite_hint" ...>
```
`SettingsActivity.kt` 搜索 `tv_invite_hint`：无结果。读者会误以为代码某处在引用此 ID。

**硬编码颜色（activity_settings.xml）：**
- 第 96 行（已有代码）：`android:textColor="#888888"`
- 第 103 行（PR 新增）：`android:background="#EEEEEE"`
- 第 129 行（PR 新增）：`android:textColor="#888888"`
- 第 144 行（已有代码）：`android:textColor="#888888"`

`colors.xml` 当前仅有 1 个颜色 `ic_launcher_background`，`#888888` 是全局次要文字颜色，应统一管理。

## Proposed Solutions

### 方案 A：直接修复
1. 删除 `tv_invite_hint` 的 `android:id` 属性
2. 在 `res/values/colors.xml` 中添加：
```xml
<color name="text_secondary">#888888</color>
<color name="divider">#EEEEEE</color>
```
3. 将布局中所有 `#888888` 替换为 `@color/text_secondary`，`#EEEEEE` 替换为 `@color/divider`

**预计改动量：** ~8 行

## Recommended Action

方案 A，可作为独立小 PR 或与其他 Android 清理工作合并。

## Technical Details

- **受影响文件：**
  - `android/app/src/main/res/layout/activity_settings.xml`
  - `android/app/src/main/res/values/colors.xml`

## Acceptance Criteria

- [ ] `tv_invite_hint` 无 `android:id`（或有 id 但在代码中有引用）
- [ ] 布局中无裸色值字符串（`#888888`、`#EEEEEE` 替换为颜色资源引用）
- [ ] `colors.xml` 定义 `text_secondary` 和 `divider` 颜色

## Work Log

- 2026-03-11: 发现于 PR #3 code-simplicity-reviewer 审查，标识为优先级 2/3
