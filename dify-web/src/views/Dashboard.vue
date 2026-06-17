<template>
  <div class="dashboard">
    <!-- 欢迎头部 -->
    <div class="dashboard-header">
      <div class="header-content">
        <h1>
          <span class="welcome-text">欢迎回来，</span>
          <span class="username">{{ userStore.username }}</span>
        </h1>
        <p>✨ Dify AI 平台为您提供智能应用开发服务</p>
      </div>
      <div class="header-decoration">
        <div class="orb"></div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6" v-for="(stat, index) in statsCards" :key="index">
        <div class="stat-card" :style="{ animationDelay: `${index * 0.05}s` }">
          <div class="stat-icon" :class="stat.iconClass">
            <el-icon :size="24"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
            <div v-if="stat.sub" class="stat-sub">{{ stat.sub }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作和最近活动 -->
    <el-row :gutter="20" class="action-row">
      <el-col :xs="24" :md="12">
        <div class="feature-card">
          <div class="card-header">
            <span class="card-title">🚀 快速开始</span>
          </div>
          <div class="quick-actions">
            <button class="action-btn primary" @click="goToChat">
              <el-icon><ChatDotRound /></el-icon>
              <span>开始对话</span>
            </button>
            <button class="action-btn" @click="goToKnowledgeBase">
              <el-icon><Document /></el-icon>
              <span>上传文档</span>
            </button>
            <button class="action-btn" @click="goToAgent">
              <el-icon><Cpu /></el-icon>
              <span>创建Agent</span>
            </button>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :md="12">
        <div class="feature-card">
          <div class="card-header">
            <span class="card-title">📊 最近活动</span>
          </div>
          <div class="activities">
            <div v-if="recentActivities.length === 0" class="empty-activities">
              <el-icon><Bell /></el-icon>
              <span>暂无活动记录</span>
            </div>
            <div v-for="(activity, idx) in recentActivities" :key="activity.id" class="activity-item">
              <div class="activity-icon" :style="{ backgroundColor: getActivityColor(activity.type) }">
                <el-icon size="14"><component :is="getActivityIcon(activity.type)" /></el-icon>
              </div>
              <div class="activity-content">
                <span class="activity-text">{{ activity.content }}</span>
                <span class="activity-time">{{ activity.time }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 应用管理 -->
    <div class="app-card">
      <div class="card-header">
        <span class="card-title">📱 我的应用</span>
        <button class="create-btn" @click="showCreateApp = true">
          <el-icon><Plus /></el-icon>
          创建应用
        </button>
      </div>
      <el-table :data="userStore.apps" stripe class="custom-table" empty-text="暂无应用，点击上方按钮创建">
        <el-table-column prop="appName" label="应用名称" min-width="150" />
        <el-table-column prop="appKey" label="App Key" min-width="200" show-overflow-tooltip />
        <el-table-column prop="appSecret" label="App Secret" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status === 1 ? 'active' : 'inactive'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="70" align="center">
          <template #default="{ row }">
            <button class="delete-btn" @click="handleDeleteApp(row.id)">
              <el-icon><Delete /></el-icon>
            </button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建应用对话框 -->
    <el-dialog v-model="showCreateApp" title="创建应用" width="400px" class="custom-dialog">
      <el-form :model="newApp" :rules="appRules" ref="appFormRef">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="newApp.appName" placeholder="请输入应用名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateApp = false">取消</el-button>
        <el-button type="primary" @click="createApp" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onActivated, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Document, Cpu, Plus, Bell, Delete, Tools, Collection } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {chatApi, ragApi, functionApi, agentApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const showCreateApp = ref(false)
const creating = ref(false)
const appFormRef = ref()

const newApp = reactive({ appName: '' })

const appRules = {
  appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }]
}

// 统计数据
const statsData = reactive({
  conversations: 0,
  knowledgeBases: 0,
  documents: 0,
  documentChunks: 0,
  agents: 0,
  enabledAgents: 0,
  tools: 0
})

const statsCards = computed(() => [
  { icon: 'ChatDotRound', iconClass: 'chat', value: statsData.conversations, label: '总对话次数', sub: null },
  { icon: 'Collection', iconClass: 'kb', value: statsData.knowledgeBases, label: '知识库数量', sub: `📄 ${statsData.documents} 文档 | 🧩 ${statsData.documentChunks} 分块` },
  { icon: 'Cpu', iconClass: 'agent', value: statsData.agents, label: 'Agent数量', sub: `✅ 已启用: ${statsData.enabledAgents}` },
  { icon: 'Tools', iconClass: 'tool', value: statsData.tools, label: '工具数量', sub: null }
])

const recentActivities = ref([])

const getActivityIcon = (type) => {
  const icons = { agent: 'Cpu', kb: 'Collection', app: 'Plus' }
  return icons[type] || 'Bell'
}

const getActivityColor = (type) => {
  const colors = { agent: '#10b981', kb: '#f59e0b', app: '#667eea' }
  return colors[type] || '#64748b'
}

const loadStats = async () => {
  try {
    const [convRes, kbRes, agentRes, toolRes] = await Promise.all([
      chatApi.getStats().catch(() => ({ code: 200, data: { conversationCount: 0 } })),
      ragApi.getKnowledgeBases().catch(() => ({ code: 200, data: [] })),
      agentApi.list().catch(() => ({ code: 200, data: [] })),
      functionApi.getFunctionList().catch(() => ({ code: 200, data: [] }))
    ])

    statsData.conversations = convRes.code === 200 ? (convRes.data?.conversationCount || 0) : 0

    if (kbRes.code === 200 && kbRes.data) {
      statsData.knowledgeBases = kbRes.data.length
      let totalDocs = 0, totalChunks = 0
      kbRes.data.forEach(kb => {
        totalDocs += kb.documents || 0
        totalChunks += kb.chunkCount || 0
      })
      statsData.documents = totalDocs
      statsData.documentChunks = totalChunks
    }

    if (agentRes.code === 200 && agentRes.data) {
      statsData.agents = agentRes.data.length
      statsData.enabledAgents = agentRes.data.filter(a => a.isEnabled === 1 || a.isEnabled === true).length
    }

    statsData.tools = (toolRes.code === 200 && toolRes.data) ? toolRes.data.length : 0
  } catch (error) {
    console.error('加载统计数据失败', error)
  }
}

const loadRecentActivities = async () => {
  try {
    const activities = []
    const agentRes = await agentApi.list()
    if (agentRes.code === 200 && agentRes.data?.length) {
      [...agentRes.data].sort((a, b) => new Date(b.createTime) - new Date(a.createTime)).slice(0, 3).forEach(agent => {
        if (agent.createTime) activities.push({ id: `agent_${agent.id}`, type: 'agent', content: `创建了Agent "${agent.agentName}"`, time: formatTime(agent.createTime), timestamp: new Date(agent.createTime).getTime() })
      })
    }
    const kbRes = await ragApi.getKnowledgeBases()
    if (kbRes.code === 200 && kbRes.data?.length) {
      [...kbRes.data].sort((a, b) => new Date(b.createTime) - new Date(a.createTime)).slice(0, 3).forEach(kb => {
        if (kb.createTime) activities.push({ id: `kb_${kb.id}`, type: 'kb', content: `创建了知识库 "${kb.name}"`, time: formatTime(kb.createTime), timestamp: new Date(kb.createTime).getTime() })
      })
    }
    if (userStore.apps?.length) {
      [...userStore.apps].sort((a, b) => new Date(b.createTime) - new Date(a.createTime)).slice(0, 3).forEach(app => {
        if (app.createTime) activities.push({ id: `app_${app.id}`, type: 'app', content: `创建了应用 "${app.appName}"`, time: formatTime(app.createTime), timestamp: new Date(app.createTime).getTime() })
      })
    }
    recentActivities.value = activities.sort((a, b) => b.timestamp - a.timestamp).slice(0, 6)
  } catch (error) {
    console.error('加载最近活动失败', error)
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const diff = Date.now() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return `${date.getMonth() + 1}-${date.getDate()}`
}

const goToChat = () => router.push('/chat')
const goToKnowledgeBase = () => router.push('/knowledge-base')
const goToAgent = () => router.push('/agent')

const createApp = async () => {
  try {
    await appFormRef.value.validate()
    creating.value = true
    await userStore.createApp(newApp.appName)
    ElMessage.success('创建成功')
    showCreateApp.value = false
    newApp.appName = ''
    await loadRecentActivities()
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

const handleDeleteApp = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此应用吗？', '提示', { type: 'warning' })
    await userStore.deleteApp(id)
    ElMessage.success('删除成功')
    await loadRecentActivities()
  } catch {}
}

onMounted(async () => {
  await userStore.loadApps()
  await Promise.all([loadStats(), loadRecentActivities()])
})

onActivated(async () => {
  await userStore.loadApps()
  await Promise.all([loadStats(), loadRecentActivities()])
})
</script>

<style scoped>
.dashboard {
  padding: 24px;
  min-height: 100%;
  background: #0a0e27;
}

/* ========== 欢迎头部 ========== */
.dashboard-header {
  position: relative;
  margin-bottom: 28px;
  padding: 28px 32px;
  background: linear-gradient(135deg, #1a1f3a 0%, #0f1228 100%);
  border-radius: 20px;
  border: 1px solid #2a2f4a;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h1 {
  font-size: 28px;
  margin-bottom: 6px;
  color: #ffffff;
}

.welcome-text {
  color: #94a3b8;
  font-weight: 400;
}

.username {
  background: linear-gradient(135deg, #667eea, #764ba2);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-weight: 600;
}

.header-content p {
  color: #64748b;
  font-size: 14px;
}

.header-decoration {
  position: absolute;
  right: -30px;
  top: -30px;
  width: 150px;
  height: 150px;
}

.orb {
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.15), transparent);
  border-radius: 50%;
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ========== 统计卡片 ========== */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all 0.3s ease;
  animation: fadeInUp 0.4s ease-out both;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: #667eea;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.15);
}

.stat-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  flex-shrink: 0;
}

.stat-icon.chat { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-icon.kb { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.stat-icon.agent { background: linear-gradient(135deg, #10b981, #3b82f6); }
.stat-icon.tool { background: linear-gradient(135deg, #8b5cf6, #ec4899); }

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 4px;
}

.stat-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
}

/* ========== 功能卡片 ========== */
.action-row {
  margin-bottom: 20px;
}

.feature-card {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 16px;
  padding: 20px;
  height: 260px;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  animation: fadeInUp 0.4s ease-out both;
}

.feature-card:hover {
  border-color: #667eea;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

/* 快捷操作按钮 */
.quick-actions {
  display: flex;
  gap: 14px;
  flex-direction: column;
  flex: 1;
}

.action-btn {
  padding: 12px 20px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #0f1228;
  color: #cbd5e6;
  border: 1px solid #2a2f4a;
}

.action-btn.primary {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  color: #ffffff;
}

.action-btn:hover {
  transform: translateY(-2px);
}

.action-btn.primary:hover {
  box-shadow: 0 6px 16px rgba(102, 126, 234, 0.4);
}

.action-btn:not(.primary):hover {
  border-color: #667eea;
  color: #a78bfa;
}

/* 最近活动 */
.activities {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #2a2f4a;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.activity-text {
  font-size: 13px;
  color: #cbd5e6;
}

.activity-time {
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
}

.empty-activities {
  text-align: center;
  padding: 40px 20px;
  color: #64748b;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

/* ========== 应用管理卡片 ========== */
.app-card {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 16px;
  padding: 20px;
  margin-top: 0;
  animation: fadeInUp 0.4s ease-out both;
}

.create-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  border-radius: 10px;
  color: #ffffff;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.create-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.delete-btn {
  background: none;
  border: none;
  color: #f87171;
  cursor: pointer;
  padding: 6px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.delete-btn:hover {
  background: rgba(248, 113, 113, 0.15);
  transform: scale(1.05);
}

/* 表格样式 */
.custom-table :deep(.el-table) {
  background: transparent;
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}

.custom-table :deep(.el-table__header th) {
  background: #0f1228;
  color: #94a3b8;
  font-weight: 500;
  font-size: 13px;
  border-bottom: 1px solid #2a2f4a;
}

.custom-table :deep(.el-table__body td) {
  color: #cbd5e6;
  border-bottom: 1px solid #2a2f4a;
}

.custom-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.05);
}

/* 状态标签 */
.status-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.15);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.3);
}

.status-badge.inactive {
  background: rgba(100, 116, 139, 0.15);
  color: #94a3b8;
  border: 1px solid rgba(100, 116, 139, 0.3);
}

/* ========== 动画 ========== */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ========== 对话框样式 ========== */
:deep(.el-dialog) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 16px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-dialog__title) {
  color: #ffffff;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-form-item__label) {
  color: #cbd5e6;
}

:deep(.el-input__wrapper) {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
}

:deep(.el-input__inner) {
  color: #ffffff;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .dashboard {
    padding: 16px;
  }

  .dashboard-header {
    padding: 20px;
  }

  .header-content h1 {
    font-size: 22px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }

  .feature-card {
    height: auto;
    margin-bottom: 16px;
  }

  .action-btn {
    padding: 10px 16px;
  }

  .activity-content {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>