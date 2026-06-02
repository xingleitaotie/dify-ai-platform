<template>
  <div class="top-nav">
    <div class="nav-left">
      <div class="breadcrumb-wrapper">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">
            <el-icon><HomeFilled /></el-icon>
            首页
          </el-breadcrumb-item>
          <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>
    </div>

    <div class="nav-right">
      <div class="notification-btn">
        <el-icon :size="20"><Bell /></el-icon>
      </div>

      <el-dropdown @command="handleCommand">
        <div class="user-info">
          <div class="avatar-glow">
            <el-avatar :size="36" :icon="UserFilled" />
          </div>
          <span>{{ userStore.username || '用户' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { HomeFilled, UserFilled, ArrowDown, Bell, User, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const currentTitle = computed(() => route.meta?.title || route.path.split('/')[1] || '首页')

const handleCommand = (command) => {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'profile') {
    ElMessage.info('功能开发中')
  }
}
</script>

<style scoped>
.top-nav {
  height: 64px;
  background: rgba(15, 25, 45, 0.6);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: relative;
  z-index: 10;
}

.breadcrumb-wrapper {
  background: rgba(255, 255, 255, 0.05);
  padding: 8px 16px;
  border-radius: 12px;
  backdrop-filter: blur(8px);
}

:deep(.el-breadcrumb) {
  font-size: 14px;
}

:deep(.el-breadcrumb__item) {
  color: #94a3b8;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #667eea;
  font-weight: 500;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.notification-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #94a3b8;
  position: relative;
}

.notification-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #a78bfa;
  transform: translateY(-2px);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 6px 16px 6px 8px;
  border-radius: 40px;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.user-info:hover {
  background: rgba(102, 126, 234, 0.15);
  border-color: #667eea;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.2);
}

/* 用户名样式 - 关键修复 */
.user-info span {
  color: #ffffff !important;
  font-weight: 500;
  font-size: 14px;
}

.user-info .el-icon {
  color: #94a3b8;
}

.user-info:hover .el-icon {
  color: #a78bfa;
}

/* 头像发光效果 */
.avatar-glow {
  border-radius: 50%;
  position: relative;
}

.avatar-glow::before {
  content: '';
  position: absolute;
  inset: -2px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.user-info:hover .avatar-glow::before {
  opacity: 1;
}

/* 头像内部图标颜色 */
:deep(.el-avatar svg) {
  color: #94a3b8;
}

.user-info:hover :deep(.el-avatar svg) {
  color: #a78bfa;
}

/* 下拉菜单样式优化 */
:deep(.el-dropdown-menu) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px !important;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.3);
}

:deep(.el-dropdown-menu__item) {
  color: #cbd5e6 !important;
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  padding: 10px 20px !important;
}

:deep(.el-dropdown-menu__item:hover) {
  background: #2a2f4a !important;
  color: #ffffff !important;
}

:deep(.el-dropdown-menu__item .el-icon) {
  color: #94a3b8;
}

:deep(.el-dropdown-menu__item:hover .el-icon) {
  color: #a78bfa;
}

/* 分割线样式 */
:deep(.el-dropdown-menu__item--divided) {
  border-top-color: #2a2f4a !important;
  margin-top: 6px;
  padding-top: 6px;
}
</style>