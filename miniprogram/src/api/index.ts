// API 基础 URL，开发时可改为 http://localhost:8080
const BASE_URL = 'https://your-server.com'

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

function authHeader() {
  return { Authorization: `Bearer ${getToken()}` }
}

/** 微信登录，返回 token */
export async function wxLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: ({ code }) => {
        uni.request({
          url: `${BASE_URL}/api/wx-login`,
          method: 'POST',
          data: { code },
          success: (res: any) => {
            const token = res.data?.token
            if (token) {
              uni.setStorageSync('token', token)
              resolve(token)
            } else {
              reject(new Error('登录失败'))
            }
          },
          fail: reject
        })
      },
      fail: reject
    })
  })
}

/** 获取我绑定的相框列表 */
export async function getMyFrames(): Promise<any[]> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/my/frames`,
      header: authHeader(),
      success: (res: any) => resolve(res.data?.frames || []),
      fail: reject
    })
  })
}

/** 扫码绑定相框 */
export async function bindFrame(qrToken: string): Promise<any> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/bind`,
      method: 'POST',
      header: authHeader(),
      data: { qr_token: qrToken },
      success: (res: any) => {
        if (res.statusCode === 200) resolve(res.data)
        else reject(new Error(res.data?.error || '绑定失败'))
      },
      fail: reject
    })
  })
}

/** 上传单张照片，返回 Promise，支持进度回调 */
export function uploadPhoto(
  filePath: string,
  deviceId: string,
  onProgress?: (percent: number) => void
): Promise<{ id: number; url: string }> {
  return new Promise((resolve, reject) => {
    const task = uni.uploadFile({
      url: `${BASE_URL}/api/upload`,
      filePath,
      name: 'file',
      formData: { device_id: deviceId },
      header: authHeader(),
      success: (res) => {
        try {
          const data = JSON.parse(res.data)
          if (res.statusCode === 200) resolve(data)
          else reject(new Error(data.error || '上传失败'))
        } catch {
          reject(new Error('响应解析失败'))
        }
      },
      fail: reject
    })
    if (onProgress) {
      task.onProgressUpdate(({ progress }) => onProgress(progress))
    }
  })
}

/** 获取相框照片列表 */
export async function getPhotos(deviceId: string): Promise<any[]> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/photos`,
      data: { device_id: deviceId },
      header: authHeader(),
      success: (res: any) => resolve(res.data?.photos || []),
      fail: reject
    })
  })
}

/** 删除照片 */
export async function deletePhoto(photoId: number): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/photos/${photoId}`,
      method: 'DELETE',
      header: authHeader(),
      success: (res: any) => {
        if (res.statusCode === 200) resolve()
        else reject(new Error(res.data?.error || '删除失败'))
      },
      fail: reject
    })
  })
}
