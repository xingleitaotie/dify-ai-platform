<template>
  <div class="main-layout">
    <Sidebar />
    <div class="main-content">
      <TopNav />
      <div class="content-view">
        <router-view v-slot="{ Component, route }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import Sidebar from '@/views/components/Sidebar.vue'
import TopNav from '@/views/components/TopNav.vue'
</script>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  width: 100%;
  overflow: hidden;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f3f4f6;
}

.content-view {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>