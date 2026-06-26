<template>
  <!-- 模板部分保持不变，与之前完全一致 -->
  <div class="llm-config">
    <!-- ===== 模型配置（含参数） ===== -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">模型配置</span>
      </template>

      <!-- 模型选择：自定义下拉 -->
      <el-form-item label="模型">
        <div class="model-selector">
          <el-dropdown trigger="click" @command="selectModel">
            <div class="model-trigger">
              <span v-if="selectedModelLabel" class="model-value">{{ selectedModelLabel }}</span>
              <span v-else class="model-placeholder">请选择模型</span>
              <el-icon class="model-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                    v-for="model in modelList"
                    :key="model.id"
                    :command="model.id"
                >
                  {{ model.modelName }} ({{ model.providerName }})
                </el-dropdown-item>
                <el-dropdown-item v-if="modelList.length === 0" disabled>
                  暂无模型
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" plain @click="loadChatModels" class="refresh-btn" :loading="modelLoading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </el-form-item>

      <!-- 模型详情 -->
      <el-form-item label="模型详情" v-if="selectedModel">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="模型名称">{{ selectedModel.modelName }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ selectedModel.providerName }}</el-descriptions-item>
          <el-descriptions-item label="Temperature">{{ selectedModel.temperature }}</el-descriptions-item>
          <el-descriptions-item label="Max Tokens">{{ selectedModel.maxTokens }}</el-descriptions-item>
        </el-descriptions>
      </el-form-item>

      <!-- 模型参数（温度 + 最大Token） -->
      <el-divider content-position="left" style="margin: 16px 0 12px 0;">
        <span style="font-size: 13px; color: #94a3b8;">参数设置</span>
      </el-divider>

      <el-form-item label="温度">
        <el-slider v-model="localConfig.temperature" :min="0" :max="2" :step="0.1" class="custom-slider" />
      </el-form-item>

      <el-form-item label="最大 Token">
        <el-slider
            v-model="localConfig.maxTokens"
            :min="1"
            :max="maxTokensLimit"
            :step="1"
            :marks="{ [maxTokensLimit]: `${maxTokensLimit}` }"
            class="custom-slider"
        />
        <div class="slider-value">当前值：{{ localConfig.maxTokens || 512 }}</div>
      </el-form-item>
    </el-card>

    <!-- ===== 提示词配置（保持不变） ===== -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">提示词配置</span>
      </template>

      <!-- 系统提示词 -->
      <div class="prompt-section">
        <div class="prompt-label">
          <span class="label-text">系统提示词</span>
          <span class="label-badge">定义 AI 的角色与行为准则</span>
        </div>
        <div class="prompt-editor">
          <div class="prompt-toolbar">
            <el-button size="small" type="primary" plain @click="openPromptGenerator">
              <el-icon><MagicStick /></el-icon> AI生成
            </el-button>
            <el-button size="small" text @click="localConfig.systemPrompt = ''">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-input
              v-model="localConfig.systemPrompt"
              type="textarea"
              :rows="12"
              placeholder="设定 AI 的角色、行为准则和限制条件…"
              class="prompt-textarea"
          />
        </div>
      </div>

      <!-- 用户提示词 -->
      <div class="prompt-section" style="margin-top: 20px;">
        <div class="prompt-label">
          <span class="label-text">用户提示词</span>
          <span class="label-badge">用户输入与任务描述</span>
        </div>
        <div class="prompt-editor">
          <div class="prompt-toolbar">
            <el-dropdown @command="insertToUser" trigger="click">
              <el-button size="small" plain>
                <el-icon><Plus /></el-icon> 插入变量
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item divided>
                    <strong>📥 输入变量</strong>
                  </el-dropdown-item>
                  <el-dropdown-item
                      v-for="input in inputVarList"
                      :key="`input.${input.name}`"
                      :command="`input.${input.name}`"
                  >
                    <span class="var-code">{{ '{' }}{{ '{' }}input.{{ input.name }}{{ '}' }}{{ '}' }}</span>
                    <span class="var-desc"> - {{ input.description || input.name }}</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided>
                    <strong>📤 节点输出变量</strong>
                  </el-dropdown-item>
                  <el-dropdown-item
                      v-for="v in nodeOutputVars"
                      :key="v.nodeId"
                      :command="`var.${v.outputVar}`"
                  >
                    <span class="var-code">{{ '{' }}{{ '{' }}var.{{ v.outputVar }}{{ '}' }}{{ '}' }}</span>
                    <span class="var-desc"> - {{ v.nodeName }}（{{ v.nodeType }}）</span>
                  </el-dropdown-item>
                  <el-dropdown-item v-if="nodeOutputVars.length === 0" disabled>
                    暂无已配置输出变量的前置节点
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button size="small" text @click="localConfig.userPrompt = ''">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-input
              v-model="localConfig.userPrompt"
              type="textarea"
              :rows="2"
              placeholder="输入具体的任务描述，可引用变量…"
              class="prompt-textarea"
          />
        </div>
      </div>
    </el-card>

    <!-- 输出配置（保持不变） -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">输出配置</span>
      </template>

      <el-form-item label="输出变量名" prop="outputVar">
        <el-input v-model="localConfig.outputVar" placeholder="例如: llm_response" />
        <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
      </el-form-item>

      <el-form-item label="输出类型">
        <el-radio-group v-model="localConfig.outputType" @change="onOutputTypeChange">
          <el-radio value="string">字符串</el-radio>
          <el-radio value="json">JSON对象</el-radio>
          <el-radio value="array">数组</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 数组类型配置 -->
      <template v-if="localConfig.outputType === 'array'">
        <el-form-item label="数组项类型">
          <el-radio-group v-model="localConfig.arrayItemType">
            <el-radio value="string">字符串数组</el-radio>
            <el-radio value="object">对象数组</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>

      <!-- JSON对象类型配置 -->
      <template v-if="localConfig.outputType === 'json'">
        <el-form-item label="输出字段">
          <div class="output-fields-editor">
            <div v-for="(field, idx) in localConfig.outputFields" :key="idx" class="field-row">
              <el-input v-model="field.name" placeholder="字段名" size="small" style="width: 120px" />
              <el-select v-model="field.type" placeholder="类型" size="small" style="width: 100px">
                <el-option label="字符串" value="string" />
                <el-option label="数字" value="number" />
                <el-option label="布尔值" value="boolean" />
              </el-select>
              <el-input v-model="field.description" placeholder="描述" size="small" style="width: 150px" />
              <el-button type="danger" size="small" @click="removeField(idx)" :icon="Delete" />
            </div>
            <el-button size="small" type="primary" plain @click="addField">
              <el-icon><Plus /></el-icon> 添加字段
            </el-button>
          </div>
        </el-form-item>
      </template>
    </el-card>

    <!-- 提示词生成器对话框 -->
    <PromptGeneratorDialog
        v-model="showGeneratorDialog"
        @apply="applyGeneratedPrompt"
    />
  </div>
</template>

<script setup>
/* ========== JS 逻辑完全保持原样，仅新增 UI 辅助 ========== */
import { ref, reactive, onMounted, watch, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, MagicStick, Delete, Plus, Refresh } from '@element-plus/icons-vue'
import PromptGeneratorDialog from './PromptGeneratorDialog.vue'
import { systemModelApi, providerApi } from '@/api/modelConfig'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})

const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

const localConfig = props.config

const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

// ========== 动态模型加载 ==========
const modelList = ref([])
const selectedModel = ref(null)
const modelLoading = ref(false)

// UI 辅助：当前选中的模型显示名称
const selectedModelLabel = computed(() => {
  if (!localConfig.modelConfigId) return ''
  const found = modelList.value.find(m => m.id === localConfig.modelConfigId)
  return found ? `${found.modelName} (${found.providerName})` : ''
})

// ★ 核心修改：只加载列表，不自动选中模型
const loadChatModels = async () => {
  // 如果已有列表，不重复加载（可通过刷新按钮强制刷新）
  if (modelList.value.length > 0 && !modelLoading.value) {
    // 但仍需检查是否已有选中的模型需要回显
    if (localConfig.modelConfigId) {
      const found = modelList.value.find(m => m.id === localConfig.modelConfigId)
      if (found) {
        selectedModel.value = found
      }
    }
    return
  }

  modelLoading.value = true
  try {
    // 1. 获取系统配置的聊天模型 ID（仅用于显示高亮，不自动选中）
    let systemChatModelId = null
    const capRes = await systemModelApi.getCapabilities()
    if (capRes.code === 200 && capRes.data) {
      const chatCapability = capRes.data.chat
      if (chatCapability && chatCapability.modelConfigId) {
        systemChatModelId = chatCapability.modelConfigId
      }
    }

    // 2. 获取所有启用的供应商及其聊天模型
    const providerRes = await providerApi.getEnabledProviders()
    if (providerRes.code === 200 && providerRes.data) {
      const models = []
      for (const provider of providerRes.data) {
        try {
          const detailRes = await providerApi.getProviderDetail(provider.id)
          if (detailRes.code === 200 && detailRes.data && detailRes.data.models) {
            const chatModels = detailRes.data.models.filter(
                model => model.capabilityType === 'chat' && model.status === 1
            )
            chatModels.forEach(model => {
              models.push({
                id: model.id,
                modelName: model.modelName,
                modelKey: model.modelKey,
                providerName: provider.providerName,
                providerKey: provider.providerKey,
                temperature: model.defaultTemperature || 0.7,
                maxTokens: model.maxTokens || 4096,
                raw: model
              })
            })
          }
        } catch (e) {
          console.warn(`获取供应商 ${provider.providerName} 的模型失败:`, e)
        }
      }
      modelList.value = models

      // ★ 关键修改：不自动选中任何模型
      // 仅当已有 modelConfigId 时，回显对应的模型
      if (localConfig.modelConfigId) {
        const found = models.find(m => m.id === localConfig.modelConfigId)
        if (found) {
          selectedModel.value = found
          // 如果用户之前未设置 maxTokens，使用模型默认值
          if (!localConfig.maxTokens) {
            localConfig.maxTokens = found.maxTokens
          }
          if (localConfig.temperature === undefined || localConfig.temperature === null) {
            localConfig.temperature = found.temperature ?? 0.7
          }
        }
      } else {
        // 没有已选模型时，清空选中状态
        selectedModel.value = null
      }
    } else {
      ElMessage.warning('未获取到可用的模型供应商')
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.error('加载模型列表失败')
  } finally {
    modelLoading.value = false
  }
}

// 用户手动选择模型
const selectModel = (id) => {
  localConfig.modelConfigId = id
  const found = modelList.value.find(m => m.id === id)
  if (found) {
    selectedModel.value = found
    // 切换模型时自动设置模型默认参数
    localConfig.maxTokens = found.maxTokens
    localConfig.temperature = found.temperature ?? 0.7
  }
}

// ========== 原有功能（保持不变） ==========
const outputVarDisplay = computed(() => {
  const varName = localConfig.outputVar || 'llm_response'
  return `{{var.${varName}}}`
})

const showGeneratorDialog = ref(false)

const onOutputTypeChange = (type) => {
  if (type === 'json' && !localConfig.outputFields?.length) {
    localConfig.outputFields = []
  }
}

const addField = () => {
  if (!localConfig.outputFields) localConfig.outputFields = []
  localConfig.outputFields.push({ name: '', type: 'string', description: '' })
}

const removeField = (index) => {
  localConfig.outputFields.splice(index, 1)
}

const openPromptGenerator = () => {
  showGeneratorDialog.value = true
}

const applyGeneratedPrompt = (prompt) => {
  if (prompt) {
    localConfig.systemPrompt = prompt
    ElMessage.success('提示词已应用')
  }
}

const insertToUser = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.userPrompt = (localConfig.userPrompt || '') + variable
}

const maxTokensLimit = computed(() => {
  return selectedModel.value?.maxTokens || 4096;
});

// 初始化 maxTokens
if (!localConfig.maxTokens && selectedModel.value) {
  localConfig.maxTokens = Math.min(512, selectedModel.value.maxTokens);
}

// ========== 生命周期 ==========
onMounted(() => {
  loadChatModels()
})
</script>

<style scoped>
/* ===== 卡片样式 ===== */
.config-card {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  margin-bottom: 20px;
}
.config-card :deep(.el-card__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 12px 20px;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #e2e8f0;
}

/* ===== 模型选择器 ===== */
.model-selector {
  display: flex;
  gap: 8px;
  align-items: center;
}
.model-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 32px;
  min-width: 180px;
  font-size: 13px;
  flex: 1;
}
.model-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.model-value {
  color: #a78bfa;
}
.model-placeholder {
  color: #64748b;
}
.model-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.model-trigger:hover .model-arrow {
  color: #a78bfa;
}
.refresh-btn {
  background: rgba(102, 126, 234, 0.08) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
  color: #a78bfa !important;
  padding: 5px 8px !important;
}
.refresh-btn:hover {
  background: rgba(102, 126, 234, 0.2) !important;
  border-color: #667eea !important;
}

/* ===== 提示词区域 ===== */
.prompt-section {
  padding: 0 4px;
}
.prompt-label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 8px;
}
.label-text {
  font-size: 13px;
  font-weight: 500;
  color: #e2e8f0;
}
.label-badge {
  font-size: 11px;
  color: #64748b;
  font-weight: 400;
}

.prompt-editor {
  position: relative;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  overflow: hidden;
  background: #0a0e27;
  transition: border-color 0.2s;
}
.prompt-editor:focus-within {
  border-color: #667eea;
}

.prompt-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #13182f;
  border-bottom: 1px solid #2a2f4a;
}
.prompt-toolbar .el-button {
  font-size: 12px;
  color: #94a3b8;
  padding: 4px 10px;
  height: 28px;
}
.prompt-toolbar .el-button:hover {
  color: #e2e8f0;
}
.prompt-toolbar .el-button--primary {
  color: #a78bfa;
}
.prompt-toolbar .el-button--primary:hover {
  color: #c4b5fd;
}
.prompt-toolbar .el-button.is-text {
  color: #64748b;
}
.prompt-toolbar .el-button.is-text:hover {
  color: #ef4444;
}

.prompt-textarea :deep(.el-textarea__inner) {
  background: transparent !important;
  border: none !important;
  color: #e2e8f0;
  font-size: 13px;
  line-height: 1.7;
  padding: 12px 16px;
  resize: vertical;
  box-shadow: none !important;
}
.prompt-textarea :deep(.el-textarea__inner::placeholder) {
  color: #475569;
}

/* ===== 变量代码样式 ===== */
.var-code {
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  color: #a78bfa;
}
.var-desc {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}

/* ===== 输出字段 ===== */
.field-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.output-fields-editor {
  width: 100%;
}

/* ===== 其他组件 ===== */
.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
.form-tip code {
  background: #1a1f3a;
  padding: 1px 6px;
  border-radius: 4px;
  color: #a78bfa;
  font-size: 12px;
}
.slider-value {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

/* ===== 滑块深色覆盖 ===== */
.custom-slider :deep(.el-slider__runway) {
  background: #2a2f4a !important;
}
.custom-slider :deep(.el-slider__bar) {
  background: #667eea !important;
}
.custom-slider :deep(.el-slider__button) {
  background: #667eea !important;
  border-color: #667eea !important;
}

/* ===== 全局深色覆盖（输入框、下拉、按钮、分割线等） ===== */
:deep(.el-input .el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 6px !important;
  box-shadow: none !important;
}
:deep(.el-input .el-input__wrapper:hover) {
  border-color: #667eea !important;
}
:deep(.el-input .el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}
:deep(.el-input .el-input__inner) {
  color: #ffffff !important;
}
:deep(.el-input .el-input__inner::placeholder) {
  color: #64748b !important;
}

:deep(.el-dropdown-menu) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 8px !important;
}
:deep(.el-dropdown-menu .el-dropdown-menu__item) {
  color: #cbd5e6 !important;
  background: transparent !important;
}
:deep(.el-dropdown-menu .el-dropdown-menu__item:hover) {
  background: #2a2f4a !important;
  color: #ffffff !important;
}
:deep(.el-dropdown-menu .el-dropdown-menu__item.is-selected) {
  color: #667eea !important;
  background: rgba(102, 126, 234, 0.1) !important;
}
:deep(.el-dropdown-menu .el-dropdown-menu__item.is-disabled) {
  color: #4a4f6a !important;
  cursor: not-allowed !important;
}

:deep(.el-divider) {
  border-color: #2a2f4a !important;
}
:deep(.el-divider__text) {
  color: #94a3b8 !important;
  font-weight: 500;
  font-size: 13px;
}

:deep(.el-radio-group .el-radio) {
  color: #cbd5e6 !important;
}
:deep(.el-radio-group .el-radio.is-checked) {
  color: #a78bfa !important;
}
:deep(.el-radio-group .el-radio .el-radio__inner) {
  background: #0f1228 !important;
  border-color: #2a2f4a !important;
}
:deep(.el-radio-group .el-radio.is-checked .el-radio__inner) {
  background: #667eea !important;
  border-color: #667eea !important;
}

:deep(.el-descriptions) {
  background: transparent !important;
  border: none !important;
}
:deep(.el-descriptions .el-descriptions__table) {
  border-collapse: collapse;
}
:deep(.el-descriptions .el-descriptions__cell) {
  border-color: #2a2f4a !important;
  background: #0f1228 !important;
  color: #cbd5e6 !important;
}
:deep(.el-descriptions .el-descriptions__label) {
  background: #0a0e27 !important;
  color: #94a3b8 !important;
}
:deep(.el-descriptions .el-descriptions__content) {
  color: #e2e8f0 !important;
}
</style>