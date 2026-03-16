const API_BASE = '/api/admin'

function getToken(): string | null {
  return sessionStorage.getItem('admin_token')
}

export function saveToken(token: string) {
  sessionStorage.setItem('admin_token', token)
}

export function clearToken() {
  sessionStorage.removeItem('admin_token')
}

export function isLoggedIn(): boolean {
  return !!getToken()
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken()
  const res = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  if (res.status === 401) {
    clearToken()
    window.location.href = '/admin/'
    throw new Error('Unauthorized')
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error || `请求失败 (${res.status})`)
  }
  return res.json()
}

export async function verifyToken(token: string): Promise<boolean> {
  const res = await fetch(`${API_BASE}/stats`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  return res.ok
}

export interface Stats {
  user_count: number
  device_count: number
  photo_count: number
  top_devices: { device_id: string; device_name: string; photo_count: number }[]
}

export interface DeviceItem {
  id: string
  name: string
  user_count: number
  photo_count: number
  created_at: string
}

export interface UserItem {
  id: number
  nickname: string
  device_count: number
  photo_count: number
  created_at: string
}

export interface PhotoItem {
  id: number
  url: string
  device_id?: string
  device_name?: string
  uploader_name: string
  taken_at?: string          // EXIF 拍摄时间（新增）
  uploaded_at: string
  latitude?: number          // 纬度（新增）
  longitude?: number         // 经度（新增）
  location_address?: string  // 详细地址（新增）
  camera_make?: string       // 相机制造商（新增）
  camera_model?: string      // 相机型号（新增）
}

export interface PagedResult<T> {
  total: number
  page: number
  page_size: number
  photos: T[]
}

export const api = {
  stats: () => request<Stats>('/stats'),

  devices: () => request<{ devices: DeviceItem[] }>('/devices'),
  deleteDevice: (id: string) => request<{ message: string }>(`/devices/${id}`, { method: 'DELETE' }),
  devicePhotos: (id: string, page = 1, pageSize = 50) =>
    request<PagedResult<PhotoItem>>(`/devices/${id}/photos?page=${page}&page_size=${pageSize}`),

  users: () => request<{ users: UserItem[] }>('/users'),

  photos: (page = 1, pageSize = 50) =>
    request<PagedResult<PhotoItem>>(`/photos?page=${page}&page_size=${pageSize}`),
  deletePhoto: (id: number) => request<{ message: string }>(`/photos/${id}`, { method: 'DELETE' }),
}
