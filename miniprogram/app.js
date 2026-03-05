const BASE_URL = 'http://localhost:8080' // 部署前替换为真实域名

App({
  globalData: {
    baseUrl: BASE_URL,
    token: '',
    framesCache: null,
    framesCacheTime: 0
  },

  onLaunch() {
    // ready 是一个 Promise，登录完成（无论成功失败）后 resolve
    // 页面的 onShow 应 await getApp().ready 后再发请求，避免竞态
    let resolve
    this.ready = new Promise(r => { resolve = r })

    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
      resolve()
    } else {
      this._login(resolve)
    }
  },

  _login(resolve) {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${BASE_URL}/api/wx-login`,
          method: 'POST',
          data: { code },
          success: (res) => {
            if (res.statusCode === 200 && res.data && res.data.token) {
              this.globalData.token = res.data.token
              try {
                wx.setStorageSync('token', res.data.token)
              } catch (e) {
                console.error('token 持久化失败', e)
              }
            } else {
              wx.showToast({ title: '登录失败，请重试', icon: 'none' })
            }
            resolve() // 无论登录成功失败，都 resolve，让页面继续渲染
          },
          fail() {
            wx.showToast({ title: '网络异常，请检查连接', icon: 'none' })
            resolve()
          }
        })
      },
      fail() {
        wx.showToast({ title: '微信登录失败', icon: 'none' })
        resolve()
      }
    })
  }
})
