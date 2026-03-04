<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyFrames, getPhotos, deletePhoto } from '../../api/index'

const frames = ref<any[]>([])
const photos = ref<any[]>([])
const selectedDeviceId = ref('')
const loading = ref(false)

onShow(async () => {
  frames.value = await getMyFrames()
  if (frames.value.length > 0 && !selectedDeviceId.value) {
    selectedDeviceId.value = frames.value[0].id
    await loadPhotos()
  }
})

async function loadPhotos() {
  if (!selectedDeviceId.value) return
  loading.value = true
  try {
    photos.value = await getPhotos(selectedDeviceId.value)
  } catch {
    uni.showToast({ title: '加载失败', icon: 'error' })
  } finally {
    loading.value = false
  }
}

async function onDeletePhoto(photo: any) {
  const confirmed = await new Promise<boolean>(resolve => {
    uni.showModal({
      title: '删除照片',
      content: '确认删除这张照片？',
      success: ({ confirm }) => resolve(confirm)
    })
  })
  if (!confirmed) return

  try {
    await deletePhoto(photo.id)
    photos.value = photos.value.filter(p => p.id !== photo.id)
    uni.showToast({ title: '已删除', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '删除失败', icon: 'error' })
  }
}

function formatDate(str: string): string {
  return str ? str.slice(0, 10) : ''
}
</script>

<template>
  <view class="page">
    <!-- 相框选择 -->
    <view v-if="frames.length > 1" class="frame-picker">
      <picker :range="frames.map(f => f.name)" @change="(e: any) => { selectedDeviceId = frames[e.detail.value].id; loadPhotos() }">
        <view class="picker-label">选择相框：{{ frames.find(f => f.id === selectedDeviceId)?.name }}</view>
      </picker>
    </view>

    <view v-if="loading" class="loading">加载中...</view>

    <view v-else-if="photos.length === 0" class="empty">
      <text>还没有照片</text>
    </view>

    <view v-else class="photo-grid">
      <view v-for="photo in photos" :key="photo.id" class="photo-item">
        <image :src="photo.url" mode="aspectFill" class="photo-thumb" />
        <view class="photo-info">
          <text class="photo-date">{{ formatDate(photo.taken_at || photo.uploaded_at) }}</text>
          <text class="photo-uploader">{{ photo.uploader_name || '未知' }}</text>
        </view>
        <view class="delete-btn" @tap="onDeletePhoto(photo)">×</view>
      </view>
    </view>
  </view>
</template>

<style>
.page { padding: 20rpx; background: #f5f5f5; min-height: 100vh; }
.frame-picker { background: #fff; padding: 24rpx; border-radius: 12rpx; margin-bottom: 20rpx; }
.picker-label { font-size: 28rpx; color: #333; }
.loading, .empty { text-align: center; padding: 100rpx; color: #999; }
.photo-grid { display: flex; flex-wrap: wrap; gap: 10rpx; }
.photo-item { position: relative; width: calc((100% - 20rpx) / 3); aspect-ratio: 1; border-radius: 8rpx; overflow: hidden; background: #fff; }
.photo-thumb { width: 100%; height: 100%; }
.photo-info { position: absolute; bottom: 0; left: 0; right: 0; background: linear-gradient(transparent, rgba(0,0,0,0.6)); padding: 8rpx; }
.photo-date { display: block; font-size: 20rpx; color: #fff; }
.photo-uploader { display: block; font-size: 18rpx; color: rgba(255,255,255,0.8); }
.delete-btn { position: absolute; top: 6rpx; right: 6rpx; width: 44rpx; height: 44rpx; background: rgba(0,0,0,0.5); border-radius: 50%; color: #fff; text-align: center; line-height: 44rpx; font-size: 32rpx; }
</style>
