---
status: pending
priority: p1
issue_id: "035"
tags: [code-review, react, hooks, bug]
---

# DevicePhotos.tsx useEffect 缺少依赖 + 无错误处理导致静默失败

## Problem Statement

`DevicePhotos.tsx` 中 `useEffect` 依赖数组缺少 `load` 函数，形成 stale closure bug：当 `id` 变化时 `load` 仍捕获旧的状态值。同时 `load()` 没有 `.catch()`，任何 API 失败都静默忽略，用户看到空列表无任何提示。

## Findings

- **File:** `admin-frontend/src/pages/DevicePhotos.tsx`
```typescript
function load(p = page) {  // 使用 state 作默认值 —— 反模式
  if (!id) return
  setLoading(true)
  api.devicePhotos(id, p, pageSize)
    .then(r => { setPhotos(r.photos); setTotal(r.total) })
    // 无 .catch()!
    .finally(() => setLoading(false))
}

useEffect(() => { load() }, [id, page])  // 缺少 load 在依赖中
```
- `Photos.tsx` 存在相同问题

## Proposed Solutions

### Option A: useCallback + AbortController（推荐）
```typescript
const [error, setError] = useState<string | null>(null)

useEffect(() => {
  if (!id) return
  const controller = new AbortController()
  setLoading(true)
  setError(null)
  api.devicePhotos(id, page, pageSize)
    .then(r => { setPhotos(r.photos); setTotal(r.total) })
    .catch(err => {
      if (err.name !== 'AbortError') {
        setError(err instanceof Error ? err.message : '加载失败')
      }
    })
    .finally(() => setLoading(false))
  return () => controller.abort()
}, [id, page])
```
同时在 JSX 中展示 error state。

## Acceptance Criteria

- [ ] `useEffect` 依赖数组包含所有实际依赖
- [ ] 添加 `.catch()` 并展示错误信息给用户
- [ ] 添加 AbortController 防止组件卸载后 setState
- [ ] `Photos.tsx` 同步修复

## Work Log

- 2026-03-11: Found by kieran-typescript-reviewer agent
