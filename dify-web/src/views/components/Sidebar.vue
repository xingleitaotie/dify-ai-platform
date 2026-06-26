<template>
  <div class="sidebar">
    <div class="logo" @click="goTo('/dashboard')">
      <div class="logo-icon">
        <el-icon :size="32"><DataAnalysis /></el-icon>
      </div>
      <span class="logo-text">Dify AI</span>
    </div>

    <div class="menu-list">
      <div
          v-for="menu in sidebarMenus"
          :key="menu.id"
          class="menu-item-wrapper"
      >
        <div
            class="menu-item"
            :class="{ active: currentPath === menu.path }"
            @click="goTo(menu.path)"
        >
          <div class="menu-icon">
            <el-icon><component :is="getIcon(menu.icon)" /></el-icon>
          </div>
          <span>{{ menu.title }}</span>
          <div class="menu-glow"></div>
        </div>
      </div>
    </div>

    <div class="user-info" @click="logout">
      <div class="user-icon">
        <el-icon><User /></el-icon>
      </div>
      <span>退出登录</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  DataAnalysis,
  HomeFilled,
  ChatDotRound,
  Document,
  Cpu,
  Setting,
  User,
  Share,
  Edit,
  Tools,
  Menu,
  Grid
} from '@element-plus/icons-vue'
import { useMenuStore } from '@/stores/menu'

const route = useRoute()
const router = useRouter()
const menuStore = useMenuStore()

const currentPath = computed(() => route.path)

const sidebarMenus = computed(() => menuStore.sidebarMenus)

const iconMap = {
  HomeFilled,
  ChatDotRound,
  Document,
  Cpu,
  Setting,
  Share,
  Edit,
  Tools,
  Menu,
  Grid
}

const getIcon = (iconName) => {
  return iconMap[iconName] || HomeFilled
}

const goTo = (path) => {
  router.push(path)
}

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'custom-message-box'
    })
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('userRole')
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {}
}

onMounted(() => {
  if (!localStorage.getItem('userRole')) {
    localStorage.setItem('userRole', 'admin')
  }
})
</script>

<style scoped>
.sidebar {
  width: 260px;
  background: var(--bg-sidebar);
  backdrop-filter: blur(12px);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--primary-color), transparent);
}

.logo {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid var(--glass-border);
  cursor: pointer;
  position: relative;
  transition: all 0.3s ease;
}

.logo:hover {
  transform: scale(1.02);
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: var(--primary-gradient);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(102, 126, 234, 0.7);
  }
  50% {
    box-shadow: 0 0 0 10px rgba(102, 126, 234, 0);
  }
}

.logo-text {
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-size: 22px;
}

.menu-list {
  flex: 1;
  padding: 24px 16px;
  overflow-y: auto;
}

.menu-item-wrapper {
  margin-bottom: 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  color: var(--text-secondary);
  font-size: 14px;
  position: relative;
  overflow: hidden;
}

.menu-item:hover {
  background: rgba(102, 126, 234, 0.1);
  color: var(--text-primary);
}

.menu-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(118, 75, 162, 0.2));
  color: var(--primary-color);
  border: 1px solid var(--border-glow);
  box-shadow: 0 0 15px rgba(102, 126, 234, 0.3);
}

.menu-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.menu-item.active .menu-icon {
  background: var(--primary-gradient);
  color: white;
}

.menu-glow {
  position: absolute;
  right: 0;
  width: 3px;
  height: 0;
  background: var(--primary-gradient);
  transition: height 0.3s ease;
}

.menu-item.active .menu-glow {
  height: 60%;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin: 16px;
  border-radius: 12px;
  cursor: pointer;
  color: var(--danger);
  transition: all 0.3s ease;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.user-info:hover {
  background: rgba(239, 68, 68, 0.2);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
}

.user-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.2);
}
</style>
