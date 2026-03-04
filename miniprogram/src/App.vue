<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { wxLogin } from './api/index'

onLaunch(async () => {
  const token = uni.getStorageSync('token')
  if (!token) {
    try {
      await wxLogin()
    } catch (e) {
      console.warn('微信登录失败', e)
    }
  }
})
</script>

<template>
  <router-view />
</template>
