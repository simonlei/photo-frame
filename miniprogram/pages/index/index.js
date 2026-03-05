const { request } = require('../../utils/api')

Page({
  data: {
    frames: [],
    loading: false
  },

  onShow() {
    this._loadFrames()
  },

  async _loadFrames() {
    this.setData({ loading: true })
    try {
      const app = getApp()
      const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
      this.setData({ frames: data.frames || [] })
    } catch (e) {
      if (e.message !== '未登录') {
        wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  onTapFrame(e) {
    const frame = e.currentTarget.dataset.frame
    wx.navigateTo({
      url: `/pages/upload/upload?device_id=${frame.id}&frame_name=${encodeURIComponent(frame.name)}`
    })
  },

  onTapBind() {
    wx.navigateTo({ url: '/pages/bind/bind' })
  }
})
