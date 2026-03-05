---
status: pending
priority: p3
issue_id: "030"
tags: [code-review, miniprogram, security, config]
---

# [P3] sitemap.json 允许全部页面被微信爬虫索引，功能性页面不应开放

## Problem Statement

`sitemap.json` 当前配置：

```json
"rules": [{ "action": "allow", "page": "*" }]
```

这会让 `upload`、`bind`、`manage` 等功能性页面被微信索引，暴露小程序内部页面结构，协助攻击者进行信息收集。功能性页面无需被搜索引擎发现。

## Proposed Solutions

```json
{
  "rules": [
    { "action": "allow",    "page": "pages/index/index" },
    { "action": "disallow", "page": "*" }
  ]
}
```

## Acceptance Criteria

- [ ] 只有首页允许被索引
- [ ] `upload`、`bind`、`manage` 页面被设为 disallow

## Work Log

- 2026-03-04: 由 security-sentinel 发现
