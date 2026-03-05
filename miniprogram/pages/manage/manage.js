const { request } = require('../../utils/api')

Page({
  data: {
    frames: [],
    currentFrameIdx: 0,
    photos: [],
    loading: false
  },

  onShow() {
    this._loadFrames()
  },

  async _loadFrames() {
    try {
      const app = getApp()
      const data = await request({ url: `${app.globalData.baseUrl}/api/my/frames` })
      const frames = data.frames || []
      this.setData({ frames, currentFrameIdx: 0 })
      if (frames.length > 0) {
        this._loadPhotos(frames[0].id)
      }
    } catch (e) {
      if (e.message !== '未登录') {
        wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      }
    }
  },

  onFrameChange(e) {
    const idx = Number(e.detail.value)
    const frame = this.data.frames[idx]
    this.setData({ currentFrameIdx: idx })
    if (frame) this._loadPhotos(frame.id)
  },

  async _loadPhotos(deviceId) {
    this.setData({ loading: true, photos: [] })
    try {
      const app = getApp()
      const data = await request({
        url: `${app.globalData.baseUrl}/api/photos`,
        data: { device_id: deviceId }
      })
      this.setData({ photos: data.photos || [] })
    } catch (e) {
      if (e.message !== '未登录') {
        wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  onTapDelete(e) {
    const photo = e.currentTarget.dataset.photo
    wx.showModal({
      title: '删除照片',
      content: '确认删除这张照片？相框上也会同步移除。',
      confirmColor: '#e53935',
      success: async (res) => {
        if (!res.confirm) return
        try {
          const app = getApp()
          await request({
            url: `${app.globalData.baseUrl}/api/photos/${photo.id}`,
            method: 'DELETE'
          })
          const photos = this.data.photos.filter(p => p.id !== photo.id)
          this.setData({ photos })
          wx.showToast({ title: '已删除', icon: 'success' })
        } catch (e) {
          wx.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    })
  }
})
