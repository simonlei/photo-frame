---
status: pending
priority: p3
issue_id: "042"
tags: [code-review, react, refactor, antd]
---

# DevicePhotos 和 Photos 页面 70% 代码重复，Ant Design Menu API 已废弃

## Problem Statement

1. `DevicePhotos.tsx` 和 `Photos.tsx` 中图片网格（缩略图+删除按钮+分页）几乎完全相同，可提取 `PhotoGrid` 组件。
2. `App.tsx` 使用 Ant Design v4 风格的 `<Menu.Item>` children API，v5 已废弃，将在下一主版本移除。

## Findings

- **Files:** `admin-frontend/src/pages/DevicePhotos.tsx`, `admin-frontend/src/pages/Photos.tsx`
- 两页差异仅：DevicePhotos 有返回按钮 + 读 useParams；caption 内容略有不同
- **File:** `admin-frontend/src/App.tsx:41-54` — `<Menu.Item>` 废弃 API

## Proposed Solutions

### Option A: 提取 PhotoGrid 组件 + 迁移 Menu API
```typescript
// components/PhotoGrid.tsx
interface Props {
  photos: PhotoItem[]
  loading: boolean
  total: number
  page: number
  pageSize: number
  onDelete: (id: number) => Promise<void>
  onPageChange: (p: number) => void
  renderCaption: (p: PhotoItem) => React.ReactNode
}
```

App.tsx Menu 迁移到 `items` prop：
```typescript
const menuItems = [
  { key: 'dashboard', icon: <DashboardOutlined />, label: <Link to="/">概览</Link> },
  { key: 'devices',   icon: <MobileOutlined />,   label: <Link to="/devices">设备管理</Link> },
  ...
]
<Menu selectedKeys={[selectedKey]} mode="inline" items={menuItems} />
```

## Acceptance Criteria

- [ ] 提取 `PhotoGrid` 组件，DevicePhotos 和 Photos 使用它
- [ ] App.tsx 迁移到 Ant Design v5 `items` prop
- [ ] 消除 AntD 废弃 API console 警告

## Work Log

- 2026-03-11: Found by code-simplicity-reviewer and kieran-typescript-reviewer agents
