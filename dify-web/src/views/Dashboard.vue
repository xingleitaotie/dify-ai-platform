<template>
  <div class="dashboard">
    <div class="dashboard-header fade-in-up">
      <div class="header-content">
        <h1>
          <span class="welcome-text">欢迎回来，</span>
          <span class="username-glow">{{ userStore.username }}</span>
        </h1>
        <p>✨ Dify AI 平台为您提供智能应用开发服务</p>
      </div>
      <div class="header-decoration">
        <div class="orb"></div>
      </div>
    </div>

    <el-row :gutter="24" class="stats-row">
      <el-col :span="6" v-for="(stat, index) in statsCards" :key="index">
        <div class="stat-card glass-card" :style="{ animationDelay: `${index * 0.1}s` }">
          <div class="stat-icon" :class="stat.iconClass">
            <el-icon :size="28"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
            <div v-if="stat.sub" class="stat-sub">{{ stat.sub }}</div>
          </div>
          <div class="stat-glow"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="24">
      <el-col :span="12">
        <div class="feature-card glass-card slide-in-left">
          <div class="card-header">
            <span class="card-title">🚀 快速开始</span>
          </div>
          <div class="quick-actions">
            <button class="action-btn primary" @click="goToChat">
              <el-icon><ChatDotRound /></el-icon>
              开始对话
            </button>
            <button class="action-btn" @click="goToKnowledgeBase">
              <el-icon><Document /></el-icon>
              上传文档
            </button>
            <button class="action-btn" @click="goToAgent">
              <el-icon><Cpu /></el-icon>
              创建Agent
            </button>
          </div>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="feature-card glass-card slide-in-right">
          <div class="card-header">
            <span class="card-title">📊 最近活动</span>
          </div>
          <div class="activities">
            <div v-if="recentActivities.length === 0" class="empty-activities">
              <el-icon><Bell /></el-icon>
              <span>暂无活动记录</span>
            </div>
            <div v-for="(activity, idx) in recentActivities" :key="activity.id" class="activity-item" :style="{ animationDelay: `${idx * 0.05}s` }">
              <div class="activity-icon">
                <el-icon><component :is="getActivityIcon(activity.type)" /></el-icon>
              </div>
              <div class="activity-content">
                <span>{{ activity.content }}</span>
                <span class="activity-time">{{ activity.time }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="app-management glass-card fade-in-up">
      <div class="card-header">
        <span class="card-title">📱 我的应用</span>
        <button class="create-btn" @click="showCreateApp = true">
          <el-icon><Plus /></el-icon>
          创建应用
        </button>
      </div>
      <el-table :data="userStore.apps" stripe class="custom-table">
        <el-table-column prop="appName" label="应用名称" />
        <el-table-column prop="appKey" label="App Key" />
        <el-table-column prop="appSecret" label="App Secret" show-overflow-tooltip />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <span class="status-badge" :class="row.status === 1 ? 'active' : 'inactive'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <button class="delete-btn" @click="handleDeleteApp(row.id)">
              <el-icon><Delete /></el-icon>
            </button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建应用对话框 -->
    <el-dialog v-model="showCreateApp" title="创建应用" class="custom-dialog">
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
import { agentApi } from '@/api/agent'
import { functionApi } from '@/api/function'
import { chatApi } from '@/api/chat'
import { ragApi } from '@/api/rag'

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
  { icon: 'Collection', iconClass: 'kb', value: statsData.knowledgeBases, label: '知识库数量', sub: `文档: ${statsData.documents} | 分块: ${statsData.documentChunks}` },
  { icon: 'Cpu', iconClass: 'agent', value: statsData.agents, label: 'Agent数量', sub: `已启用: ${statsData.enabledAgents}` },
  { icon: 'Tools', iconClass: 'tool', value: statsData.tools, label: '工具数量', sub: null }
])

const recentActivities = ref([])

// 获取图标
const getActivityIcon = (type) => {
  const icons = { agent: 'Cpu', kb: 'Collection', app: 'Plus' }
  return icons[type] || 'Bell'
}

// API 调用函数（保持原有逻辑，只简化展示）
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
    recentActivities.value = activities.sort((a, b) => b.timestamp - a.timestamp).slice(0, 10)
  } catch (error) {
    console.error('加载最近活动失败', error)
  }
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const diff = Date.now() - date.getTime()
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
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
  animation: fadeIn 0.6s ease-out;
}

/* 头部区域 */
.dashboard-header {
  position: relative;
  margin-bottom: 32px;
  padding: 32px;
  background: linear-gradient(135deg, #1a1f3a 0%, #0f1228 100%) !important;  /* 改成实色渐变 */
  border-radius: 24px;
  overflow: hidden;
  border: 1px solid #2a2f4a;  /* 加个边框更明显 */
}

.header-content h1 {
  font-size: 32px;
  margin-bottom: 8px;
  color: #ffffff !important;
}

.welcome-text {
  color: #cbd5e6 !important;  /* 亮灰色，不是深灰 */
}

.username-glow {
  color: #667eea !important;  /* 保持品牌色但更亮 */
}

.header-content p {
  color: #cbd5e6 !important;
}

.header-decoration {
  position: absolute;
  right: -50px;
  top: -50px;
  width: 200px;
  height: 200px;
}

.orb {
  width: 100%;
  height: 100%;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.3), transparent);
  border-radius: 50%;
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  position: relative;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  animation: fadeInUp 0.6s ease-out both;
}

.stat-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  transition: all 0.3s ease;
}

.stat-icon.chat { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-icon.kb { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.stat-icon.agent { background: linear-gradient(135deg, #10b981, #3b82f6); }
.stat-icon.tool { background: linear-gradient(135deg, #8b5cf6, #ec4899); }

.stat-card:hover .stat-icon {
  transform: scale(1.1) rotate(5deg);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700 !important;
  color: #ffffff !important;
  letter-spacing: -0.5px;
  background: none !important;
  -webkit-background-clip: unset !important;
  background-clip: unset !important;
}

.stat-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.stat-sub {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 4px;
}

.stat-glow {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--primary-gradient);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.stat-card:hover .stat-glow {
  transform: scaleX(1);
}

/* 功能卡片 */
.feature-card {
  padding: 24px;
  height: 280px;
  animation: fadeInUp 0.6s ease-out both;
}

.feature-card:first-child { animation-delay: 0.2s; }
.feature-card:last-child { animation-delay: 0.3s; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  background: var(--primary-gradient);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.quick-actions {
  display: flex;
  gap: 16px;
  flex-direction: column;
}

.action-btn {
  padding: 14px 24px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-primary);
  border: 1px solid var(--glass-border);
}

.action-btn.primary {
  background: var(--primary-gradient);
  border: none;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.3);
}

.activities {
  max-height: 200px;
  overflow-y: auto;
}

.activity-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  margin-bottom: 8px;
  transition: all 0.3s ease;
  animation: fadeInUp 0.4s ease-out both;
}

.activity-item:hover {
  background: rgba(102, 126, 234, 0.1);
  transform: translateX(4px);
}

.activity-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: rgba(102, 126, 234, 0.2);
  color: var(--primary-color);
}

.activity-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.activity-time {
  font-size: 12px;
  color: var(--text-muted);
}

.empty-activities {
  text-align: center;
  padding: 40px;
  color: var(--text-muted);
}

/* 应用管理 */
.app-management {
  margin-top: 24px;
  padding: 24px;
  animation: fadeInUp 0.6s ease-out 0.4s both;
}

.create-btn {
  padding: 8px 20px;
  background: var(--primary-gradient);
  border: none;
  border-radius: 8px;
  color: white;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.delete-btn {
  background: none;
  border: none;
  color: var(--danger);
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.delete-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  transform: scale(1.1);
}

/* 状态标签 */
.status-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.2);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.5);
}

.status-badge.inactive {
  background: rgba(100, 116, 139, 0.2);
  color: var(--text-muted);
  border: 1px solid rgba(100, 116, 139, 0.5);
}

/* 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.fade-in-up {
  animation: fadeInUp 0.6s ease-out;
}

.slide-in-left {
  animation: fadeInUp 0.6s ease-out;
}

.slide-in-right {
  animation: fadeInUp 0.6s ease-out 0.1s;
}
</style>