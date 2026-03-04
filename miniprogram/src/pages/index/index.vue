<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyFrames } from '../../api/index'

const frames = ref<any[]>([])
const loading = ref(false)

async function loadFrames() {
  loading.value = true
  try {
    frames.value = await getMyFrames()
  } catch (e) {
    uni.showToast({ title: '加载失败', icon: 'error' })
  } finally {
    loading.value = false
  }
}

onShow(() => loadFrames())

function goUpload(frame: any) {
  uni.navigateTo({ url: `/pages/upload/index?device_id=${frame.id}&frame_name=${frame.name}` })
}

function goBind() {
  uni.navigateTo({ url: '/pages/bind/index' })
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading">加载中...</view>

    <view v-else-if="frames.length === 0" class="empty">
      <text class="empty-text">还没有绑定相框</text>
      <button class="btn-primary" @tap="goBind">扫码绑定相框</button>
    </view>

    <view v-else class="frame-list">
      <view
        v-for="frame in frames"
        :key="frame.id"
        class="frame-card"
        @tap="goUpload(frame)"
      >
        <text class="frame-name">{{ frame.name }}</text>
        <text class="frame-id">ID: {{ frame.id.slice(0, 8) }}...</text>
        <text class="frame-arrow">上传照片 →</text>
      </view>

      <button class="btn-secondary" @tap="goBind">绑定新相框</button>
    </view>
  </view>
</template>

<style>
.page { padding: 20rpx; background: #f5f5f5; min-height: 100vh; }
.loading { text-align: center; padding: 100rpx; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; padding: 120rpx 40rpx; }
.empty-text { color: #999; font-size: 32rpx; margin-bottom: 40rpx; }
.frame-list { padding: 20rpx 0; }
.frame-card {
  background: #fff; border-radius: 16rpx; padding: 32rpx; margin-bottom: 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.08);
}
.frame-name { display: block; font-size: 34rpx; font-weight: bold; color: #333; margin-bottom: 8rpx; }
.frame-id { display: block; font-size: 24rpx; color: #999; margin-bottom: 16rpx; }
.frame-arrow { display: block; font-size: 28rpx; color: #1976D2; }
.btn-primary { background: #1976D2; color: #fff; border-radius: 12rpx; margin-top: 20rpx; }
.btn-secondary { background: #fff; color: #1976D2; border: 2rpx solid #1976D2; border-radius: 12rpx; margin-top: 20rpx; }
</style>
