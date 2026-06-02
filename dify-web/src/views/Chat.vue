<template>
  <div class="chat-container">
    <!-- 侧边栏 -->
    <div class="chat-sidebar">
      <div class="sidebar-header">
        <div class="logo" @click="newChat">
          <el-icon :size="28"><ChatDotRound /></el-icon>
          <span>新对话</span>
        </div>
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
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-screen">
          <div class="welcome-icon">🤖</div>
          <h1>Dify AI 助手</h1>
          <p>你好！我是 Dify AI 助手，有什么可以帮你的吗？</p>
          <div class="suggestions">
            <div
                v-for="suggestion in suggestions"
                :key="suggestion"
                class="suggestion-item"
                @click="sendQuickMessage(suggestion)"
            >
              {{ suggestion }}
            </div>
          </div>
        </div>

        <div v-for="(msg, index) in messages" :key="index" class="message" :class="msg.role">
          <div class="message-avatar">
            <el-avatar :size="36">
              <el-icon v-if="msg.role === 'user'"><UserFilled /></el-icon>
              <el-icon v-else><Service /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-name">{{ msg.role === 'user' ? '我' : 'Dify AI' }}</span>
              <span class="message-time">{{ msg.time }}</span>
            </div>
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
          </div>
        </div>

        <div v-if="isStreaming" class="message assistant">
          <div class="message-avatar">
            <el-avatar :size="36">
              <el-icon><Service /></el-icon>
            </el-avatar>
          </div>
          <div class="message-content">
            <div class="message-header">
              <span class="message-name">Dify AI</span>
              <span class="message-time">正在输入...</span>
            </div>
            <div class="message-text" v-html="formatMessage(streamingContent)"></div>
            <span class="cursor">▊</span>
          </div>
        </div>
      </div>

      <div class="input-container">
        <!-- 模型选择栏 -->
        <div class="model-selector-bar">
          <div class="model-selector">
            <span class="model-label">使用模型：</span>
            <el-select
                v-model="selectedModelId"
                placeholder="选择模型"
                size="default"
                style="width: 260px"
                :disabled="isStreaming"
                filterable
            >
              <el-option
                  v-for="model in modelList"
                  :key="model.id"
                  :label="model.configName"
                  :value="model.id"
              >
                <span style="float: left">{{ model.configName }}</span>
                <span style="float: right; color: #8492a6; font-size: 13px">
                  {{ getTypeLabel(model.type) }}
                </span>
              </el-option>
            </el-select>
          </div>
        </div>

        <div class="input-wrapper">
          <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="1"
              placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
              @keydown.enter.prevent="handleKeyDown"
              resize="none"
          />
          <el-button
              type="primary"
              :loading="isStreaming"
              :disabled="!inputMessage.trim()"
              @click="sendMessage"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
          <el-button
              v-if="isStreaming"
              type="warning"
              @click="stopStreaming"
          >
            <el-icon><Close /></el-icon>
            停止生成
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, ChatLineRound, Delete, UserFilled, Service, Promotion, Close } from '@element-plus/icons-vue'
import { streamChat } from '@/api/sse'
import { chatApi, modelConfigApi } from '@/api/chat'

// ==================== 模型相关 ====================
const selectedModelId = ref(null)  // 当前选择的模型ID
const modelList = ref([])  // 模型列表

// 获取模型类型显示名称
const getTypeLabel = (type) => {
  const labels = {
    modelScope: 'ModelScope',
    openai: 'OpenAI',
    ollama: 'Ollama',
    qwen: '通义千问',
    ernie: '文心一言',
    spark: '讯飞星火',
    zhipu: '智谱AI'
  }
  return labels[type] || type
}

// 加载可用的模型列表
const loadModelList = async () => {
  try {
    // 获取所有启用的配置
    const res = await modelConfigApi.getEnabledConfigs()
    if (res.code === 200 && res.data && res.data.length > 0) {
      modelList.value = res.data

      // 查找默认配置
      const defaultModel = modelList.value.find(m => m.isDefault === 1)
      if (defaultModel) {
        selectedModelId.value = defaultModel.id
      } else if (modelList.value.length > 0) {
        selectedModelId.value = modelList.value[0].id
      }
    } else {
      // 如果没有配置，使用默认选项
      ElMessage.warning('暂无可用模型配置，请先在设置中添加')
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.error('加载模型列表失败')
  }
}

// ==================== 原有变量 ====================
let abortController = null
let currentStreamClient = null

const messagesContainer = ref(null)
const inputMessage = ref('')
const isStreaming = ref(false)
const streamingContent = ref('')
const currentSessionId = ref(Date.now().toString())
const messages = ref([])
const chatHistory = ref([])

const suggestions = [
  '介绍一下你自己',
  '什么是RAG？',
  '帮我写一段Python爬虫代码',
  '解释一下机器学习'
]

const escapeHtml = (text) => {
  if (!text) return ''
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
}

const formatMessage = (content) => {
  if (!content) return ''

  let thinkHtml = ''
  let mainContent = content

  const thinkMatches = [...content.matchAll(/<think>([\s\S]*?)<\/think>/g)]
  if (thinkMatches.length > 0) {
    const lastThink = thinkMatches[thinkMatches.length - 1]
    const thinkText = lastThink[1].trim()
    thinkHtml = `
      <details class="think-details">
        <summary class="think-summary">💭 思考过程</summary>
        <div class="think-content">${escapeHtml(thinkText)}</div>
      </details>
    `
    mainContent = content.replace(/<think>[\s\S]*?<\/think>/g, '').trim()
  }

  let mainHtml = ''
  if (mainContent) {
    mainHtml = `<div style="white-space: pre-wrap; font-family: monospace;">${escapeHtml(mainContent)}</div>`
  }

  return thinkHtml + mainHtml
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

watch(streamingContent, () => {
  scrollToBottom()
})

const sendQuickMessage = (message) => {
  inputMessage.value = message
  sendMessage()
}

const stopStreaming = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  if (currentStreamClient && typeof currentStreamClient.disconnect === 'function') {
    currentStreamClient.disconnect()
  }
  isStreaming.value = false
  streamingContent.value = ''
  ElMessage.info('已停止生成')
}

const saveMessageToDB = async (sessionId, role, content) => {
  try {
    await chatApi.saveMessage(sessionId, role, content)
  } catch (error) {
    console.error('保存消息失败', error)
  }
}

const saveSessionToDB = async (sessionId, title) => {
  try {
    await chatApi.saveSession(sessionId, title)
  } catch (error) {
    console.error('保存会话失败', error)
  }
}

const sendMessage = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return
  if (!selectedModelId.value) {
    ElMessage.warning('请先选择模型')
    return
  }

  const userMessage = inputMessage.value.trim()
  const selectedModel = modelList.value.find(m => m.id === selectedModelId.value)

  messages.value.push({
    role: 'user',
    content: userMessage,
    time: new Date().toLocaleTimeString()
  })

  await saveMessageToDB(currentSessionId.value, 'user', userMessage)

  inputMessage.value = ''
  scrollToBottom()

  if (messages.value.length === 1) {
    const title = userMessage.slice(0, 30) + (userMessage.length > 30 ? '...' : '')
    chatHistory.value.unshift({
      id: currentSessionId.value,
      title
    })
    await saveSessionToDB(currentSessionId.value, title)
    saveChatHistory()
  }

  isStreaming.value = true
  streamingContent.value = ''

  abortController = new AbortController()

  try {
    let finalContent = ''

    // 调用流式对话，传入模型ID
    await streamChat(
        currentSessionId.value,
        userMessage,
        selectedModelId.value,  // 传入模型ID
        (data) => {
          if (data && data.content) {
            finalContent = data.content
            streamingContent.value = data.content
          }
        },
        (error) => {
          if (error.name === 'AbortError') {
            console.log('请求已取消')
          } else {
            console.error('Stream error:', error)
            ElMessage.error('对话出错：' + (error.message || '未知错误'))
          }
          isStreaming.value = false
          abortController = null
        },
        async () => {
          if (finalContent && finalContent.trim()) {
            messages.value.push({
              role: 'assistant',
              content: finalContent,
              time: new Date().toLocaleTimeString()
            })
            await saveMessageToDB(currentSessionId.value, 'assistant', finalContent)
          }
          streamingContent.value = ''
          isStreaming.value = false
          abortController = null
          scrollToBottom()
          saveChatMessages()
        },
        abortController.signal
    )
  } catch (error) {
    if (error.name !== 'AbortError') {
      console.error('Send message error:', error)
      ElMessage.error('发送失败：' + (error.message || '未知错误'))
    }
    isStreaming.value = false
    abortController = null
  }
}

const saveChatMessages = () => {
  const key = `chat_messages_${currentSessionId.value}`
  localStorage.setItem(key, JSON.stringify(messages.value))
}

const loadChatMessages = (sessionId) => {
  const key = `chat_messages_${sessionId}`
  const saved = localStorage.getItem(key)
  if (saved) {
    try {
      messages.value = JSON.parse(saved)
    } catch (e) {}
  } else {
    messages.value = []
  }
}

const newChat = () => {
  if (isStreaming.value) {
    stopStreaming()
  }
  if (messages.value.length > 0) {
    saveChatMessages()
  }
  currentSessionId.value = Date.now().toString()
  messages.value = []
  streamingContent.value = ''
}

const deleteChat = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此对话吗？', '提示', { type: 'warning' })
    const res = await chatApi.deleteSession(id)
    if (res.code !== 200) {
      ElMessage.error(res.msg || '删除失败')
      return
    }

    const index = chatHistory.value.findIndex(c => c.id === id)
    if (index !== -1) chatHistory.value.splice(index, 1)
    localStorage.removeItem(`chat_messages_${id}`)

    if (currentSessionId.value === id) {
      newChat()
    }

    saveChatHistory()
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

const loadSessions = async () => {
  try {
    const res = await chatApi.getSessions()
    if (res.code === 200 && res.data) {
      chatHistory.value = res.data.map(session => ({
        id: session.sessionId,
        title: session.title || '新对话'
      }))
    }
  } catch (error) {
    console.error('加载会话列表失败', error)
  }
}

const loadSessionMessages = async (sessionId) => {
  try {
    const res = await chatApi.getMessages(sessionId)
    if (res.code === 200 && res.data) {
      messages.value = res.data.map(msg => ({
        role: msg.role,
        content: msg.content,
        time: new Date(msg.createTime).toLocaleTimeString()
      }))
    }
  } catch (error) {
    console.error('加载会话消息失败', error)
  }
}

const switchChat = async (id) => {
  if (isStreaming.value) {
    stopStreaming()
  }
  if (messages.value.length > 0) {
    saveChatMessages()
  }
  currentSessionId.value = id
  await loadSessionMessages(id)
  scrollToBottom()
}

const handleKeyDown = (e) => {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const saveChatHistory = () => {
  localStorage.setItem('chatHistory', JSON.stringify(chatHistory.value))
}

const loadChatHistory = () => {
  const saved = localStorage.getItem('chatHistory')
  if (saved) {
    try {
      const history = JSON.parse(saved)
      if (history.length > 0) chatHistory.value = history
    } catch (e) {}
  }
}

onMounted(() => {
  loadModelList()
  loadSessions()
  loadChatMessages(currentSessionId.value)
})

onUnmounted(() => {
  if (isStreaming.value) {
    stopStreaming()
  }
  if (messages.value.length > 0) {
    saveChatMessages()
  }
  saveChatHistory()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 100vh;
  background: #0a0e27;
}

/* ========== 侧边栏 - 深色科技风 ========== */
.chat-sidebar {
  width: 280px;
  background: #0f1228;
  border-right: 1px solid #2a2f4a;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #2a2f4a;
}

.logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #1a1f3a, #0f1228);
  border: 1px solid #2a2f4a;
  border-radius: 40px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
  color: #ffffff;
}

.logo:hover {
  background: linear-gradient(135deg, #2a2f4a, #1a1f3a);
  border-color: #667eea;
}

.logo .el-icon {
  color: #667eea;
}

.logo span {
  color: #ffffff;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

/* 自定义滚动条 */
.chat-history::-webkit-scrollbar {
  width: 4px;
}

.chat-history::-webkit-scrollbar-track {
  background: #0f1228;
}

.chat-history::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 2px;
}

.chat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
  color: #cbd5e6;
  background: transparent;
}

.chat-item:hover {
  background: #1a1f3a;
  color: #ffffff;
}

.chat-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(118, 75, 162, 0.1));
  color: #667eea;
  border: 1px solid rgba(102, 126, 234, 0.3);
}

.chat-item span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: inherit;
}

.chat-item .el-button {
  color: #64748b;
  opacity: 0;
  transition: opacity 0.2s;
}

.chat-item:hover .el-button {
  opacity: 1;
}

.chat-item .el-button:hover {
  color: #ef4444;
}

.empty-history {
  padding: 20px;
  color: #64748b;
}

.empty-history :deep(.el-empty__description p) {
  color: #64748b;
}

/* ========== 主聊天区域 ========== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0a0e27;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* 自定义滚动条 */
.messages-container::-webkit-scrollbar {
  width: 6px;
}

.messages-container::-webkit-scrollbar-track {
  background: #0f1228;
}

.messages-container::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

/* 欢迎屏幕 */
.welcome-screen {
  text-align: center;
  padding: 60px 20px;
  background: radial-gradient(circle at center, rgba(102, 126, 234, 0.08), transparent);
  border-radius: 48px;
  margin: 40px auto;
  max-width: 600px;
}

.welcome-icon {
  font-size: 64px;
  margin-bottom: 24px;
}

.welcome-screen h1 {
  font-size: 32px;
  margin-bottom: 12px;
  color: #ffffff;
  font-weight: 700;
}

.welcome-screen p {
  color: #cbd5e6;
  margin-bottom: 32px;
  font-size: 16px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  max-width: 500px;
  margin: 0 auto;
}

.suggestion-item {
  padding: 10px 20px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 24px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
  color: #cbd5e6;
}

.suggestion-item:hover {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #ffffff;
  transform: translateY(-2px);
  border-color: transparent;
}

/* 消息样式 */
.message {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  max-width: 100%;
  animation: fadeInUp 0.3s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.message.user {
  flex-direction: row-reverse;
}

.message.user .message-content {
  align-items: flex-end;
}

.message.user .message-header {
  justify-content: flex-end;
}

.message.user .message-text {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #ffffff;
}

.message.assistant .message-text {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  color: #cbd5e6;
}

.message-avatar {
  flex-shrink: 0;
}

.message-avatar :deep(.el-avatar) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
}

.message.user .message-avatar :deep(.el-avatar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  max-width: 80%;
}

.message-header {
  display: flex;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.message-name {
  font-weight: 600;
  color: #cbd5e6;
}

.message.user .message-name {
  color: #a78bfa;
}

.message-time {
  font-size: 11px;
  color: #64748b;
}

.message-text {
  padding: 12px 16px;
  border-radius: 16px;
  line-height: 1.6;
  font-size: 14px;
  word-wrap: break-word;
  overflow-x: auto;
}

.message.user .message-text {
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  border-bottom-left-radius: 4px;
}

/* 光标闪烁 */
.cursor {
  display: inline-block;
  width: 2px;
  height: 1.2em;
  background: #667eea;
  vertical-align: middle;
  margin-left: 2px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ========== 输入区域 ========== */
.input-container {
  padding: 20px 24px;
  background: #0f1228;
  border-top: 1px solid #2a2f4a;
}

.model-selector-bar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 8px 0;
  margin-bottom: 16px;
  border-bottom: 1px solid #2a2f4a;
}

.model-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-label {
  font-size: 13px;
  color: #cbd5e6;
  font-weight: 500;
}

/* 模型选择器样式覆盖 */
.model-selector-bar :deep(.el-select) {
  width: 260px;
}

.model-selector-bar :deep(.el-input__wrapper) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  box-shadow: none;
  border-radius: 10px;
}

.model-selector-bar :deep(.el-input__wrapper:hover) {
  border-color: #667eea;
}

.model-selector-bar :deep(.el-input__inner) {
  color: #ffffff;
}

.model-selector-bar :deep(.el-select .el-input__suffix) {
  color: #667eea;
}

.input-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.input-wrapper .el-textarea {
  flex: 1;
}

.input-wrapper :deep(.el-textarea__inner) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  color: #ffffff;
  font-size: 14px;
  resize: none;
}

.input-wrapper :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.input-wrapper :deep(.el-textarea__inner::placeholder) {
  color: #64748b;
}

.input-wrapper .el-button {
  height: 40px;
  padding: 0 24px;
  border-radius: 12px;
  font-weight: 500;
}

.input-wrapper .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
}

.input-wrapper .el-button--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.input-wrapper .el-button--warning {
  background: #f59e0b;
  border: none;
  color: #ffffff;
}

/* ========== 思考过程样式 ========== */
.think-details {
  margin: 8px 0;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  background: #0f1228;
  overflow: hidden;
}

.think-summary {
  padding: 10px 14px;
  cursor: pointer;
  font-size: 12px;
  color: #cbd5e6;
  background: #1a1f3a;
  user-select: none;
  font-weight: 500;
}

.think-summary::-webkit-details-marker {
  display: none;
}

.think-summary:before {
  content: '▶';
  display: inline-block;
  margin-right: 8px;
  font-size: 10px;
  transition: transform 0.2s;
  color: #667eea;
}

details[open] .think-summary:before {
  transform: rotate(90deg);
}

.think-summary:hover {
  background: #2a2f4a;
}

.think-content {
  padding: 12px 14px;
  border-top: 1px solid #2a2f4a;
  font-size: 12px;
  color: #cbd5e6;
  background: #0f1228;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  line-height: 1.5;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .chat-sidebar {
    position: fixed;
    left: -280px;
    top: 0;
    height: 100%;
    z-index: 100;
    transition: left 0.3s;
  }

  .message-content {
    max-width: 85%;
  }

  .welcome-screen {
    margin: 20px;
    padding: 40px 20px;
  }

  .suggestions {
    gap: 8px;
  }

  .suggestion-item {
    padding: 8px 16px;
    font-size: 12px;
  }
}
</style>