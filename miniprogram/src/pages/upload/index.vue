<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { uploadPhoto } from '../../api/index'

const deviceId = ref('')
const frameName = ref('')

interface UploadItem {
  path: string
  status: 'pending' | 'uploading' | 'done' | 'error'
  progress: number
  error?: string
}

const items = ref<UploadItem[]>([])
const isUploading = ref(false)

onLoad((options: any) => {
  deviceId.value = options?.device_id || ''
  frameName.value = options?.frame_name || '相框'
  uni.setNavigationBarTitle({ title: `上传到 ${frameName.value}` })
})

function choosePhotos() {
  uni.chooseMedia({
    count: 9,
    mediaType: ['image'],
    sourceType: ['album', 'camera'],
    success: ({ tempFiles }) => {
      const newItems: UploadItem[] = tempFiles.map(f => ({
        path: f.tempFilePath,
        status: 'pending',
        progress: 0
      }))
      items.value = [...items.value, ...newItems]
    }
  })
}

async function startUpload() {
  if (!deviceId.value) {
    uni.showToast({ title: '相框 ID 无效', icon: 'error' })
    return
  }
  const pendingItems = items.value.filter(i => i.status === 'pending')
  if (pendingItems.length === 0) return

  isUploading.value = true

  for (const item of pendingItems) {
    item.status = 'uploading'
    try {
      // 大于 8MB 先压缩
      const fileInfo = await getFileSize(item.path)
      let filePath = item.path
      if (fileInfo > 8 * 1024 * 1024) {
        const compressed = await compressImage(item.path)
        filePath = compressed
      }

      await uploadPhoto(filePath, deviceId.value, (p) => {
        item.progress = p
      })
      item.status = 'done'
      item.progress = 100
    } catch (e: any) {
      item.status = 'error'
      item.error = e?.message || '上传失败'
    }
  }

  isUploading.value = false

  const doneCount = items.value.filter(i => i.status === 'done').length
  uni.showToast({ title: `成功上传 ${doneCount} 张`, icon: 'success' })
}

function removeItem(index: number) {
  items.value.splice(index, 1)
}

function retryItem(item: UploadItem) {
  item.status = 'pending'
  item.progress = 0
  item.error = undefined
}

function getFileSize(path: string): Promise<number> {
  return new Promise(resolve => {
    const fs = uni.getFileSystemManager()
    fs.getFileInfo({ filePath: path, success: (i) => resolve(i.size), fail: () => resolve(0) })
  })
}

function compressImage(path: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.compressImage({ src: path, quality: 80, success: (r) => resolve(r.tempFilePath), fail: reject })
  })
}
</script>

<template>
  <view class="page">
    <view class="photo-grid">
      <view
        v-for="(item, index) in items"
        :key="index"
        class="photo-item"
      >
        <image :src="item.path" mode="aspectFill" class="photo-thumb" />
        <view v-if="item.status === 'uploading'" class="progress-bar">
          <view class="progress-fill" :style="{ width: item.progress + '%' }" />
        </view>
        <view v-if="item.status === 'done'" class="status-done">✓</view>
        <view v-if="item.status === 'error'" class="status-error" @tap="retryItem(item)">重试</view>
        <view v-if="item.status === 'pending'" class="remove-btn" @tap="removeItem(index)">×</view>
      </view>

      <view class="add-btn" @tap="choosePhotos">
        <text class="add-icon">+</text>
        <text class="add-text">选择照片</text>
      </view>
    </view>

    <button
      v-if="items.some(i => i.status === 'pending')"
      class="btn-upload"
      :disabled="isUploading"
      @tap="startUpload"
    >
      {{ isUploading ? '上传中...' : `上传 ${items.filter(i=>i.status==='pending').length} 张` }}
    </button>
  </view>
</template>

<style>
.page { padding: 20rpx; background: #f5f5f5; min-height: 100vh; }
.photo-grid { display: flex; flex-wrap: wrap; gap: 10rpx; margin-bottom: 30rpx; }
.photo-item { position: relative; width: 220rpx; height: 220rpx; border-radius: 8rpx; overflow: hidden; }
.photo-thumb { width: 100%; height: 100%; }
.progress-bar { position: absolute; bottom: 0; left: 0; right: 0; height: 8rpx; background: rgba(0,0,0,0.3); }
.progress-fill { height: 100%; background: #4CAF50; transition: width 0.3s; }
.status-done { position: absolute; top: 4rpx; right: 4rpx; width: 40rpx; height: 40rpx; background: #4CAF50; border-radius: 50%; color: #fff; text-align: center; line-height: 40rpx; font-size: 24rpx; }
.status-error { position: absolute; inset: 0; background: rgba(0,0,0,0.5); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; }
.remove-btn { position: absolute; top: 4rpx; right: 4rpx; width: 40rpx; height: 40rpx; background: rgba(0,0,0,0.5); border-radius: 50%; color: #fff; text-align: center; line-height: 40rpx; font-size: 32rpx; }
.add-btn { width: 220rpx; height: 220rpx; background: #fff; border-radius: 8rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 2rpx dashed #ccc; }
.add-icon { font-size: 60rpx; color: #ccc; }
.add-text { font-size: 24rpx; color: #999; }
.btn-upload { background: #1976D2; color: #fff; border-radius: 12rpx; margin-top: 20rpx; font-size: 32rpx; }
</style>
