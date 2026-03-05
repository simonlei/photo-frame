const { fetchMyFrames, ERR_UNAUTHORIZED } = require('../../utils/api')

Page({
  data: {
    frames: [],
    loading: false
  },

  onShow() {
    this._loadFrames()
  },

  async _loadFrames() {
    await getApp().ready  // 等待登录完成，避免首次冷启动竞态
    this.setData({ loading: true })
    try {
      const frames = await fetchMyFrames()
      this.setData({ frames })
    } catch (e) {
      if (e.code !== ERR_UNAUTHORIZED) {
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
