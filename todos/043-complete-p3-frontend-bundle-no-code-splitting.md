---
status: pending
priority: p3
issue_id: "043"
tags: [code-review, performance, frontend]
---

# 前端 bundle 无代码分割，Photos.tsx 双重 API 调用

## Problem Statement

1. 所有页面静态导入，首次加载需解析 1MB bundle（gzip 325KB）。
2. `Photos.tsx`（及 `DevicePhotos.tsx`）的 Pagination `onChange` 同时调用 `setPage(p)` 和 `load(p)`，`useEffect` 又会触发第二次 `load()`，每次翻页发出两个相同请求。

## Findings

- **File:** `admin-frontend/vite.config.ts` — 无 `build.rollupOptions` 配置
- **File:** `admin-frontend/src/pages/Photos.tsx:61` — `onChange={p => { setPage(p); load(p) }}`
- build 日志：`1,001.75 kB` chunk with Vite chunk size warning

## Proposed Solutions

### Option A: 路由懒加载 + vendor 分包 + 修复 onChange
```typescript
// App.tsx
const Dashboard    = lazy(() => import('./pages/Dashboard'))
// ...
<Suspense fallback={<Spin />}><Routes>...</Routes></Suspense>

// vite.config.ts
build: {
  rollupOptions: {
    output: {
      manualChunks: {
        antd:  ['antd'],
        icons: ['@ant-design/icons'],
        react: ['react', 'react-dom', 'react-router-dom'],
      }
    }
  }
}

// Photos.tsx / DevicePhotos.tsx - 修复双重调用
onChange={p => setPage(p)}  // 仅更新 state，useEffect 负责触发 load
```

## Acceptance Criteria

- [ ] 使用 `React.lazy` 懒加载五个页面组件
- [ ] vite.config.ts 配置 manualChunks 分包 antd/icons/react
- [ ] Photos.tsx 和 DevicePhotos.tsx 的 Pagination onChange 只调用 setPage
- [ ] 首次加载 bundle 尺寸减少 40% 以上

## Work Log

- 2026-03-11: Found by performance-oracle agent
