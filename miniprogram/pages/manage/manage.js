const { fetchMyFrames, request, ERR_UNAUTHORIZED } = require('../../utils/api')

Page({
  data: {
    frames: [],
    currentFrameIdx: 0,
    photos: [],
    loading: false,
    loadingMore: false,
    hasMore: true,
    _currentDeviceId: ''
  },

  onShow() {
    this._loadFrames()
  },

  async _loadFrames() {
    await getApp().ready  // 等待登录完成，避免首次冷启动竞态
    this.setData({ loading: true, photos: [] })
    try {
      const frames = await fetchMyFrames()
      this.setData({ frames })
      // 保持 currentFrameIdx，避免切换 Tab 后丢失相框选择
      const idx = this.data.currentFrameIdx < frames.length ? this.data.currentFrameIdx : 0
      if (frames.length > 0) {
        this.setData({ currentFrameIdx: idx })
        this._loadPhotos(frames[idx].id)
      }
    } catch (e) {
      if (e.code !== ERR_UNAUTHORIZED) {
        wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  onFrameChange(e) {
    const idx = Number(e.detail.value)
    const frame = this.data.frames[idx]
    this.setData({ currentFrameIdx: idx })
    if (frame) this._loadPhotos(frame.id)
  },

  async _loadPhotos(deviceId, append) {
    if (!append) {
      this.setData({ photos: [], hasMore: true, _currentDeviceId: deviceId })
    }
    this.setData({ loadingMore: true })
    try {
      const app = getApp()
      const lastId = append && this.data.photos.length > 0
        ? this.data.photos[this.data.photos.length - 1].id
        : 0
      const data = await request({
        url: `${app.globalData.baseUrl}/api/photos`,
        data: { device_id: deviceId, since: lastId, limit: 30 }
      })
      const newPhotos = data.photos || []
      this.setData({
        photos: append ? [...this.data.photos, ...newPhotos] : newPhotos,
        hasMore: newPhotos.length === 30
      })
    } catch (e) {
      if (e.code !== ERR_UNAUTHORIZED) {
        wx.showToast({ title: e.message || '加载失败', icon: 'none' })
      }
    } finally {
      this.setData({ loadingMore: false, loading: false })
    }
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loadingMore && this.data._currentDeviceId) {
      this._loadPhotos(this.data._currentDeviceId, true)
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
