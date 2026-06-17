<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <div class="chat-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <div class="logo" @click="newChat">
          <el-icon :size="24"><ChatDotRound /></el-icon>
          <span>新对话</span>
        </div>
        <el-button text class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon><DArrowLeft /></el-icon>
        </el-button>
      </div>

      <div class="chat-history">
        <div
            v-for="chat in chatHistory"
            :key="chat.id"
            class="chat-item"
            :class="{ active: currentSessionId === chat.id }"
            @click="switchChat(chat.id)"
        >
          <el-icon><ChatLineRound /></el-icon>
          <span>{{ chat.title }}</span>
          <el-button text @click.stop="deleteChat(chat.id)">
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div v-if="chatHistory.length === 0" class="empty-history">
          <el-empty description="暂无对话历史" :image-size="60" />
        </div>
      </div>
    </div>

    <!-- 主聊天区域 -->
    <div class="chat-main">
      <div class="chat-header">
        <div class="header-left">
          <div class="model-badge" v-if="currentChatModelInfo">
            <el-icon><Cpu /></el-icon>
            <span>{{ currentChatModelInfo.modelName || currentChatModelInfo.modelKey }}</span>
          </div>
          <div class="template-badge" v-if="showTemplateInfo && currentTemplateName">
            <el-icon><Document /></el-icon>
            <span>{{ currentTemplateName }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-tooltip content="高级设置" placement="bottom">
            <el-button text @click="settingsVisible = true" class="settings-btn">
              <el-icon><Setting /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <!-- 消息区域 -->
      <div class="messages-container" ref="messagesContainer">
        <!-- 欢迎屏幕 -->
        <div v-if="messages.length === 0" class="welcome-screen">
          <div class="welcome-icon">🤖</div>
          <h1>Dify AI 助手</h1>
          <p>你好！我是 Dify AI 助手，有什么可以帮你的吗？</p>
          <div class="suggestions">
            <div
                v-for="suggestion in suggestions"
                :key="suggestion.text"
                class="suggestion-item"
                @click="sendMessage(suggestion.text)"
            >
              {{ suggestion.text }}
            </div>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="index" class="message" :class="msg.role">
          <div class="message-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-name">{{ msg.role === 'user' ? '我' : 'Dify AI' }}</span>
              <span class="message-time">{{ msg.time }}</span>
              <el-button text size="small" class="copy-btn" @click="copyMessage(msg.rawContent)">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
            <div class="message-text">
              <div v-if="msg.thinkContent" class="think-block">
                <el-collapse>
                  <el-collapse-item title="查看思考过程">
                    <MarkdownRenderer :content="msg.thinkContent" />
                  </el-collapse-item>
                </el-collapse>
              </div>
              <MarkdownRenderer :content="msg.displayContent" />
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="isStreaming" class="message assistant">
          <div class="message-avatar">🤖</div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-name">Dify AI</span>
              <span class="message-time">正在生成...</span>
            </div>
            <div class="message-text">
              <div class="loading-dots"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-container">
        <div class="input-actions">
          <el-button text size="small" @click="clearChat" :disabled="messages.length === 0">
            <el-icon><Delete /></el-icon>清空对话
          </el-button>
          <el-button v-if="manualTemplateEnabled" text size="small" @click="templateSelectVisible = true">
            <el-icon><Grid /></el-icon>切换模板
          </el-button>
        </div>
        <div class="input-wrapper">
          <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="1"
              :placeholder="inputPlaceholder"
              @keydown.enter.prevent="handleEnter"
              resize="none"
              class="chat-input"
          />
          <el-button
              type="primary"
              :loading="isStreaming"
              :disabled="!inputMessage.trim() || !currentChatModelId"
              @click="sendMessage()"
              class="send-btn"
          >
            <el-icon><Promotion /></el-icon>发送
          </el-button>
          <el-button v-if="isStreaming" type="warning" @click="stopStreaming" class="stop-btn">
            <el-icon><Close /></el-icon>停止
          </el-button>
        </div>
      </div>
    </div>

    <!-- 高级设置对话框 -->
    <el-dialog v-model="settingsVisible" title="高级设置" width="400px">
      <el-form>
        <el-form-item label="显示模板信息">
          <el-switch v-model="showTemplateInfo" />
          <div class="form-tip">开启后会在顶部显示当前使用的模板名称</div>
        </el-form-item>
        <el-form-item label="手动选择模板">
          <el-switch v-model="manualTemplateEnabled" @change="onManualTemplateToggle" />
          <div class="form-tip">开启后可手动选择对话模板，关闭后自动智能匹配</div>
        </el-form-item>
        <el-form-item label="当前模板" v-if="manualTemplateEnabled">
          <el-select
              v-model="selectedTemplateId"
              placeholder="选择对话模板"
              filterable
              :loading="loadingTemplates"
              @change="onTemplateChange"
          >
            <el-option v-for="tpl in availableTemplates" :key="tpl.id" :label="tpl.name" :value="tpl.id">
              <div class="template-option">
                <span>{{ tpl.name }}</span>
                <el-tag size="small" :type="getTemplateTagType(tpl.type)">{{ tpl.type }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- 模板选择对话框 -->
    <el-dialog v-model="templateSelectVisible" title="选择对话模式" width="500px">
      <div class="template-list">
        <div
            v-for="tpl in availableTemplates"
            :key="tpl.id"
            class="template-card"
            :class="{ active: selectedTemplateId === tpl.id }"
            @click="selectTemplate(tpl.id)"
        >
          <div class="template-card-header">
            <span class="template-name">{{ tpl.name }}</span>
            <el-tag size="small" :type="getTemplateTagType(tpl.type)">{{ tpl.type }}</el-tag>
          </div>
          <div class="template-desc">{{ tpl.description || '暂无描述' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="templateSelectVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmTemplateSelect">确认使用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, ChatLineRound, Delete,
  Promotion, Close, Cpu, DArrowLeft, CopyDocument,
  Document, Setting, Grid
} from '@element-plus/icons-vue'
import { chatApi, llmApi, promptApi, systemModelApi} from '@/api'
import MarkdownRenderer from '@/views/components/MarkdownRenderer.vue'

// ==================== 状态变量 ====================
const currentChatModelId = ref(null)
const currentChatModelInfo = ref(null)
const sidebarCollapsed = ref(false)
const messagesContainer = ref(null)
const inputMessage = ref('')
const isStreaming = ref(false)
const currentSessionId = ref(Date.now().toString())
const messages = ref([])
const chatHistory = ref([])

// 模板相关
const currentTemplateName = ref('')
const showTemplateInfo = ref(false)
const manualTemplateEnabled = ref(false)
const selectedTemplateId = ref(null)
const availableTemplates = ref([])
const loadingTemplates = ref(false)
const settingsVisible = ref(false)
const templateSelectVisible = ref(false)

// 建议列表
const suggestions = [
  { text: '介绍一下你自己' },
  { text: '什么是RAG？' },
  { text: '帮我写一段Python爬虫代码' },
  { text: '解释一下机器学习' },
  { text: '写一首关于春天的诗' },
  { text: '分析一下销售数据' }
]

// 计算属性
const inputPlaceholder = computed(() => {
  if (manualTemplateEnabled.value && selectedTemplateId.value) {
    const template = availableTemplates.value.find(t => t.id === selectedTemplateId.value)
    return template ? `使用【${template.name}】模式，输入消息...` : '输入消息...'
  }
  return '输入消息... (Enter 发送, Shift+Enter 换行)'
})

// ==================== 存储操作 ====================
const STORAGE_KEYS = {
  CHAT_HISTORY: 'chatHistory',
  CHAT_MESSAGES_PREFIX: 'chat_messages_',
  CHAT_SETTINGS: 'chat_settings'
}

const saveChatHistory = () => {
  localStorage.setItem(STORAGE_KEYS.CHAT_HISTORY, JSON.stringify(chatHistory.value))
}

const loadChatHistory = () => {
  const saved = localStorage.getItem(STORAGE_KEYS.CHAT_HISTORY)
  if (saved) {
    try {
      chatHistory.value = JSON.parse(saved)
    } catch (e) {
      console.error('加载会话历史失败', e)
      chatHistory.value = []
    }
  }
}

const saveMessages = () => {
  const toStore = messages.value.map(({ role, rawContent, thinkContent, displayContent, time }) => ({
    role, rawContent, thinkContent, displayContent, time
  }))
  localStorage.setItem(`${STORAGE_KEYS.CHAT_MESSAGES_PREFIX}${currentSessionId.value}`, JSON.stringify(toStore))
}

const loadMessages = (sessionId) => {
  const saved = localStorage.getItem(`${STORAGE_KEYS.CHAT_MESSAGES_PREFIX}${sessionId}`)
  if (saved) {
    try {
      messages.value = JSON.parse(saved)
    } catch {
      messages.value = []
    }
  } else {
    messages.value = []
  }
}

const saveSettings = () => {
  localStorage.setItem(STORAGE_KEYS.CHAT_SETTINGS, JSON.stringify({
    showTemplateInfo: showTemplateInfo.value,
    manualTemplateEnabled: manualTemplateEnabled.value,
    selectedTemplateId: selectedTemplateId.value
  }))
}

const loadSettings = () => {
  const saved = localStorage.getItem(STORAGE_KEYS.CHAT_SETTINGS)
  if (saved) {
    try {
      const settings = JSON.parse(saved)
      showTemplateInfo.value = settings.showTemplateInfo || false
      manualTemplateEnabled.value = settings.manualTemplateEnabled || false
      selectedTemplateId.value = settings.selectedTemplateId || null
    } catch (e) {
      console.error('加载设置失败', e)
    }
  }
}

// ==================== API 操作 ====================
const saveSessionToDB = async (sessionId, title) => {
  try {
    await chatApi.saveSession(sessionId, title)
  } catch (error) {
    console.error('保存会话失败', error)
  }
}

const saveMessageToDB = async (sessionId, role, content) => {
  try {
    await chatApi.saveMessage(sessionId, role, content)
  } catch (error) {
    console.error('保存消息失败', error)
  }
}

const loadSessionsFromDB = async () => {
  try {
    const res = await chatApi.getSessions()
    if (res.code === 200 && res.data) {
      chatHistory.value = res.data.map(s => ({
        id: s.sessionId,
        title: s.title || '新对话'
      }))
      saveChatHistory()
    }
  } catch (error) {
    console.error('加载会话列表失败', error)
    loadChatHistory() // 降级使用本地缓存
  }
}

const loadMessagesFromDB = async (sessionId) => {
  try {
    const res = await chatApi.getMessages(sessionId)
    if (res.code === 200 && res.data) {
      messages.value = res.data.map(msg => {
        const parsed = parseThinkContent(msg.content)
        return {
          role: msg.role,
          rawContent: msg.content,
          thinkContent: parsed.thinkContent,
          displayContent: parsed.displayContent,
          time: new Date(msg.createTime).toLocaleTimeString()
        }
      })
      saveMessages()
    }
  } catch (error) {
    console.error('加载会话消息失败', error)
    loadMessages(sessionId) // 降级使用本地缓存
  }
}

// ==================== 辅助函数 ====================
const parseThinkContent = (rawContent) => {
  const thinkRegex = /<think>([\s\S]*?)<\/think>/
  const match = rawContent.match(thinkRegex)
  if (match) {
    return {
      thinkContent: match[1].trim(),
      displayContent: rawContent.replace(thinkRegex, '').trim()
    }
  }
  return { thinkContent: '', displayContent: rawContent }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const copyMessage = async (content) => {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

const stopStreaming = () => {
  isStreaming.value = false
  ElMessage.info('已停止')
}

// ==================== 模板相关 ====================
const getTemplateTagType = (type) => {
  const typeMap = {
    'CODE': 'danger', 'RAG': 'warning', 'CREATIVE': 'success',
    'DATA': 'info', 'GENERAL': '', 'CUSTOM': 'primary'
  }
  return typeMap[type] || ''
}

const loadAvailableTemplates = async () => {
  loadingTemplates.value = true
  try {
    const res = await promptApi.listTemplates()
    if (res.code === 200 && res.data) {
      availableTemplates.value = res.data.filter(t => t.status === 'ACTIVE')
    }
  } catch (error) {
    console.error('加载模板列表失败', error)
  } finally {
    loadingTemplates.value = false
  }
}

const clearSessionTemplateCache = async () => {
  try {
    if (llmApi.clearSessionTemplate) {
      await llmApi.clearSessionTemplate(currentSessionId.value)
    }
  } catch (error) {
    console.error('清除会话模板缓存失败', error)
  }
}

const onTemplateChange = (templateId) => {
  selectedTemplateId.value = templateId
  const template = availableTemplates.value.find(t => t.id === templateId)
  if (template) {
    currentTemplateName.value = template.name
    ElMessage.success(`已切换到【${template.name}】模式`)
  }
  saveSettings()
}

const onManualTemplateToggle = (enabled) => {
  if (!enabled) {
    selectedTemplateId.value = null
    currentTemplateName.value = ''
    clearSessionTemplateCache()
    ElMessage.info('已切换回智能匹配模式')
  } else if (!selectedTemplateId.value && availableTemplates.value.length > 0) {
    templateSelectVisible.value = true
  }
  saveSettings()
}

const selectTemplate = (templateId) => {
  selectedTemplateId.value = templateId
  const template = availableTemplates.value.find(t => t.id === templateId)
  if (template) currentTemplateName.value = template.name
}

const confirmTemplateSelect = () => {
  if (selectedTemplateId.value) {
    const template = availableTemplates.value.find(t => t.id === selectedTemplateId.value)
    ElMessage.success(`已选择【${template?.name}】模式`)
  }
  templateSelectVisible.value = false
  saveSettings()
}

// ==================== 消息操作 ====================
const sendMessage = async (quickMessage = null) => {
  const userMessage = quickMessage || inputMessage.value.trim()
  if (!userMessage || isStreaming.value) return
  if (!currentChatModelId.value) {
    ElMessage.warning('未配置系统大语言模型，请先在设置中配置')
    return
  }

  // 添加用户消息
  messages.value.push({
    role: 'user',
    rawContent: userMessage,
    displayContent: userMessage,
    thinkContent: '',
    time: new Date().toLocaleTimeString()
  })
  await saveMessageToDB(currentSessionId.value, 'user', userMessage)

  if (!quickMessage) inputMessage.value = ''
  scrollToBottom()

  // 第一条消息时创建会话
  if (messages.value.length === 1) {
    const title = userMessage.slice(0, 30) + (userMessage.length > 30 ? '...' : '')
    chatHistory.value.unshift({ id: currentSessionId.value, title })
    await saveSessionToDB(currentSessionId.value, title)
    saveChatHistory()
  }

  isStreaming.value = true

  try {
    const requestParams = {
      sessionId: currentSessionId.value,
      message: userMessage,
      configId: currentChatModelId.value,
      saveContext: true
    }

    if (manualTemplateEnabled.value && selectedTemplateId.value) {
      requestParams.templateId = selectedTemplateId.value
      const template = availableTemplates.value.find(t => t.id === selectedTemplateId.value)
      if (template) currentTemplateName.value = template.name
    }

    const response = await llmApi.chat(requestParams)

    if (response.code === 200 && response.data) {
      const parsed = parseThinkContent(response.data)
      messages.value.push({
        role: 'assistant',
        rawContent: response.data,
        thinkContent: parsed.thinkContent,
        displayContent: parsed.displayContent,
        time: new Date().toLocaleTimeString()
      })
      await saveMessageToDB(currentSessionId.value, 'assistant', response.data)
      saveMessages()
    } else {
      ElMessage.error(response.msg || '对话失败')
    }
  } catch (error) {
    ElMessage.error('发送失败：' + (error.message || '未知错误'))
  } finally {
    isStreaming.value = false
    scrollToBottom()
    saveMessages()
  }
}

const clearChat = async () => {
  await ElMessageBox.confirm('确定清空所有对话吗？', '提示', { type: 'warning' })
  messages.value = []
  saveMessages()
  ElMessage.success('已清空')
}

// ==================== 会话操作 ====================
const newChat = async () => {
  if (isStreaming.value) stopStreaming()
  if (messages.value.length > 0) saveMessages()

  currentSessionId.value = Date.now().toString()
  messages.value = []
  await clearSessionTemplateCache()

  if (!manualTemplateEnabled.value) currentTemplateName.value = ''
}

const switchChat = async (id) => {
  if (isStreaming.value) stopStreaming()
  if (messages.value.length > 0) saveMessages()

  currentSessionId.value = id
  await loadMessagesFromDB(id)
  scrollToBottom()

  if (!manualTemplateEnabled.value) currentTemplateName.value = ''
}

const deleteChat = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此对话吗？', '提示', { type: 'warning' })
    await chatApi.deleteSession(id)
    chatHistory.value = chatHistory.value.filter(c => c.id !== id)
    localStorage.removeItem(`${STORAGE_KEYS.CHAT_MESSAGES_PREFIX}${id}`)

    if (currentSessionId.value === id) {
      await newChat()
    }
    saveChatHistory()
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleEnter = (e) => {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

// ==================== 系统模型 ====================
const loadSystemChatModel = async () => {
  try {
    const res = await systemModelApi.getCapabilities()
    if (res.code === 200 && res.data?.chat?.modelConfigId) {
      currentChatModelId.value = res.data.chat.modelConfigId
      currentChatModelInfo.value = {
        modelName: res.data.chat.modelName,
        modelKey: res.data.chat.modelKey
      }
    } else {
      ElMessage.warning('未配置系统大语言模型，请先在设置中配置')
    }
  } catch (error) {
    console.error('加载系统模型失败:', error)
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await Promise.all([
    loadSystemChatModel(),
    loadSessionsFromDB(),
    loadAvailableTemplates()
  ])
  loadSettings()
  loadMessages(currentSessionId.value)
})

onUnmounted(() => {
  if (isStreaming.value) stopStreaming()
  if (messages.value.length > 0) saveMessages()
  saveSettings()
})
</script>

<style scoped>
/* ========== 基础样式 ========== */
.chat-container {
  display: flex;
  height: 100vh;
  background: #0a0e27;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
}

/* ========== 侧边栏 ========== */
.chat-sidebar {
  width: 280px;
  background: #0f1228;
  border-right: 1px solid #2a2f4a;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
}
.chat-sidebar.collapsed { width: 70px; }
.chat-sidebar.collapsed .logo span,
.chat-sidebar.collapsed .chat-item span,
.chat-sidebar.collapsed .chat-item .el-button,
.chat-sidebar.collapsed .empty-history { display: none; }

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.15), rgba(118, 75, 162, 0.08));
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 40px;
  cursor: pointer;
  transition: all 0.2s;
}
.logo:hover {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.25), rgba(118, 75, 162, 0.15));
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.chat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  color: #cbd5e6;
}
.chat-item:hover { background: rgba(129, 140, 248, 0.12); color: #fff; }
.chat-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(118, 75, 162, 0.12));
  color: #a5b4fc;
  border: 1px solid rgba(129, 140, 248, 0.25);
}
.chat-item span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}
.chat-item .el-button {
  opacity: 0;
  transition: opacity 0.2s;
  color: #94a3b8;
}
.chat-item:hover .el-button { opacity: 1; color: #f87171; }

/* ========== 主聊天区域 ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  padding: 12px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: #0f0f1a;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left, .header-right { display: flex; align-items: center; gap: 8px; }

.model-badge, .template-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(129, 140, 248, 0.12);
  border-radius: 20px;
  font-size: 12px;
  color: #a5b4fc;
}
.template-badge { background: rgba(129, 140, 248, 0.08); color: #94a3b8; }

.settings-btn { color: #94a3b8; }
.settings-btn:hover { color: #a5b4fc; }

/* ========== 消息区域 ========== */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.message {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}
.message.user { flex-direction: row-reverse; }
.message-avatar { font-size: 28px; flex-shrink: 0; }
.message-content { flex: 1; max-width: 80%; }

.message-header {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  align-items: center;
}
.message.user .message-header { justify-content: flex-end; }

.message-name { font-weight: 600; color: #e2e8f0; }
.message.user .message-name { color: #c7d2fe; }
.message-time { font-size: 11px; color: #94a3b8; }

.copy-btn {
  opacity: 0;
  transition: opacity 0.2s;
  color: #94a3b8;
}
.message:hover .copy-btn { opacity: 1; }

.message-text {
  padding: 14px 20px;
  border-radius: 20px;
  line-height: 1.6;
  font-size: 15px;
}
.message.user .message-text {
  background: linear-gradient(135deg, #5c6bc0, #8e5ea6);
  color: #fff;
}
.message.assistant .message-text {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  color: #f8fafc;
}

/* ========== 输入区域 ========== */
.input-container {
  padding: 16px 24px 24px;
  background: #0f0f1a;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.input-actions {
  margin-bottom: 12px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.chat-input { flex: 1; }
.chat-input :deep(.el-textarea__inner) {
  background: #1a1625;
  border: 1px solid #2d2a3a;
  border-radius: 16px;
  color: #f1f5f9;
  font-size: 14px;
  padding: 12px 16px;
  resize: none;
}
.chat-input :deep(.el-textarea__inner:focus) { border-color: #818cf8; }

.send-btn, .stop-btn {
  height: 44px;
  padding: 0 28px;
  border-radius: 16px;
}
.send-btn {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  color: #fff;
}
.stop-btn { background: #f59e0b; border: none; color: #fff; }

/* ========== 欢迎屏幕 ========== */
.welcome-screen {
  text-align: center;
  padding: 60px 20px;
  max-width: 600px;
  margin: 40px auto;
}
.welcome-icon { font-size: 64px; margin-bottom: 24px; }
.welcome-screen h1 {
  font-size: 32px;
  background: linear-gradient(135deg, #fff, #a5b4fc);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.welcome-screen p { color: #cbd5e6; margin-bottom: 32px; }

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}
.suggestion-item {
  padding: 10px 20px;
  background: #1a1625;
  border: 1px solid #2d2a3a;
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: #e2e8f0;
}
.suggestion-item:hover {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}

/* ========== 加载动画 ========== */
.loading-dots {
  display: flex;
  gap: 6px;
  padding: 8px 0;
}
.loading-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #818cf8;
  animation: bounce 1.4s infinite ease-in-out both;
}
.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* ========== think 折叠块 ========== */
.think-block { margin-bottom: 12px; }
.think-block :deep(.el-collapse) { background: transparent; border: none; }
.think-block :deep(.el-collapse-item__header) {
  background: rgba(129, 140, 248, 0.15);
  color: #cbd5ff;
  border-radius: 8px;
  border: none;
}

/* ========== 模板选择对话框 ========== */
.template-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 400px;
  overflow-y: auto;
}
.template-card {
  padding: 16px;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #1a1f3a;
}
.template-card:hover { border-color: #818cf8; background: rgba(129, 140, 248, 0.1); }
.template-card.active { border-color: #818cf8; background: rgba(129, 140, 248, 0.15); }
.template-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.template-name { font-weight: 600; color: #e2e8f0; }
.template-desc { font-size: 13px; color: #94a3b8; line-height: 1.5; }
.template-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.form-tip { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* ========== 滚动条 ========== */
.messages-container::-webkit-scrollbar,
.chat-history::-webkit-scrollbar { width: 4px; }
.messages-container::-webkit-scrollbar-thumb,
.chat-history::-webkit-scrollbar-thumb { background: #3a3f5c; border-radius: 4px; }

/* ========== 响应式 ========== */
@media (max-width: 768px) { .template-badge { display: none; } }
</style>