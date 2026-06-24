<template>
  <div class="llm-config">
    <!-- ===== 模型配置（含参数） ===== -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">模型配置</span>
      </template>

      <!-- 模型选择 -->
      <el-form-item label="模型">
        <el-select
            v-model="localConfig.modelConfigId"
            placeholder="选择模型"
            filterable
            clearable
            :loading="modelLoading"
            @change="onModelChange"
            style="width:100%"
        >
          <el-option
              v-for="model in modelList"
              :key="model.id"
              :label="`${model.modelName} (${model.providerName})`"
              :value="model.id"
          />
        </el-select>
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
        <el-slider v-model="localConfig.temperature" :min="0" :max="2" :step="0.1" />
      </el-form-item>

      <el-form-item label="最大 Token">
        <el-slider
            v-model="localConfig.maxTokens"
            :min="1"
            :max="maxTokensLimit"
            :step="1"
            :marks="{ [maxTokensLimit]: `${maxTokensLimit}` }"
        />
        <div class="slider-value">当前值：{{ localConfig.maxTokens || 512 }}</div>
      </el-form-item>
    </el-card>

    <!-- ===== 提示词配置 ===== -->
    <el-card class="config-card" shadow="never">
      <template #header>
        <span class="card-title">提示词配置</span>
      </template>

      <!-- 系统提示词（12行） -->
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

      <!-- 用户提示词（2行） -->
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

    <!-- 输出配置 -->
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

    <!-- 提示词生成器对话框（保持不变） -->
    <PromptGeneratorDialog
        v-model="showGeneratorDialog"
        @apply="applyGeneratedPrompt"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, MagicStick, Delete, Plus } from '@element-plus/icons-vue'
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

// ========== 动态模型加载（参考知识库页面） ==========
const modelList = ref([])
const selectedModel = ref(null)
const modelLoading = ref(false)

// 加载聊天模型列表
const loadChatModels = async () => {
  modelLoading.value = true
  try {
    // 1. 获取系统配置的聊天模型 ID
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
            // 筛选出 chat 类型的模型
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
                // 保留原始 model 对象以备后续扩展
                raw: model
              })
            })
          }
        } catch (e) {
          console.warn(`获取供应商 ${provider.providerName} 的模型失败:`, e)
        }
      }
      modelList.value = models

      // 3. 设置默认选中的模型（优先使用系统配置的模型）
      if (systemChatModelId) {
        const systemModel = models.find(m => m.id === systemChatModelId)
        if (systemModel) {
          selectedModel.value = systemModel
          if (!localConfig.modelConfigId) {
            localConfig.modelConfigId = systemModel.id
          }
          if (!localConfig.maxTokens) {
            localConfig.maxTokens = systemModel.maxTokens
          }
          return
        }
      }

      // 如果没有系统模型，选择第一个
      if (models.length > 0 && !localConfig.modelConfigId) {
        selectedModel.value = models[0]
        localConfig.modelConfigId = models[0].id
        localConfig.maxTokens = models[0].maxTokens
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

// 模型变更时更新选中模型详情 + 自动设置 maxTokens
function onModelChange(id) {
  localConfig.modelConfigId = id
}

// ========== 原有功能 ==========
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
// 初始化 maxTokens（如果未设置）
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

/* ===== 其他组件样式（保持原有） ===== */
.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
.field-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
.output-fields-editor {
  width: 100%;
}
.slider-value {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}
</style>