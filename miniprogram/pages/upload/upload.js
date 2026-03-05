const { getFileSize, authHeader } = require('../../utils/api')

const CONCURRENCY = 3
const COMPRESS_THRESHOLD = 2 * 1024 * 1024 // 2MB

Page({
  data: {
    deviceId: '',
    frameName: '',
    items: [],       // { tempFilePath, status: 'pending'|'uploading'|'done'|'fail', progress: 0, error: '' }
    uploading: false
  },

  onLoad(options) {
    this.setData({
      deviceId: options.device_id || '',
      frameName: decodeURIComponent(options.frame_name || '相框')
    })
    wx.setNavigationBarTitle({ title: `上传到 ${this.data.frameName}` })
  },

  onChoosePhoto() {
    wx.chooseMedia({
      count: 9,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const newItems = res.tempFiles.map(f => ({
          tempFilePath: f.tempFilePath,
          status: 'pending',
          progress: 0,
          error: ''
        }))
        this.setData({ items: [...this.data.items, ...newItems] })
      }
    })
  },

  onRemoveItem(e) {
    const idx = e.currentTarget.dataset.idx
    const items = [...this.data.items]
    items.splice(idx, 1)
    this.setData({ items })
  },

  onRetryItem(e) {
    const idx = e.currentTarget.dataset.idx
    this._uploadOne(idx)
  },

  async onStartUpload() {
    const pendingIndexes = this.data.items
      .map((item, idx) => ({ item, idx }))
      .filter(({ item }) => item.status === 'pending' || item.status === 'fail')
      .map(({ idx }) => idx)

    if (pendingIndexes.length === 0) {
      wx.showToast({ title: '没有待上传的照片', icon: 'none' })
      return
    }

    this.setData({ uploading: true })
    // 分批并发上传
    for (let i = 0; i < pendingIndexes.length; i += CONCURRENCY) {
      const batch = pendingIndexes.slice(i, i + CONCURRENCY)
      await Promise.all(batch.map(idx => this._uploadOne(idx)))
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

  async _uploadOne(idx) {
    let item = this.data.items[idx]
    if (!item) return

    this._updateItem(idx, { status: 'uploading', progress: 0, error: '' })

    let filePath = item.tempFilePath
    try {
      // 压缩大图
      const size = await getFileSize(filePath)
      if (size > COMPRESS_THRESHOLD) {
        const res = await new Promise((resolve, reject) =>
          wx.compressImage({ src: filePath, quality: 80, success: resolve, fail: reject })
        )
        filePath = res.tempFilePath
      }
    } catch (e) {
      // 压缩失败时继续用原图
    }

    return new Promise((resolve) => {
      const app = getApp()
      const task = wx.uploadFile({
        url: `${app.globalData.baseUrl}/api/upload`,
        filePath,
        name: 'file',
        formData: { device_id: this.data.deviceId },
        header: authHeader(),
        success: (res) => {
          if (res.statusCode === 200) {
            this._updateItem(idx, { status: 'done', progress: 100 })
          } else {
            let errMsg = '上传失败'
            try { errMsg = JSON.parse(res.data).error || errMsg } catch (e) {}
            this._updateItem(idx, { status: 'fail', error: errMsg })
          }
          resolve()
        },
        fail: (err) => {
          this._updateItem(idx, { status: 'fail', error: err.errMsg || '上传失败' })
          resolve()
        }
      })
      task.onProgressUpdate(({ progress }) => {
        this._updateItem(idx, { progress })
      })
    })
  },

  _updateItem(idx, patch) {
    const items = [...this.data.items]
    if (items[idx]) {
      items[idx] = Object.assign({}, items[idx], patch)
      this.setData({ items })
    }
  }
})
