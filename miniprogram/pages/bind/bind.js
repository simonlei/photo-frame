const { request } = require('../../utils/api')

Page({
  data: {
    scanning: false
  },

  onTapScan() {
    if (this.data.scanning) return
    this.setData({ scanning: true })

    wx.scanCode({
      onlyFromCamera: false,
      success: async (res) => {
        const rawData = res.result || ''
        // 解析 photoframe://bind?qr_token=xxx（正则，兼容所有基础库版本）
        let qrToken = ''
        if (rawData.startsWith('photoframe://bind')) {
          const match = rawData.match(/[?&]qr_token=([^&]+)/)
          qrToken = match ? decodeURIComponent(match[1]) : ''
        }

        if (!qrToken || !/^[A-Za-z0-9_-]{8,64}$/.test(qrToken)) {
          wx.showToast({ title: '无效的相框二维码', icon: 'none' })
          this.setData({ scanning: false })
          return
        }

        wx.showLoading({ title: '绑定中...' })
        try {
          const app = getApp()
          await request({
            url: `${app.globalData.baseUrl}/api/bind`,
            method: 'POST',
            data: { qr_token: qrToken }
          })
          wx.hideLoading()
          // 清除相框列表缓存，确保首页刷新看到新绑定的相框
          getApp().globalData.framesCache = null
          this.setData({ scanning: false })
          wx.showToast({ title: '绑定成功', icon: 'success' })
          setTimeout(() => wx.navigateBack(), 1200)
        } catch (e) {
          wx.hideLoading()
          wx.showToast({ title: e.message || '绑定失败', icon: 'none' })
          this.setData({ scanning: false })
        }
      },
      fail: () => {
        this.setData({ scanning: false })
      }
    })
  }
})
