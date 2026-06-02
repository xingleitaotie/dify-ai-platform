<template>
  <div class="layout-container">
    <el-aside width="220px" class="aside">
      <div class="logo">Dify AI 平台</div>
      <el-menu router default-active="/chat" class="menu">
        <el-menu-item index="/home">首页</el-menu-item>
        <el-menu-item index="/chat">对话</el-menu-item>
        <el-menu-item index="/knowledge">知识库</el-menu-item>
        <el-menu-item index="/function">工具管理</el-menu-item>
        <el-menu-item index="/agent/list">Agent 管理</el-menu-item>
        <el-menu-item index="/setting">系统设置</el-menu-item>
      </el-menu>
    </el-aside>

    <div class="main">
      <el-header class="header">
        <span>智能对话平台</span>
        <el-button link @click="logout">退出登录</el-button>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
const router = useRouter()

const logout = () => {
  localStorage.clear()
  ElMessage.success('退出成功')
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
}
.aside {
  background: #002140;
  color: #fff;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 16px;
  font-weight: bold;
}
.menu {
  border-right: none;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px #eee;
}
.content {
  flex: 1;
  background: #f5f5f5;
  overflow: auto;
  padding: 20px;
}
</style>