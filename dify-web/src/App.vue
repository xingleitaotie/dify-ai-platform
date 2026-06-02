<template>
  <div class="app-container">
    <div v-if="showGlow" class="glow-cursor" :style="{ left: mouseX + 'px', top: mouseY + 'px' }"></div>
    <router-view v-slot="{ Component, route }">
      <transition name="fade" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const showGlow = ref(true)
const mouseX = ref(0)
const mouseY = ref(0)

const handleMouseMove = (e) => {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
}

// 捕获路由错误
router.onError((error) => {
  console.error('路由错误:', error)
  if (error.message?.includes('Failed to fetch') || error.message?.includes('NetworkError')) {
    ElMessage.error('网络连接失败，请检查后端服务')
  }
})

onMounted(() => {
  console.log('App mounted, current route:', route.path)
  window.addEventListener('mousemove', handleMouseMove)

  // 初始化认证状态
  userStore.initAuth()
})

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  width: 100%;
}

.app-container {
  position: relative;
  height: 100%;
  width: 100%;
  overflow: hidden;
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>