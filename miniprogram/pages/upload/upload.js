const { authHeader } = require('../../utils/api')

const CONCURRENCY = 3
const MAX_PHOTOS = 9

Page({
  data: {
    deviceId: '',
    frameName: '',
    items: [],       // { id(tempFilePath), tempFilePath, compressedPath, status, progress, error }
    uploading: false
  },

  // 维护活跃的 UploadTask 引用，用于 onUnload 时取消
  _tasks: [],
  // 进度节流计时器 Map，key 为 tempFilePath
  _progressTimers: {},

  onLoad(options) {
    this.setData({
      deviceId: options.device_id || '',
      frameName: decodeURIComponent(options.frame_name || '相框')
    })
    wx.setNavigationBarTitle({ title: `上传到 ${this.data.frameName}` })
  },

  onUnload() {
    // 取消所有进行中的上传，防止内存泄漏和回调悬挂
    this._tasks.forEach(t => { try { t.abort() } catch (e) {} })
    this._tasks = []
    // 清除所有节流计时器
    Object.values(this._progressTimers).forEach(timer => clearTimeout(timer))
    this._progressTimers = {}
  },

  onChoosePhoto() {
    const remaining = MAX_PHOTOS - this.data.items.length
    if (remaining <= 0) {
      wx.showToast({ title: '最多选择 9 张', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: remaining,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const newItems = res.tempFiles.map(f => ({
          id: f.tempFilePath,
          tempFilePath: f.tempFilePath,
          status: 'pending',
          progress: 0,
          error: ''
        }))
        this.setData({ items: [...this.data.items, ...newItems] })
      },
      fail: (err) => {
        if (err.errMsg && err.errMsg.includes('auth deny')) {
          wx.showModal({
            title: '需要相册权限',
            content: '请在设置中允许访问相册和摄像头',
            confirmText: '去设置',
            success: (r) => { if (r.confirm) wx.openSetting() }
          })
        }
        // 用户主动取消不提示
      }
    })
  },

  onRemoveItem(e) {
    if (this.data.uploading) return  // 上传中禁止删除，防止下标偏移
    const itemId = e.currentTarget.dataset.id
    const items = this.data.items.filter(it => it.id !== itemId)
    this.setData({ items })
  },

  onRetryItem(e) {
    if (this.data.uploading) return  // 批量上传中禁止单独重试
    const itemId = e.currentTarget.dataset.id
    const item = this.data.items.find(it => it.id === itemId)
    if (item) this._uploadOne(item)
  },

  async onStartUpload() {
    const pendingItems = this.data.items.filter(
      it => it.status === 'pending' || it.status === 'fail'
    )
    if (pendingItems.length === 0) {
      wx.showToast({ title: '没有待上传的照片', icon: 'none' })
      return
    }

    this.setData({ uploading: true })
    for (let i = 0; i < pendingItems.length; i += CONCURRENCY) {
      const batch = pendingItems.slice(i, i + CONCURRENCY)
      await Promise.all(batch.map(item => this._uploadOne(item)))
    }
    this.setData({ uploading: false })

    const doneCount = this.data.items.filter(it => it.status === 'done').length
    const failCount = this.data.items.filter(it => it.status === 'fail').length
    if (failCount === 0) {
      wx.showToast({ title: `已上传 ${doneCount} 张`, icon: 'success' })
    } else {
      wx.showToast({ title: `${doneCount} 张成功，${failCount} 张失败`, icon: 'none' })
    }
  },

  async _uploadOne(item) {
    if (!item || !this.data) return
    this._updateItem(item.id, { status: 'uploading', progress: 0, error: '' })

    // 直接使用原图，保留 EXIF（含 GPS）
    const uploadPath = item.tempFilePath

    return new Promise((resolve) => {
      if (!this.data) { resolve(); return }
      const app = getApp()
      const task = wx.uploadFile({
        url: `${app.globalData.baseUrl}/api/upload`,
        filePath: uploadPath,
        name: 'file',
        formData: { device_id: this.data.deviceId },
        header: authHeader(),
        success: (res) => {
          if (!this.data) { resolve(); return }
          if (res.statusCode === 200) {
            this._updateItem(item.id, { status: 'done', progress: 100 })
          } else {
            let errMsg = '上传失败'
            try { errMsg = JSON.parse(res.data).error || errMsg } catch (e) {}
            this._updateItem(item.id, { status: 'fail', error: errMsg })
          }
          this._tasks = this._tasks.filter(t => t !== task)
          resolve()
        },
        fail: (err) => {
          if (!this.data) { resolve(); return }
          this._updateItem(item.id, { status: 'fail', error: err.errMsg || '上传失败' })
          this._tasks = this._tasks.filter(t => t !== task)
          resolve()
        }
      })
      this._tasks.push(task)

      // 节流进度更新：同一张图 300ms 内最多更新一次，使用路径语法减少 setData 开销
      task.onProgressUpdate(({ progress }) => {
        if (!this.data) return
        const key = item.id
        if (this._progressTimers[key]) return
        this._progressTimers[key] = setTimeout(() => {
          delete this._progressTimers[key]
          if (!this.data) return
          const idx = this.data.items.findIndex(it => it.id === key)
          if (idx >= 0) {
            this.setData({ [`items[${idx}].progress`]: progress })
          }
        }, 300)
      })
    })
  },

  // 用 id（tempFilePath）作为稳定 key，避免数组下标偏移问题
  _updateItem(itemId, patch) {
    if (!this.data) return
    const items = this.data.items.map(it =>
      it.id === itemId ? Object.assign({}, it, patch) : it
    )
    this.setData({ items })
  }
})
