<template>
  <div class="llm-config">
    <el-divider content-position="left">模型配置</el-divider>

    <el-form-item label="模型配置">
      <el-select
          v-model="localConfig.modelConfigId"
          placeholder="选择模型配置"
          filterable
          clearable
          :loading="modelLoading"
          @change="onModelChange"
      >
        <el-option
            v-for="model in modelList"
            :key="model.id"
            :label="`${model.modelName} (${model.providerName})`"
            :value="model.id"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="模型详情" v-if="selectedModel">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="模型名称">{{ selectedModel.modelName }}</el-descriptions-item>
        <el-descriptions-item label="Temperature">{{ selectedModel.temperature }}</el-descriptions-item>
        <el-descriptions-item label="Max Tokens">{{ selectedModel.maxTokens }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ selectedModel.providerName }}</el-descriptions-item>
      </el-descriptions>
    </el-form-item>

    <el-divider content-position="left">提示词配置</el-divider>

    <el-form-item label="系统提示词">
      <div class="prompt-editor">
        <div class="prompt-toolbar">
          <el-button size="small" type="primary" plain @click="openPromptGenerator">
            <el-icon><MagicStick /></el-icon> AI生成
          </el-button>
          <el-button size="small" @click="localConfig.systemPrompt = ''" plain>清空</el-button>
        </div>
        <el-input
            v-model="localConfig.systemPrompt"
            type="textarea"
            :rows="4"
            placeholder="系统提示词：设定AI的角色、行为准则"
        />
      </div>
    </el-form-item>

    <el-form-item label="用户提示词">
      <div class="prompt-editor">
        <div class="prompt-toolbar">
          <el-dropdown @command="insertToUser">
            <el-button size="small" type="primary" plain>
              插入变量 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 输入变量分组 -->
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

                <!-- 节点输出变量分组 -->
                <el-dropdown-item divided>
                  <strong>📤 节点输出变量（{{ nodeOutputVars.length }}）</strong>
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
          <el-button size="small" @click="localConfig.userPrompt = ''" plain>清空</el-button>
        </div>
        <el-input
            v-model="localConfig.userPrompt"
            type="textarea"
            :rows="6"
            placeholder="用户提示词：具体的任务描述"
        />
      </div>
    </el-form-item>

    <el-divider content-position="left">模型参数</el-divider>

    <el-form-item label="温度">
      <el-slider v-model="localConfig.temperature" :min="0" :max="2" :step="0.1" />
    </el-form-item>

    <el-divider content-position="left">输出配置</el-divider>

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

    <!-- 提示词生成器对话框 -->
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
import { systemModelApi, providerApi } from '@/api/modelConfig'  // 使用实际的 API 路径

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})
const emit = defineEmits(['update'])

const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

const localConfig = reactive(props.config)

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
          // 如果当前 localConfig 中没有 modelConfigId，自动设置为系统模型
          if (!localConfig.modelConfigId) {
            localConfig.modelConfigId = systemModel.id
          }
          return
        }
      }

      // 如果系统模型未找到或未配置，则默认选择第一个可用模型
      if (models.length > 0 && !localConfig.modelConfigId) {
        selectedModel.value = models[0]
        localConfig.modelConfigId = models[0].id
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

// 模型变更时更新选中模型详情
const onModelChange = (id) => {
  const model = modelList.value.find(m => m.id === id)
  if (model) {
    selectedModel.value = model
  } else {
    selectedModel.value = null
  }
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

watch(localConfig, (newVal) => {
  emit('update', newVal)
}, { deep: true })

// ========== 生命周期 ==========
onMounted(() => {
  loadChatModels()
})
</script>

<style scoped>
/* 原有样式保持不变 */
.var-code {
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  color: #a78bfa;
}
.var-desc {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}
.prompt-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.form-tip {
  font-size: 12px;
  color: #8b8fa9;
  margin-top: 4px;
}
.field-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
}
.output-fields-editor {
  width: 100%;
}
</style>