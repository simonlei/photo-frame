/**
 * 统一 HTTP 请求封装
 * - 自动携带 Authorization 头
 * - 检查 HTTP 状态码，401 自动清除 token 并提示
 */

function getApp_() {
  return getApp()
}

function authHeader() {
  const app = getApp_()
  const token = (app && app.globalData && app.globalData.token) || wx.getStorageSync('token') || ''
  return { Authorization: `Bearer ${token}` }
}

/**
 * @param {object} options - wx.request 参数，header 会自动合并认证头
 * @returns {Promise}
 */
function request(options) {
  return new Promise((resolve, reject) => {
    wx.request({
      ...options,
      header: Object.assign({}, authHeader(), options.header || {}),
      success(res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          const app = getApp_()
          if (app) app.globalData.token = ''
          wx.showToast({ title: '登录已过期，请重新进入', icon: 'none' })
          reject(new Error('未登录'))
        } else {
          const msg = (res.data && res.data.error) || `请求失败 (${res.statusCode})`
          reject(new Error(msg))
        }
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络请求失败'))
      }
    })
  })
}

/**
 * 获取文件大小（字节）
 * @param {string} filePath 临时文件路径
 * @returns {Promise<number>}
 */
function getFileSize(filePath) {
  return new Promise((resolve, reject) => {
    const fs = wx.getFileSystemManager()
    fs.getFileInfo({
      filePath,
      success: (res) => resolve(res.size),
      fail: () => resolve(0) // 获取失败视为 0，不阻断流程
    })
  })
}

module.exports = { request, authHeader, getFileSize }
