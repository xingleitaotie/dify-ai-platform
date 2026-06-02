<template>
  <div class="agent-container">
    <div class="page-header">
      <h2>Agent 管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        创建 Agent
      </el-button>
    </div>

    <div class="agent-grid">
      <el-card
          v-for="agent in agents"
          :key="agent.id"
          class="agent-card"
          @click="viewAgent(agent)"
      >
        <div class="agent-header">
          <el-icon :size="28"><Cpu /></el-icon>
          <div>
            <h3>{{ agent.agentName }}</h3>
            <el-tag :type="agent.isEnabled ? 'success' : 'info'" size="small">
              {{ agent.isEnabled ? '已启用' : '已禁用' }}
            </el-tag>
          </div>
        </div>
        <p class="agent-desc">{{ agent.description || '暂无描述' }}</p>
        <div class="agent-meta">
          <span><el-icon><Document /></el-icon> {{ agent.modelName }}</span>
          <span><el-icon><Timer /></el-icon> {{ formatDate(agent.createTime) }}</span>
        </div>
      </el-card>
    </div>

    <!-- 创建/编辑 Agent 对话框 -->
    <el-dialog
        v-model="showFormDialog"
        :title="editingAgent ? '编辑 Agent' : '创建 Agent'"
        width="900px"
        @closed="handleFormDialogClose"
    >
      <el-tabs v-model="formActiveTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
            <el-form-item label="Agent名称" prop="agentName">
              <el-input v-model="formData.agentName" placeholder="请输入Agent名称" />
            </el-form-item>

            <el-form-item label="Agent类型" prop="agentType">
              <el-select v-model="formData.agentType" placeholder="请选择Agent类型">
                <el-option label="对话助手" value="chat" />
                <el-option label="任务规划" value="planning" />
                <el-option label="代码助手" value="coding" />
                <el-option label="数据分析" value="analysis" />
                <el-option label="客服助手" value="customer_service" />
                <el-option label="翻译助手" value="translation" />
              </el-select>
            </el-form-item>

            <el-form-item label="模型" prop="modelName">
              <el-select v-model="formData.modelName" placeholder="请选择模型">
                <el-option label="GPT-4" value="gpt-4" />
                <el-option label="GPT-3.5-Turbo" value="gpt-3.5-turbo" />
                <el-option label="Claude-3" value="claude-3" />
                <el-option label="通义千问" value="qwen" />
                <el-option label="文心一言" value="ernie" />
              </el-select>
            </el-form-item>

            <el-form-item label="Temperature" prop="temperature">
              <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" />
              <span class="slider-value">{{ formData.temperature }}</span>
            </el-form-item>

            <el-form-item label="Max Tokens" prop="maxTokens">
              <el-input-number v-model="formData.maxTokens" :min="1" :max="4096" />
            </el-form-item>

            <!-- AI 智能生成提示词区域 -->
            <el-form-item label="AI 智能生成">
              <div class="ai-generate-section">
                <div class="requirement-input">
                  <el-input
                      v-model="requirement"
                      type="textarea"
                      :rows="3"
                      placeholder="描述您的Agent需求，例如：我需要一个专业的客服助手，能够回答用户关于产品的问题，语气要友好专业..."
                  />
                  <el-button
                      type="primary"
                      :loading="generatingPrompt"
                      @click="generatePrompt"
                      style="margin-top: 8px"
                  >
                    <el-icon><MagicStick /></el-icon>
                    AI 智能生成提示词
                  </el-button>
                </div>

                <!-- 生成结果显示 -->
                <div v-if="generatedPrompt" class="generated-prompt">
                  <el-alert
                      :title="`生成的提示词 (置信度: ${generatedConfidence}%)`"
                      type="success"
                      :closable="false"
                      style="margin-bottom: 12px"
                  >
                    <template #default>
                      <div class="confidence-score">
                        <el-progress
                            :percentage="generatedConfidence"
                            :color="getConfidenceColor(generatedConfidence)"
                            :stroke-width="8"
                        />
                      </div>
                    </template>
                  </el-alert>

                  <div class="prompt-preview">
                    <div class="prompt-header">
                      <span>系统提示词</span>
                      <div class="prompt-actions">
                        <el-button size="small" @click="copyToClipboard(generatedSystemPrompt)">
                          <el-icon><CopyDocument /></el-icon>
                          复制
                        </el-button>
                        <el-button size="small" type="primary" @click="applyGeneratedPrompt">
                          <el-icon><Select /></el-icon>
                          应用
                        </el-button>
                        <el-button size="small" @click="regeneratePrompt">
                          <el-icon><Refresh /></el-icon>
                          重新生成
                        </el-button>
                      </div>
                    </div>
                    <div class="prompt-content">
                      <pre>{{ generatedSystemPrompt }}</pre>
                    </div>
                  </div>
                </div>
              </div>
            </el-form-item>

            <el-form-item label="系统提示词" prop="systemPrompt">
              <el-input
                  v-model="formData.systemPrompt"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入系统提示词，或使用 AI 智能生成..."
              />
            </el-form-item>

            <el-form-item label="描述" prop="description">
              <el-input
                  v-model="formData.description"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入描述（可选）"
              />
            </el-form-item>

            <el-form-item label="状态" prop="isEnabled">
              <el-switch
                  v-model="formData.isEnabled"
                  :active-value="1"
                  :inactive-value="0"
              />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 知识库绑定 -->
        <el-tab-pane label="知识库绑定" name="kb">
          <div class="kb-binding">
            <div class="binding-header">
              <el-button type="primary" @click="openKbSelector">
                <el-icon><Plus /></el-icon>
                绑定知识库
              </el-button>
              <span class="binding-tip">绑定知识库后，Agent 可以搜索相关知识来回答问题</span>
            </div>

            <el-table :data="formKbBindings" stripe v-loading="formKbLoading" class="binding-table">
              <el-table-column prop="kbName" label="知识库名称" />
              <el-table-column prop="retrieveTopK" label="检索数量" width="120">
                <template #default="{ row }">
                  <el-input-number
                      v-model="row.retrieveTopK"
                      :min="1"
                      :max="20"
                      size="small"
                      @change="updateKbConfig(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column prop="scoreThreshold" label="相似度阈值" width="150">
                <template #default="{ row }">
                  <el-slider
                      v-model="row.scoreThreshold"
                      :min="0"
                      :max="1"
                      :step="0.05"
                      size="small"
                      style="width: 100px"
                      @change="updateKbConfig(row)"
                  />
                  <span style="margin-left: 8px;">{{ row.scoreThreshold }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="85" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button type="danger" size="small" @click="unbindFormKb(row)">
                    <el-icon><Delete /></el-icon>
                    解绑
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div v-if="formKbBindings.length === 0" class="empty-binding">
              <el-empty description="暂无绑定知识库" :image-size="80" />
            </div>
          </div>
        </el-tab-pane>

        <!-- 工具绑定 -->
        <el-tab-pane label="工具绑定" name="tools">
          <div class="tools-binding">
            <div class="binding-header">
              <span class="binding-tip">选择可用的工具，Agent 会根据用户问题自动调用</span>
            </div>

            <!-- 可绑定的工具列表 -->
            <h4>可绑定工具</h4>
            <el-table :data="availableTools.filter(t => !t.isBound)" stripe v-loading="toolsLoading" class="binding-table">
              <el-table-column prop="name" label="工具名称" />
              <el-table-column prop="desc" label="描述" show-overflow-tooltip />
              <el-table-column prop="params" label="参数" width="200">
                <template #default="{ row }">
                  <el-tag v-for="param in row.params" :key="param" size="small" style="margin: 2px">
                    {{ param }}
                  </el-tag>
                  <span v-if="!row.params?.length">无参数</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" size="small" @click="toggleTool(row)">绑定</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 已绑定的工具列表 -->
            <h4 style="margin-top: 24px;">已绑定工具</h4>
            <el-table :data="availableTools.filter(t => t.isBound)" stripe class="binding-table">
              <el-table-column prop="name" label="工具名称" />
              <el-table-column prop="desc" label="描述" show-overflow-tooltip />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-switch
                      v-model="row.isEnabled"
                      :active-value="1"
                      :inactive-value="0"
                      @change="updateToolStatus(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="danger" size="small" @click="toggleTool(row)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAgentForm" :loading="formSubmitting">
          {{ editingAgent ? '更新' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Agent 详情对话框 -->
    <el-dialog
        v-model="showDetailDialog"
        :title="currentAgent?.agentName"
        width="1000px"
        fullscreen
        @closed="handleDetailDialogClose"
    >
      <el-tabs v-model="detailTab">
        <el-tab-pane label="基本信息" name="info">
          <div class="agent-info">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="Agent名称">{{ currentAgent?.agentName }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ currentAgent?.agentType }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ currentAgent?.modelName }}</el-descriptions-item>
              <el-descriptions-item label="Temperature">{{ currentAgent?.temperature }}</el-descriptions-item>
              <el-descriptions-item label="Max Tokens">{{ currentAgent?.maxTokens }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="currentAgent?.isEnabled ? 'success' : 'info'">
                  {{ currentAgent?.isEnabled ? '已启用' : '已禁用' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="系统提示词" :span="2">
                <div class="system-prompt">{{ currentAgent?.systemPrompt }}</div>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <el-tab-pane label="知识库绑定" name="kb">
          <div class="kb-binding">
            <div class="binding-header">
              <el-button type="primary" @click="openDetailKbSelector">
                <el-icon><Plus /></el-icon>
                绑定知识库
              </el-button>
              <span class="binding-tip">绑定知识库后，Agent 可以搜索相关知识来回答问题</span>
            </div>

            <div class="table-scroll-wrapper">
              <el-table
                  :data="kbBindings"
                  v-loading="kbLoading"
                  style="min-width: 650px; width: 100%;"
              >
                <el-table-column prop="kbName" label="知识库名称" min-width="200" show-overflow-tooltip />

                <el-table-column prop="retrieveTopK" label="检索数量" width="150" align="center">
                  <template #default="{ row }">
                    <el-input-number
                        v-model="row.retrieveTopK"
                        :min="1"
                        :max="20"
                        size="small"
                        controls-position="right"
                        style="width: 110px"
                        @change="updateKbConfig(row)"
                    />
                  </template>
                </el-table-column>

                <el-table-column prop="scoreThreshold" label="相似度阈值" width="210" align="center">
                  <template #default="{ row }">
                    <div style="display: flex; align-items: center; gap: 12px;">
                      <el-slider
                          v-model="row.scoreThreshold"
                          :min="0"
                          :max="1"
                          :step="0.05"
                          size="small"
                          style="width: 120px"
                          @change="updateKbConfig(row)"
                      />
                      <span style="min-width: 40px; color: #a78bfa;">{{ row.scoreThreshold }}</span>
                    </div>
                  </template>
                </el-table-column>

                <el-table-column label="操作" width="95" align="center">
                  <template #default="{ row }">
                    <el-button type="danger" size="small" @click="unbindKB(row)">
                      <el-icon><Delete /></el-icon>
                      解绑
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>

            <div v-if="kbBindings.length === 0 && !kbLoading" class="empty-binding">
              <el-empty description="暂无绑定知识库" :image-size="80">
                <el-button type="primary" @click="openDetailKbSelector">立即绑定</el-button>
              </el-empty>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="工具配置" name="tools">
          <div class="tools-binding">
            <div class="binding-header">
              <span class="binding-tip">选择可用的工具，Agent 会根据用户问题自动调用</span>
            </div>

            <!-- 可绑定的工具列表 -->
            <h4>可绑定工具</h4>
            <el-table :data="detailAvailableTools.filter(t => !t.isBound)" v-loading="toolsLoading" style="margin-bottom: 24px">
              <el-table-column prop="name" label="工具名称" />
              <el-table-column prop="desc" label="描述" show-overflow-tooltip />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="primary" size="small" @click="toggleDetailTool(row)">绑定</el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 已绑定的工具列表 -->
            <h4>已绑定工具</h4>
            <el-table :data="detailAvailableTools.filter(t => t.isBound)">
              <el-table-column prop="name" label="工具名称" />
              <el-table-column prop="desc" label="描述" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-switch
                      v-model="row.isEnabled"
                      :active-value="1"
                      :inactive-value="0"
                      @change="updateDetailToolStatus(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button type="danger" size="small" @click="toggleDetailTool(row)">解绑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="测试对话" name="test">
          <div class="test-chat">
            <div class="chat-messages" ref="testMessagesContainer">
              <div
                  v-for="(msg, idx) in testMessages"
                  :key="idx"
                  class="message"
                  :class="msg.role"
              >
                <div class="message-content">{{ msg.content }}</div>
              </div>
            </div>
            <div class="chat-input">
              <el-input
                  v-model="testQuery"
                  type="textarea"
                  :rows="2"
                  placeholder="输入测试消息..."
                  @keydown.enter.prevent="sendTestMessage"
              />
              <el-button type="primary" @click="sendTestMessage" :loading="testLoading">
                发送
              </el-button>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 知识库选择器对话框（用于创建/编辑） -->
    <el-dialog v-model="showFormKbSelector" title="选择知识库" width="600px">
      <el-table
          :data="knowledgeBases"
          stripe
          v-loading="kbListLoading"
          @selection-change="handleFormKbSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="知识库名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="chunkCount" label="分块数" width="100" />
      </el-table>

      <div v-if="formSelectedKbs.length > 0" class="kb-config">
        <el-divider>检索配置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="检索数量">
              <el-input-number v-model="formKbConfig.retrieveTopK" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="相似度阈值">
              <el-slider v-model="formKbConfig.scoreThreshold" :min="0" :max="1" :step="0.05" />
              <span>{{ formKbConfig.scoreThreshold }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <template #footer>
        <el-button @click="showFormKbSelector = false">取消</el-button>
        <el-button type="primary" @click="confirmFormBindKb" :loading="bindingFormKb">
          确认绑定 ({{ formSelectedKbs.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 知识库选择器对话框（用于详情） -->
    <el-dialog v-model="showDetailKbSelector" title="选择知识库" width="600px">
      <el-table
          :data="knowledgeBases"
          stripe
          v-loading="kbListLoading"
          @selection-change="handleDetailKbSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="name" label="知识库名称" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="chunkCount" label="分块数" width="100" />
      </el-table>

      <div v-if="detailSelectedKbs.length > 0" class="kb-config">
        <el-divider>检索配置</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="检索数量">
              <el-input-number v-model="detailKbConfig.retrieveTopK" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="相似度阈值">
              <el-slider v-model="detailKbConfig.scoreThreshold" :min="0" :max="1" :step="0.05" />
              <span>{{ detailKbConfig.scoreThreshold }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </div>

      <template #footer>
        <el-button @click="showDetailKbSelector = false">取消</el-button>
        <el-button type="primary" @click="confirmDetailBindKb" :loading="bindingDetailKb">
          确认绑定 ({{ detailSelectedKbs.length }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Cpu, Document, Timer, Delete, MagicStick, CopyDocument, Select, Refresh } from '@element-plus/icons-vue'
import { agentApi } from '@/api/agent'
import { functionApi } from '@/api/function'
import { ragApi } from '@/api/rag'
import { promptApi } from '@/api/prompt'

// ==================== 通用状态 ====================
const agents = ref([])

// ==================== AI 生成相关 ====================
const requirement = ref('')
const generatingPrompt = ref(false)
const generatedPrompt = ref(null)
const generatedSystemPrompt = ref('')
const generatedConfidence = ref(0)

// ==================== 创建/编辑表单相关 ====================
const showFormDialog = ref(false)
const editingAgent = ref(null)
const formSubmitting = ref(false)
const formActiveTab = ref('basic')
const formRef = ref()

// 表单数据
const formData = ref({
  agentName: '',
  agentType: 'chat',
  modelName: 'gpt-3.5-turbo',
  temperature: 0.7,
  maxTokens: 2000,
  systemPrompt: '',
  description: '',
  isEnabled: 1
})

const formRules = {
  agentName: [{ required: true, message: '请输入Agent名称', trigger: 'blur' }],
  agentType: [{ required: true, message: '请选择Agent类型', trigger: 'change' }],
  modelName: [{ required: true, message: '请选择模型', trigger: 'change' }],
  systemPrompt: [{ required: true, message: '请输入系统提示词', trigger: 'blur' }]
}

// 表单的知识库绑定
const formKbBindings = ref([])
const formKbLoading = ref(false)

// 表单的知识库选择器
const showFormKbSelector = ref(false)
const formSelectedKbs = ref([])
const formKbConfig = ref({
  retrieveTopK: 5,
  scoreThreshold: 0.7
})

// ==================== 详情对话框相关 ====================
const showDetailDialog = ref(false)
const currentAgent = ref(null)
const detailTab = ref('info')

// 详情的知识库绑定
const kbBindings = ref([])
const kbLoading = ref(false)

// 详情的知识库选择器
const showDetailKbSelector = ref(false)
const detailSelectedKbs = ref([])
const detailKbConfig = ref({
  retrieveTopK: 5,
  scoreThreshold: 0.7
})

// 知识库列表（共用）
const knowledgeBases = ref([])
const kbListLoading = ref(false)
const bindingFormKb = ref(false)
const bindingDetailKb = ref(false)

// ==================== 工具相关 ====================
const toolsLoading = ref(false)
const availableTools = ref([])  // 用于创建/编辑对话框
const detailAvailableTools = ref([])  // 用于详情对话框
let isLoadingBindings = false

// ==================== 测试对话相关 ====================
const testMessages = ref([])
const testQuery = ref('')
const testLoading = ref(false)
const testMessagesContainer = ref()

// ==================== 通用函数 ====================
const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString()
}

const loadAgents = async () => {
  try {
    const res = await agentApi.list()
    if (res.code === 200) {
      agents.value = res.data || []
    }
  } catch (error) {
    console.error('加载Agent列表失败', error)
  }
}

// ==================== AI 智能生成函数 ====================
const generatePrompt = async () => {
  if (!requirement.value.trim()) {
    ElMessage.warning('请先输入您的需求描述')
    return
  }

  generatingPrompt.value = true
  try {
    const res = await promptApi.generate({
      requirement: requirement.value,
      agentType: formData.value.agentType,
      modelName: formData.value.modelName
    })

    if (res.code === 200 && res.data) {
      generatedPrompt.value = res.data
      generatedSystemPrompt.value = res.data.systemPrompt || res.data.prompt
      generatedConfidence.value = res.data.confidenceScore || 0

      ElMessage.success(`提示词生成成功！置信度: ${generatedConfidence.value}%`)
    } else {
      ElMessage.error(res.msg || '生成失败')
    }
  } catch (error) {
    console.error('生成提示词失败:', error)
    ElMessage.error('生成失败，请重试')
  } finally {
    generatingPrompt.value = false
  }
}

const regeneratePrompt = async () => {
  await generatePrompt()
}

const applyGeneratedPrompt = () => {
  if (generatedSystemPrompt.value) {
    formData.value.systemPrompt = generatedSystemPrompt.value
    ElMessage.success('提示词已应用到系统提示词框')

    // 如果生成的响应中包含推荐的模型参数，也可以应用
    if (generatedPrompt.value?.modelParams) {
      const params = generatedPrompt.value.modelParams
      if (params.temperature !== undefined) {
        formData.value.temperature = params.temperature
      }
      if (params.maxTokens !== undefined) {
        formData.value.maxTokens = params.maxTokens
      }
    }
  }
}

const copyToClipboard = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败')
  }
}

const getConfidenceColor = (score) => {
  if (score >= 80) return '#67C23A'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

// ==================== 创建/编辑对话框函数 ====================
const openCreateDialog = () => {
  editingAgent.value = null
  resetForm()
  showFormDialog.value = true
  // 创建新 Agent 时也需要加载工具列表
  loadToolsForAgent(null, availableTools)  // agentId 为 null 时只加载可用工具列表，不加载绑定状态
}

const openEditDialog = (agent) => {
  editingAgent.value = agent
  loadFormData()
  showFormDialog.value = true
}

const loadFormData = () => {
  if (editingAgent.value) {
    formData.value = {
      agentName: editingAgent.value.agentName || '',
      agentType: editingAgent.value.agentType || 'chat',
      modelName: editingAgent.value.modelName || 'gpt-3.5-turbo',
      temperature: editingAgent.value.temperature ?? 0.7,
      maxTokens: editingAgent.value.maxTokens || 2000,
      systemPrompt: editingAgent.value.systemPrompt || '',
      description: editingAgent.value.description || '',
      isEnabled: editingAgent.value.isEnabled === 1 || editingAgent.value.isEnabled === true ? 1 : 0
    }
    loadFormKbBindings()
    loadFormTools()
  }
}

const resetForm = () => {
  formData.value = {
    agentName: '',
    agentType: 'chat',
    modelName: 'gpt-3.5-turbo',
    temperature: 0.7,
    maxTokens: 2000,
    systemPrompt: '',
    description: '',
    isEnabled: 1
  }
  formActiveTab.value = 'basic'
  formKbBindings.value = []
  // 重置时清空工具列表
  availableTools.value = []
  requirement.value = ''
  generatedPrompt.value = null
  generatedSystemPrompt.value = ''
  generatedConfidence.value = 0
}

const loadFormKbBindings = async () => {
  if (!editingAgent.value?.id) return
  formKbLoading.value = true
  try {
    const res = await agentApi.getKbList(editingAgent.value.id)
    if (res.code === 200) {
      formKbBindings.value = res.data || []
    }
  } catch (error) {
    console.error('加载知识库绑定失败', error)
  } finally {
    formKbLoading.value = false
  }
}

// 统一的工具加载函数
const loadToolsForAgent = async (agentId, targetArray) => {
  toolsLoading.value = true
  isLoadingBindings = true
  try {
    // 获取所有可用工具
    const functionRes = await functionApi.getFunctionList()
    if (functionRes.code === 200) {
      const functions = functionRes.data || []

      let boundTools = []
      // 如果有 agentId，则获取已绑定的工具
      if (agentId) {
        const res = await agentApi.getToolList(agentId)
        boundTools = (res.code === 200 && res.data) ? res.data : []
      }

      targetArray.value = functions.map(func => {
        const boundTool = boundTools.find(t => t.toolName === func.name)
        if (boundTool) {
          return {
            ...func,
            isBound: true,
            boundId: boundTool.id,
            isEnabled: boundTool.isEnabled === 1 ? 1 : 0,
            _originalEnabled: boundTool.isEnabled === 1 ? 1 : 0
          }
        } else {
          return {
            ...func,
            isBound: false,
            boundId: null,
            isEnabled: 1,
            _originalEnabled: 1
          }
        }
      })
    }
  } catch (error) {
    console.error('加载工具失败', error)
  } finally {
    toolsLoading.value = false
    isLoadingBindings = false
  }
}

const loadFormTools = async () => {
  await loadToolsForAgent(editingAgent.value?.id, availableTools)
}

const submitAgentForm = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  formSubmitting.value = true
  try {
    const submitData = {
      ...formData.value,
      isEnabled: formData.value.isEnabled === 1 || formData.value.isEnabled === true ? 1 : 0
    }

    let res
    if (editingAgent.value) {
      res = await agentApi.update({ ...submitData, id: editingAgent.value.id })
    } else {
      res = await agentApi.create(submitData)
    }

    if (res.code === 200) {
      ElMessage.success(editingAgent.value ? '更新成功' : '创建成功')
      showFormDialog.value = false
      await loadAgents()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (error) {
    console.error('提交失败', error)
    ElMessage.error('操作失败')
  } finally {
    formSubmitting.value = false
  }
}

// ==================== 详情对话框函数 ====================
const viewAgent = async (agent) => {
  currentAgent.value = agent
  showDetailDialog.value = true
  await loadDetailBindings(agent.id)
  await loadDetailTools(agent.id)
}

const loadDetailBindings = async (agentId) => {
  if (isLoadingBindings) return
  isLoadingBindings = true
  kbLoading.value = true
  try {
    const [kbRes, toolRes] = await Promise.all([
      agentApi.getKbList(agentId),
      agentApi.getToolList(agentId)
    ])

    if (kbRes.code === 200) {
      kbBindings.value = kbRes.data || []
    }

    if (toolRes.code === 200) {
      // 这里只是临时存储，实际会通过 loadDetailTools 处理
      console.log('工具绑定数据:', toolRes.data)
    }
  } catch (error) {
    console.error('加载绑定失败', error)
  } finally {
    kbLoading.value = false
    isLoadingBindings = false
  }
}

const loadDetailTools = async (agentId) => {
  await loadToolsForAgent(agentId, detailAvailableTools)
}

// ==================== 工具状态更新 ====================
const updateToolStatus = async (tool) => {
  console.log('更新工具状态:', tool.isEnabled, tool._originalEnabled, tool.boundId)

  if (tool.isEnabled === tool._originalEnabled) {
    console.log('状态未变化，跳过')
    return
  }

  if (isLoadingBindings) {
    console.log('加载中，跳过')
    return
  }

  if (!tool.boundId) {
    console.warn('缺少 boundId')
    return
  }

  const originalStatus = tool.isEnabled
  try {
    const enabledValue = tool.isEnabled === 1 ? 1 : 0
    const res = await agentApi.updateToolStatus(tool.boundId, enabledValue)
    if (res.code === 200) {
      ElMessage.success(tool.isEnabled === 1 ? '已启用' : '已禁用')
      tool._originalEnabled = tool.isEnabled
    } else {
      tool.isEnabled = originalStatus
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (error) {
    console.error('更新失败:', error)
    tool.isEnabled = originalStatus
    ElMessage.error('操作失败')
  }
}

const updateDetailToolStatus = async (tool) => {
  await updateToolStatus(tool)
}

// ==================== 工具绑定/解绑 ====================
const toggleTool = async (tool) => {
  const shouldBind = !tool.isBound
  const agentId = editingAgent.value?.id || currentAgent.value?.id

  try {
    if (shouldBind) {
      const res = await agentApi.bindTool({
        agentId: agentId,
        toolName: tool.name,
        toolType: 'function',
        toolDesc: tool.desc,
        isEnabled: 1
      })
      if (res.code === 200) {
        ElMessage.success(`已添加工具「${tool.name}」`)
        if (editingAgent.value) {
          await loadFormTools()
        } else {
          await loadDetailTools(agentId)
        }
      } else {
        ElMessage.error(res.msg || '添加失败')
      }
    } else {
      if (!tool.boundId) {
        ElMessage.warning('未找到工具绑定记录')
        return
      }
      const res = await agentApi.unbindTool(tool.boundId)
      if (res.code === 200) {
        ElMessage.success(`已移除工具「${tool.name}」`)
        if (editingAgent.value) {
          await loadFormTools()
        } else {
          await loadDetailTools(agentId)
        }
      } else {
        ElMessage.error(res.msg || '移除失败')
      }
    }
  } catch (error) {
    console.error('操作失败:', error)
    ElMessage.error('操作失败')
  }
}

const toggleDetailTool = async (tool) => {
  await toggleTool(tool)
}

// ==================== 知识库相关函数 ====================
const loadKnowledgeBases = async () => {
  try {
    const res = await ragApi.getKnowledgeBases()
    if (res.code === 200) {
      knowledgeBases.value = res.data || []
    }
  } catch (error) {
    console.error('加载知识库列表失败', error)
  }
}

// 表单知识库选择器
const openKbSelector = () => {
  loadKnowledgeBases()
  showFormKbSelector.value = true
}

const handleFormKbSelectionChange = (selection) => {
  formSelectedKbs.value = selection
}

const confirmFormBindKb = async () => {
  if (formSelectedKbs.value.length === 0) {
    ElMessage.warning('请选择要绑定的知识库')
    return
  }

  bindingFormKb.value = true
  try {
    for (const kb of formSelectedKbs.value) {
      const bindData = {
        agentId: editingAgent.value.id,
        kbId: kb.id,
        kbName: kb.name,
        retrieveTopK: formKbConfig.value.retrieveTopK,
        scoreThreshold: formKbConfig.value.scoreThreshold
      }
      await agentApi.bindKb(bindData)
    }
    ElMessage.success(`成功绑定 ${formSelectedKbs.value.length} 个知识库`)
    showFormKbSelector.value = false
    formSelectedKbs.value = []
    await loadFormKbBindings()
  } catch (error) {
    console.error('绑定知识库失败', error)
    ElMessage.error('绑定失败')
  } finally {
    bindingFormKb.value = false
  }
}

// 详情知识库选择器
const openDetailKbSelector = () => {
  loadKnowledgeBases()
  showDetailKbSelector.value = true
}

const handleDetailKbSelectionChange = (selection) => {
  detailSelectedKbs.value = selection
}

const confirmDetailBindKb = async () => {
  if (detailSelectedKbs.value.length === 0) {
    ElMessage.warning('请选择要绑定的知识库')
    return
  }

  bindingDetailKb.value = true
  try {
    for (const kb of detailSelectedKbs.value) {
      const bindData = {
        agentId: currentAgent.value.id,
        kbId: kb.id,
        kbName: kb.name,
        retrieveTopK: detailKbConfig.value.retrieveTopK,
        scoreThreshold: detailKbConfig.value.scoreThreshold
      }
      await agentApi.bindKb(bindData)
    }
    ElMessage.success(`成功绑定 ${detailSelectedKbs.value.length} 个知识库`)
    showDetailKbSelector.value = false
    detailSelectedKbs.value = []
    await loadDetailBindings(currentAgent.value.id)
  } catch (error) {
    console.error('绑定知识库失败', error)
    ElMessage.error('绑定失败')
  } finally {
    bindingDetailKb.value = false
  }
}

// 解绑知识库
const unbindKB = async (binding) => {
  try {
    await ElMessageBox.confirm(`确定解绑知识库「${binding.kbName}」吗？`, '提示', {
      type: 'warning'
    })
    const res = await agentApi.unbindKb(binding.id)
    if (res.code === 200) {
      ElMessage.success('解绑成功')
      await loadDetailBindings(currentAgent.value.id)
    } else {
      ElMessage.error(res.msg || '解绑失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('解绑失败', error)
      ElMessage.error('解绑失败')
    }
  }
}

const unbindFormKb = async (kb) => {
  try {
    await ElMessageBox.confirm(`确定解绑知识库「${kb.kbName}」吗？`, '提示', {
      type: 'warning'
    })
    const res = await agentApi.unbindKb(kb.id)
    if (res.code === 200) {
      ElMessage.success('解绑成功')
      await loadFormKbBindings()
    } else {
      ElMessage.error(res.msg || '解绑失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('解绑失败', error)
      ElMessage.error('解绑失败')
    }
  }
}

// 更新知识库配置
const updateKbConfig = async (kb) => {
  try {
    const res = await agentApi.updateKbBind(kb.id, {
      retrieveTopK: kb.retrieveTopK,
      scoreThreshold: kb.scoreThreshold
    })
    if (res.code === 200) {
      ElMessage.success('配置已更新')
    }
  } catch (error) {
    console.error('更新配置失败', error)
  }
}

// ==================== 测试对话 ====================
const sendTestMessage = async () => {
  if (!testQuery.value.trim() || testLoading.value) return

  testMessages.value.push({
    role: 'user',
    content: testQuery.value
  })
  const query = testQuery.value
  testQuery.value = ''
  scrollTestChat()

  testLoading.value = true

  testMessages.value.push({
    role: 'assistant',
    content: ''
  })

  try {
    await agentApi.streamChat(
        currentAgent.value.id,
        query,
        `test_${currentAgent.value.id}`,
        (data) => {
          if (data && data.content) {
            const lastMsg = testMessages.value[testMessages.value.length - 1]
            lastMsg.content += data.content
            scrollTestChat()
          }
        },
        (error) => {
          console.error('Stream error:', error)
          const lastMsg = testMessages.value[testMessages.value.length - 1]
          lastMsg.content = '对话出错，请重试'
          testLoading.value = false
        },
        () => {
          testLoading.value = false
          scrollTestChat()
        }
    )
  } catch (error) {
    console.error('发送消息失败', error)
    testLoading.value = false
  }
}

const scrollTestChat = async () => {
  await nextTick()
  if (testMessagesContainer.value) {
    testMessagesContainer.value.scrollTop = testMessagesContainer.value.scrollHeight
  }
}

const handleFormDialogClose = () => {
  editingAgent.value = null
  resetForm()
}

const handleDetailDialogClose = () => {
  testMessages.value = []
  testQuery.value = ''
  testLoading.value = false
  detailAvailableTools.value = []
}

onMounted(() => {
  loadAgents()
})
</script>

<style scoped>
.agent-container {
  height: 100%;
  padding: 20px;
  background: #0a0e27;
  overflow-y: auto;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #a5b4fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.page-header .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  border-radius: 10px;
  padding: 10px 20px;
}

.page-header .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* ========== Agent 卡片网格 ========== */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
}

.agent-card {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 16px !important;
  cursor: pointer;
  transition: all 0.3s ease;
}

.agent-card:hover {
  transform: translateY(-4px);
  border-color: #667eea !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.agent-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.agent-header .el-icon {
  color: #667eea;
  font-size: 32px;
}

.agent-header h3 {
  margin: 0 0 6px 0;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.agent-header .el-tag--success {
  background: rgba(16, 185, 129, 0.15) !important;
  border: 1px solid rgba(16, 185, 129, 0.3) !important;
  color: #34d399 !important;
}

.agent-header .el-tag--info {
  background: rgba(100, 116, 139, 0.15) !important;
  border: 1px solid rgba(100, 116, 139, 0.3) !important;
  color: #94a3b8 !important;
}

.agent-desc {
  color: #94a3b8;
  margin-bottom: 16px;
  font-size: 13px;
  line-height: 1.5;
  min-height: 40px;
}

.agent-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #64748b;
}

.agent-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ========== 对话框通用样式 ========== */
:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 20px 24px;
  margin: 0;
}

:deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
  font-size: 18px;
}

:deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #94a3b8 !important;
}

:deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  color: #f87171 !important;
}

:deep(.el-dialog__body) {
  padding: 20px 24px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 24px;
}

:deep(.el-dialog__footer .el-button--default) {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

:deep(.el-dialog__footer .el-button--default:hover) {
  background: #3a3f5a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
}

:deep(.el-dialog__footer .el-button--primary) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== Tab 样式 ========== */
:deep(.el-tabs__header) {
  margin: 0 0 20px 0;
  background: transparent;
  border-bottom: 1px solid #2a2f4a;
}

:deep(.el-tabs__item) {
  color: #94a3b8;
  font-weight: 500;
  font-size: 14px;
}

:deep(.el-tabs__item:hover) {
  color: #a78bfa;
}

:deep(.el-tabs__item.is-active) {
  color: #a78bfa;
}

:deep(.el-tabs__active-bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
  height: 3px;
  border-radius: 3px;
}

/* ========== 表单样式 ========== */
:deep(.el-form-item__label) {
  color: #cbd5e6 !important;
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  box-shadow: none !important;
}

:deep(.el-input__wrapper:hover) {
  border-color: #667eea !important;
}

:deep(.el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

:deep(.el-input__inner) {
  color: #ffffff !important;
}

:deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}

:deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  color: #ffffff !important;
}

:deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
}

:deep(.el-select .el-input__wrapper) {
  background: #0f1228 !important;
}

:deep(.el-select-dropdown) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px;
}

:deep(.el-select-dropdown__item) {
  color: #cbd5e6 !important;
}

:deep(.el-select-dropdown__item:hover) {
  background: #2a2f4a !important;
  color: #ffffff !important;
}

:deep(.el-select-dropdown__item.selected) {
  background: rgba(102, 126, 234, 0.15) !important;
  color: #a78bfa !important;
}

/* 滑块样式 */
.slider-value {
  margin-left: 12px;
  color: #a78bfa;
  font-weight: 500;
}

:deep(.el-slider__runway) {
  background-color: #2a2f4a;
}

:deep(.el-slider__bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

:deep(.el-slider__button) {
  border-color: #667eea;
}

/* 数字输入框 */
:deep(.el-input-number .el-input__wrapper) {
  background: #0f1228 !important;
}

:deep(.el-input-number__increase),
:deep(.el-input-number__decrease) {
  background: #2a2f4a !important;
  color: #cbd5e6 !important;
}

:deep(.el-input-number__increase:hover),
:deep(.el-input-number__decrease:hover) {
  background: #3a3f5a !important;
  color: #ffffff !important;
}

/* 开关样式 */
:deep(.el-switch__core) {
  background: #2a2f4a !important;
  border-color: #3a3f5a !important;
}

:deep(.el-switch.is-checked .el-switch__core) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border-color: transparent !important;
}

/* ========== 表格样式 ========== */
.binding-table {
  background: transparent !important;
}

.binding-table :deep(.el-table) {
  background: transparent !important;
}

.binding-table :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.binding-table :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
}

.binding-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.06) !important;
}

.binding-table :deep(.el-tag) {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

/* ========== 知识库/工具绑定区域 ========== */
.kb-binding,
.tools-binding {
  padding: 0;
  min-height: 400px;
}

.binding-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;
}

.binding-header .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

.binding-tip {
  font-size: 12px;
  color: #64748b;
}

.empty-binding {
  padding: 60px 20px;
  text-align: center;
}

.empty-binding :deep(.el-empty__description p) {
  color: #94a3b8 !important;
}

.kb-config {
  margin-top: 20px;
  padding: 16px;
  background: #0f1228;
  border-radius: 12px;
  border: 1px solid #2a2f4a;
}

/* ========== 描述列表样式 ========== */
.agent-info {
  padding: 0;
}

.agent-info :deep(.el-descriptions) {
  --el-descriptions-table-bg: transparent !important;
}

.agent-info :deep(.el-descriptions__label) {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  border-color: #2a2f4a !important;
  font-weight: 500;
}

.agent-info :deep(.el-descriptions__content) {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
  border-color: #2a2f4a !important;
}

.system-prompt {
  white-space: pre-wrap;
  background: #0f1228;
  padding: 12px;
  border-radius: 8px;
  font-size: 13px;
  max-height: 200px;
  overflow-y: auto;
  color: #cbd5e6;
  border: 1px solid #2a2f4a;
}

/* ========== AI 生成区域样式 ========== */
.ai-generate-section {
  width: 100%;
}

.requirement-input {
  margin-bottom: 16px;
}

.requirement-input .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

.generated-prompt {
  margin-top: 16px;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  overflow: hidden;
  background: #0f1228;
}

.prompt-preview {
  padding: 16px;
}

.prompt-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #2a2f4a;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-header span {
  font-weight: 600;
  color: #ffffff;
}

.prompt-actions {
  display: flex;
  gap: 8px;
}

.prompt-actions .el-button {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.prompt-actions .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  color: #ffffff !important;
}

.prompt-actions .el-button:hover {
  transform: translateY(-1px);
}

.prompt-content {
  max-height: 300px;
  overflow-y: auto;
  background: #1a1f3a;
  border-radius: 8px;
  padding: 12px;
}

.prompt-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #cbd5e6;
}

.confidence-score {
  margin-top: 8px;
}

/* ========== 测试对话区域 ========== */
.test-chat {
  height: 500px;
  display: flex;
  flex-direction: column;
  background: #0f1228;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid #2a2f4a;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #0a0e27;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: #0f1228;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

.message {
  margin-bottom: 16px;
}

.message.user {
  text-align: right;
}

.message.user .message-content {
  display: inline-block;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #ffffff;
  padding: 10px 16px;
  border-radius: 18px;
  max-width: 70%;
  font-size: 14px;
}

.message.assistant {
  text-align: left;
}

.message.assistant .message-content {
  display: inline-block;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  color: #cbd5e6;
  padding: 10px 16px;
  border-radius: 18px;
  max-width: 70%;
  font-size: 14px;
}

.chat-input {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #2a2f4a;
  background: #0f1228;
}

.chat-input .el-textarea {
  flex: 1;
}

.chat-input .el-textarea :deep(.el-textarea__inner) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  color: #ffffff !important;
}

.chat-input .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .agent-container {
    padding: 12px;
  }

  .agent-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .page-header .el-button {
    width: 100%;
  }

  .binding-header {
    flex-direction: column;
    align-items: stretch;
  }

  .prompt-header {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media screen and (max-width: 1024px) and (min-width: 769px) {
  .agent-grid {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  }
}

/* 知识库绑定表格滚动容器 */
.kb-binding .table-scroll-wrapper {
  overflow-x: auto;
  width: 100%;
  margin-top: 16px;
  border-radius: 12px;
}

/* 空状态样式 */
.kb-binding .empty-binding {
  padding: 40px 20px;
  text-align: center;
}
</style>