// 从环境配置读取服务器地址，避免硬编码
// 在 App.vue 的 onLaunch 中调用 initBaseUrl() 设置
let BASE_URL = 'https://your-server.com'

export function initBaseUrl(url: string) {
  BASE_URL = url.replace(/\/$/, '')
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

function authHeader() {
  return { Authorization: `Bearer ${getToken()}` }
}

/** 统一请求封装：自动检查 HTTP 状态码，4xx/5xx 抛出错误 */
function request<T>(options: UniApp.RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      ...options,
      success: (res: any) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data as T)
        } else if (res.statusCode === 401) {
          // token 失效，清除并提示重新登录
          uni.removeStorageSync('token')
          uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
          reject(new Error('请重新登录'))
        } else {
          reject(new Error(res.data?.error || `请求失败 (${res.statusCode})`))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '网络请求失败'))
    })
  })
}

/** 微信登录，返回 token */
export async function wxLogin(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: ({ code }) => {
        request<{ token: string }>({
          url: `${BASE_URL}/api/wx-login`,
          method: 'POST',
          data: { code }
        }).then(data => {
          if (data.token) {
            uni.setStorageSync('token', data.token)
            resolve(data.token)
          } else {
            reject(new Error('登录失败'))
          }
        }).catch(reject)
      },
      fail: reject
    })
  })
}

/** 获取我绑定的相框列表 */
export async function getMyFrames(): Promise<any[]> {
  const data = await request<{ frames: any[] }>({
    url: `${BASE_URL}/api/my/frames`,
    header: authHeader()
  })
  return data.frames || []
}

/** 扫码绑定相框 */
export async function bindFrame(qrToken: string): Promise<any> {
  return request({
    url: `${BASE_URL}/api/bind`,
    method: 'POST',
    header: authHeader(),
    data: { qr_token: qrToken }
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
      fail: (err) => reject(new Error(err.errMsg || '上传失败'))
    })
    if (onProgress) {
      task.onProgressUpdate(({ progress }) => onProgress(progress))
    }
  })
}

/** 获取相框照片列表 */
export async function getPhotos(deviceId: string): Promise<any[]> {
  const data = await request<{ photos: any[] }>({
    url: `${BASE_URL}/api/photos`,
    data: { device_id: deviceId },
    header: authHeader()
  })
  return data.photos || []
}

/** 删除照片 */
export async function deletePhoto(photoId: number): Promise<void> {
  await request({
    url: `${BASE_URL}/api/photos/${photoId}`,
    method: 'DELETE',
    header: authHeader()
  })
}
