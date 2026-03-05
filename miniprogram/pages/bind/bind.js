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
        // 解析 photoframe://bind?qr_token=xxx
        let qrToken = ''
        try {
          if (rawData.startsWith('photoframe://bind')) {
            const url = new URL(rawData.replace('photoframe://', 'https://dummy.com/'))
            qrToken = url.searchParams.get('qr_token') || ''
          }
        } catch (e) {
          // URL 解析失败，尝试直接取整个内容作为 token
        }

        if (!qrToken) {
          // 兼容：直接把扫码结果当 qr_token
          qrToken = rawData.trim()
        }

        if (!qrToken) {
          wx.showToast({ title: '无效的二维码', icon: 'none' })
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
