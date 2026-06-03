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
      >
        <!-- 操作按钮组 -->
        <div class="card-actions" @click.stop>
          <el-tooltip content="编辑" placement="top">
            <el-button circle size="small" :icon="Edit" type="primary" @click="openEditDialog(agent)" />
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button circle size="small" :icon="Delete" type="danger" @click="deleteAgent(agent)" />
          </el-tooltip>
        </div>

        <div @click="viewAgent(agent)">
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
            <span><el-icon><Document /></el-icon> {{ agent.modelName || '-' }}</span>
            <span><el-icon><Timer /></el-icon> {{ formatDate(agent.createTime) }}</span>
          </div>
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

            <el-form-item label="模型" prop="modelConfigId">
              <el-select
                  v-model="formData.modelConfigId"
                  placeholder="请选择模型"
                  filterable
                  :loading="modelLoading"
                  clearable
              >
                <el-option
                    v-for="model in modelList"
                    :key="model.id"
                    :label="`${model.configName} (${getModelTypeLabel(model.type)})`"
                    :value="model.id"
                >
                  <span style="float: left">{{ model.configName }}</span>
                  <span style="float: right; color: #8492a6; font-size: 13px">
                    {{ getModelTypeLabel(model.type) }}
                  </span>
                </el-option>
              </el-select>
              <div class="form-tip">选择系统中已配置的模型</div>
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

        <!-- 知识库绑定 Tab（仅在编辑模式下显示） -->
        <el-tab-pane
            v-if="editingAgent"
            label="知识库绑定"
            name="kb"
        >
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
              <el-descriptions-item label="类型">{{ getAgentTypeLabel(currentAgent?.agentType) }}</el-descriptions-item>
              <el-descriptions-item label="模型">{{ currentAgent?.modelName || '-' }}</el-descriptions-item>
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
      <!-- 添加加载状态 -->
      <div v-loading="kbListLoading" element-loading-text="正在加载知识库列表...">
        <el-table
            :data="knowledgeBases"
            stripe
            @selection-change="handleFormKbSelectionChange"
            :empty-text="kbListLoading ? '加载中...' : '暂无知识库，请先创建知识库'"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column prop="name" label="知识库名称" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column prop="chunkCount" label="分块数" width="100" />
        </el-table>
      </div>

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
        <el-button
            type="primary"
            @click="confirmFormBindKb"
            :loading="bindingFormKb"
            :disabled="formSelectedKbs.length === 0"
        >
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
import { ref, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Cpu, Document, Timer, Delete, MagicStick, CopyDocument, Select, Refresh, Edit } from '@element-plus/icons-vue'
import { agentApi } from '@/api/agent'
import { ragApi } from '@/api/rag'
import { promptApi } from '@/api/prompt'
import { modelConfigApi } from '@/api/chat'

// ==================== 通用状态 ====================
const agents = ref([])

// ==================== 模型相关 ====================
const modelList = ref([])
const modelLoading = ref(false)
// 创建模型 ID 到模型对象的映射，用于快速查找
const modelMap = ref(new Map())

// 加载模型列表
const loadModelList = async () => {
  modelLoading.value = true
  try {
    const res = await modelConfigApi.getEnabledConfigs()
    if (res.code === 200 && res.data) {
      modelList.value = res.data
      // 构建模型 ID 到模型对象的映射
      modelMap.value.clear()
      res.data.forEach(model => {
        modelMap.value.set(model.id, model)
      })
      console.log('加载模型列表成功，共', modelList.value.length, '个模型')
    } else {
      console.warn('模型列表为空或加载失败')
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.error('加载模型列表失败')
  } finally {
    modelLoading.value = false
  }
}

// 根据模型 ID 获取模型名称
const getModelNameById = (modelConfigId) => {
  if (!modelConfigId) return '-'
  const model = modelMap.value.get(modelConfigId)
  return model ? model.configName : `模型ID:${modelConfigId}`
}

// ==================== Agent 类型中文化映射 ====================
const getAgentTypeLabel = (type) => {
  const typeMap = {
    'chat': '对话助手',
    'planning': '任务规划',
    'coding': '代码助手',
    'analysis': '数据分析',
    'customer_service': '客服助手',
    'translation': '翻译助手'
  }
  return typeMap[type] || type
}

const getModelTypeLabel = (type) => {
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

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString()
}

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
  modelConfigId: null,
  temperature: 0.7,
  maxTokens: 2000,
  systemPrompt: '',
  description: '',
  isEnabled: 1
})

const formRules = {
  agentName: [{ required: true, message: '请输入Agent名称', trigger: 'blur' }],
  agentType: [{ required: true, message: '请选择Agent类型', trigger: 'change' }],
  modelConfigId: [{ required: true, message: '请选择模型', trigger: 'change' }],
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

// ==================== 测试对话相关 ====================
const testMessages = ref([])
const testQuery = ref('')
const testLoading = ref(false)
const testMessagesContainer = ref()

// ==================== Agent CRUD 操作 ====================
const loadAgents = async () => {
  try {
    const res = await agentApi.list()
    if (res && typeof res === 'object' && res.code === 200) {
      agents.value = Array.isArray(res.data) ? res.data : []
    } else {
      console.warn('加载 Agent 列表返回异常:', res)
      agents.value = []
    }
  } catch (error) {
    console.error('加载Agent列表失败', error)
    agents.value = []
    ElMessage.error('加载 Agent 列表失败')
  }
}

const deleteAgent = async (agent) => {
  try {
    await ElMessageBox.confirm(
        `确定要删除 Agent「${agent.agentName}」吗？删除后无法恢复。`,
        '确认删除',
        {
          confirmButtonText: '删除',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )

    const res = await agentApi.delete(agent.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadAgents()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
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
      agentType: formData.value.agentType
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
    if (generatedPrompt.value?.modelParams) {
      const params = generatedPrompt.value.modelParams
      if (params.temperature !== undefined) formData.value.temperature = params.temperature
      if (params.maxTokens !== undefined) formData.value.maxTokens = params.maxTokens
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
  formActiveTab.value = 'basic'
}

const openEditDialog = async (agent) => {
  // 确保模型列表已加载
  if (modelList.value.length === 0) {
    await loadModelList()
  }

  showFormDialog.value = true
  editingAgent.value = agent
  formSubmitting.value = false

  try {
    // 调用详情接口获取完整数据
    const res = await agentApi.detail(agent.id)
    if (res && res.code === 200 && res.data) {
      const fullAgent = res.data
      editingAgent.value = fullAgent

      // 根据 modelName 查找对应的 modelConfigId
      let modelConfigId = null
      if (fullAgent.modelName) {
        const foundModel = modelList.value.find(m => m.configName === fullAgent.modelName)
        modelConfigId = foundModel ? foundModel.id : null
      }

      // 加载表单数据
      formData.value = {
        agentName: fullAgent.agentName || '',
        agentType: fullAgent.agentType || 'chat',
        modelConfigId: modelConfigId,  // 设置找到的模型ID
        temperature: fullAgent.temperature ?? 0.7,
        maxTokens: fullAgent.maxTokens || 2000,
        systemPrompt: fullAgent.systemPrompt || '',
        description: fullAgent.description || '',
        isEnabled: fullAgent.isEnabled === 1 || fullAgent.isEnabled === true ? 1 : 0
      }

      // 加载知识库绑定
      await loadFormKbBindings()

      // 激活基本信息 Tab
      formActiveTab.value = 'basic'
    } else {
      ElMessage.error(res?.msg || '获取 Agent 详情失败')
      showFormDialog.value = false
      editingAgent.value = null
    }
  } catch (error) {
    console.error('获取 Agent 详情失败:', error)
    ElMessage.error('获取 Agent 详情失败，请重试')
    showFormDialog.value = false
    editingAgent.value = null
  }
}

const resetForm = () => {
  formData.value = {
    agentName: '',
    agentType: 'chat',
    modelConfigId: null,
    temperature: 0.7,
    maxTokens: 2000,
    systemPrompt: '',
    description: '',
    isEnabled: 1
  }
  formActiveTab.value = 'basic'
  formKbBindings.value = []
  requirement.value = ''
  generatedPrompt.value = null
  generatedSystemPrompt.value = ''
  generatedConfidence.value = 0
}

const loadFormKbBindings = async () => {
  const agentId = editingAgent.value?.id
  if (!agentId) {
    formKbBindings.value = []
    return
  }

  formKbLoading.value = true
  try {
    const res = await agentApi.getKbList(agentId)
    if (res.code === 200) {
      formKbBindings.value = res.data || []
    } else {
      formKbBindings.value = []
    }
  } catch (error) {
    console.error('加载知识库绑定失败:', error)
    formKbBindings.value = []
  } finally {
    formKbLoading.value = false
  }
}

const submitAgentForm = async () => {
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (formSubmitting.value) return

  formSubmitting.value = true

  try {
    // 根据选中的模型ID，获取模型名称
    let modelName = ''
    if (formData.value.modelConfigId) {
      const selectedModel = modelList.value.find(m => m.id === formData.value.modelConfigId)
      modelName = selectedModel ? selectedModel.configName : ''
    }

    const submitData = {
      agentName: formData.value.agentName,
      agentType: formData.value.agentType,
      modelName: modelName,  // 存储模型名称到 modelName 字段
      temperature: formData.value.temperature,
      maxTokens: formData.value.maxTokens,
      systemPrompt: formData.value.systemPrompt,
      description: formData.value.description,
      isEnabled: formData.value.isEnabled === 1 || formData.value.isEnabled === true ? 1 : 0
    }

    const isCreateMode = !editingAgent.value
    let res

    if (isCreateMode) {
      res = await agentApi.create(submitData)
      if (res && res.code === 200) {
        ElMessage.success('Agent 创建成功！')
        showFormDialog.value = false
        await loadAgents()
        setTimeout(() => {
          ElMessage.info('您可以在 Agent 详情页或编辑页中绑定知识库', { duration: 3000 })
        }, 500)
      } else {
        ElMessage.error(res?.msg || '创建失败，请重试')
      }
    } else {
      // 编辑模式，需要传 id
      res = await agentApi.update({ ...submitData, id: editingAgent.value.id })
      if (res && res.code === 200) {
        ElMessage.success('Agent 信息已更新')
        showFormDialog.value = false
        await loadAgents()
      } else {
        ElMessage.error(res?.msg || '更新失败，请重试')
      }
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('操作失败，请稍后重试')
  } finally {
    formSubmitting.value = false
  }
}

const openKbSelector = async () => {
  const agentId = editingAgent.value?.id
  if (!agentId) {
    ElMessage.error('Agent 信息异常，请刷新页面后重试')
    return
  }

  // 显示加载提示
  const loadingInstance = ElMessage({
    message: '正在加载知识库列表...',
    type: 'info',
    duration: 0,  // 不自动关闭
    showClose: false
  })

  try {
    await loadKnowledgeBases()
    loadingInstance.close()

    // 如果知识库列表为空，给用户提示
    if (knowledgeBases.value.length === 0) {
      ElMessage.warning('暂无可用知识库，请先创建知识库')
    }

    showFormKbSelector.value = true
  } catch (error) {
    loadingInstance.close()
    console.error('加载知识库列表失败:', error)
    ElMessage.error('加载知识库列表失败，请重试')
  }
}

// ==================== 详情对话框函数 ====================
const viewAgent = async (agent) => {
  currentAgent.value = agent
  showDetailDialog.value = true
  await loadDetailBindings(agent.id)
}

const loadDetailBindings = async (agentId) => {
  kbLoading.value = true
  try {
    const res = await agentApi.getKbList(agentId)
    if (res.code === 200) {
      kbBindings.value = res.data || []
    }
  } catch (error) {
    console.error('加载绑定失败', error)
  } finally {
    kbLoading.value = false
  }
}

// ==================== 知识库相关函数 ====================
const loadKnowledgeBases = async () => {
  kbListLoading.value = true
  try {
    const res = await ragApi.getKnowledgeBases()
    if (res.code === 200) {
      knowledgeBases.value = res.data || []
      console.log('知识库列表加载完成，共', knowledgeBases.value.length, '个')
    } else {
      console.warn('加载知识库列表失败:', res.msg)
      knowledgeBases.value = []
      ElMessage.error(res.msg || '加载知识库列表失败')
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    knowledgeBases.value = []
    ElMessage.error('加载知识库列表失败，请检查网络')
  } finally {
    kbListLoading.value = false
  }
}

const handleFormKbSelectionChange = (selection) => {
  formSelectedKbs.value = selection
}

const confirmFormBindKb = async () => {
  if (formSelectedKbs.value.length === 0) {
    ElMessage.warning('请选择要绑定的知识库')
    return
  }

  const agentId = editingAgent.value?.id
  if (!agentId) {
    ElMessage.error('Agent 信息异常，请刷新页面后重试')
    return
  }

  bindingFormKb.value = true
  try {
    const bindPromises = formSelectedKbs.value.map(kb => {
      return agentApi.bindKb({
        agentId: agentId,
        kbId: kb.id,
        kbName: kb.name,
        retrieveTopK: formKbConfig.value.retrieveTopK,
        scoreThreshold: formKbConfig.value.scoreThreshold
      })
    })
    await Promise.all(bindPromises)
    ElMessage.success(`成功绑定 ${formSelectedKbs.value.length} 个知识库`)
    showFormKbSelector.value = false
    formSelectedKbs.value = []
    await loadFormKbBindings()
  } catch (error) {
    console.error('绑定知识库失败:', error)
    ElMessage.error('绑定失败，请重试')
  } finally {
    bindingFormKb.value = false
  }
}

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
      await agentApi.bindKb({
        agentId: currentAgent.value.id,
        kbId: kb.id,
        kbName: kb.name,
        retrieveTopK: detailKbConfig.value.retrieveTopK,
        scoreThreshold: detailKbConfig.value.scoreThreshold
      })
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

const unbindKB = async (binding) => {
  try {
    await ElMessageBox.confirm(`确定解绑知识库「${binding.kbName}」吗？`, '提示', { type: 'warning' })
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
    await ElMessageBox.confirm(`确定解绑知识库「${kb.kbName}」吗？`, '提示', { type: 'warning' })
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

  testMessages.value.push({ role: 'user', content: testQuery.value })
  const query = testQuery.value
  testQuery.value = ''
  scrollTestChat()

  testLoading.value = true
  testMessages.value.push({ role: 'assistant', content: '' })

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
}

onMounted(() => {
  loadAgents()
  loadModelList()
})
</script>

<style scoped>
/* 样式保持不变，删除工具相关的样式即可 */
.agent-container {
  height: 100%;
  padding: 20px;
  background: #0a0e27;
  overflow-y: auto;
}

/* 页面头部样式 */
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

/* Agent 卡片网格 */
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
  position: relative;
}

.agent-card:hover {
  transform: translateY(-4px);
  border-color: #667eea !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

/* 卡片编辑按钮 */
.card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 8px;
  opacity: 0;
  transition: opacity 0.2s;
  z-index: 1;
}

.agent-card:hover .card-actions {
  opacity: 1;
}

.card-actions .el-button {
  width: 28px;
  height: 28px;
  padding: 0;
  background: rgba(102, 126, 234, 0.2) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
}

.card-actions .el-button--primary:hover {
  background: rgba(102, 126, 234, 0.4) !important;
  transform: scale(1.05);
}

.card-actions .el-button--danger {
  background: rgba(239, 68, 68, 0.2) !important;
  border-color: rgba(239, 68, 68, 0.3) !important;
}

.card-actions .el-button--danger:hover {
  background: rgba(239, 68, 68, 0.4) !important;
  transform: scale(1.05);
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

/* 对话框通用样式 */
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

/* Tab 样式 */
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

/* 表单样式 */
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

/* 表格样式 */
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

/* 知识库绑定区域 */
.kb-binding {
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

/* 描述列表样式 */
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

/* AI 生成区域样式 */
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

/* 测试对话区域 */
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

/* 知识库绑定表格滚动容器 */
.kb-binding .table-scroll-wrapper {
  overflow-x: auto;
  width: 100%;
  margin-top: 16px;
  border-radius: 12px;
}

/* 响应式 */
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
</style>