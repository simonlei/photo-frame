const BASE_URL = 'https://your-server.com' // 部署前替换为真实域名

App({
  globalData: {
    baseUrl: BASE_URL,
    token: ''
  },

  onLaunch() {
    const token = wx.getStorageSync('token')
    if (token) {
      this.globalData.token = token
    } else {
      this._login()
    }
  },

  _login() {
    wx.login({
      success: ({ code }) => {
        wx.request({
          url: `${BASE_URL}/api/wx-login`,
          method: 'POST',
          data: { code },
          success: (res) => {
            if (res.statusCode === 200 && res.data && res.data.token) {
              this.globalData.token = res.data.token
              wx.setStorageSync('token', res.data.token)
            } else {
              wx.showToast({ title: '登录失败，请重试', icon: 'none' })
            }
          },
          fail() {
            wx.showToast({ title: '网络异常，请检查连接', icon: 'none' })
          }
        })
      },
      fail() {
        wx.showToast({ title: '微信登录失败', icon: 'none' })
      }
    })
  }
})
