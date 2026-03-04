<script setup lang="ts">
import { ref } from 'vue'
import { bindFrame } from '../../api/index'

const binding = ref(false)
const result = ref('')

async function scanAndBind() {
  try {
    const { result: scanResult } = await scanQrCode()
    // 解析 photoframe://bind?qr_token=xxx 格式
    const url = new URL(scanResult)
    const qrToken = url.searchParams.get('qr_token')
    if (!qrToken) {
      uni.showToast({ title: '无效的二维码', icon: 'error' })
      return
    }

    binding.value = true
    const data = await bindFrame(qrToken)
    result.value = `已绑定：${data.device_name}`
    uni.showToast({ title: '绑定成功！', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '绑定失败', icon: 'error' })
  } finally {
    binding.value = false
  }
}

function scanQrCode(): Promise<{ result: string }> {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      scanType: ['qrCode'],
      success: resolve,
      fail: reject
    })
  })
}
</script>

<template>
  <view class="page">
    <view class="icon-wrap">
      <text class="icon">📷</text>
    </view>

    <text class="title">扫码绑定相框</text>
    <text class="hint">打开相框 App，扫描屏幕上显示的二维码</text>

    <button
      class="btn-scan"
      :disabled="binding"
      @tap="scanAndBind"
    >
      {{ binding ? '绑定中...' : '扫描二维码' }}
    </button>

    <text v-if="result" class="result">{{ result }}</text>
  </view>
</template>

<style>
.page { display: flex; flex-direction: column; align-items: center; padding: 80rpx 40rpx; min-height: 100vh; background: #f5f5f5; }
.icon-wrap { margin-bottom: 40rpx; }
.icon { font-size: 120rpx; }
.title { font-size: 40rpx; font-weight: bold; color: #333; margin-bottom: 20rpx; }
.hint { font-size: 28rpx; color: #999; text-align: center; margin-bottom: 60rpx; line-height: 1.6; }
.btn-scan { background: #1976D2; color: #fff; border-radius: 12rpx; width: 500rpx; font-size: 34rpx; }
.result { margin-top: 40rpx; font-size: 28rpx; color: #4CAF50; }
</style>
