<template>
  <div class="prompt-template">
    <div class="template-header">
      <el-button type="primary" @click="showGenerateDialog = true">
        <el-icon><Plus /></el-icon>
        生成提示词
      </el-button>
      <el-button @click="showCreateDialog = true">
        <el-icon><Edit /></el-icon>
        创建模板
      </el-button>
      <el-input
          v-model="searchKeyword"
          placeholder="搜索模板..."
          :prefix-icon="Search"
          style="width: 300px; margin-left: auto"
          clearable
      />
    </div>

    <!-- ==================== 生成提示词对话框 ==================== -->
    <el-dialog v-model="showGenerateDialog" title="生成提示词" width="650px" @close="resetGenerateForm">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="需求描述">
          <el-input
              v-model="generateForm.requirement"
              type="textarea"
              :rows="4"
              placeholder="请描述您需要的提示词功能，例如：你是一个专业的Python编程助手..."
          />
          <div class="form-tip">描述越详细，生成的提示词质量越高</div>
        </el-form-item>

        <el-form-item label="使用模型">
          <el-select
              v-model="generateForm.modelConfigId"
              placeholder="请选择生成提示词使用的大模型"
              filterable
              clearable
              style="width: 100%"
              @change="onModelChange"
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
          <div class="form-tip">选择用于生成提示词的大模型，不选则使用默认模型</div>
        </el-form-item>

        <!-- 显示选中的模型详情 -->
        <el-form-item label="模型详情" v-if="selectedModelDetail">
          <el-tag type="info" size="small">{{ selectedModelDetail.modelName }}</el-tag>
          <span class="model-info">温度: {{ selectedModelDetail.temperature }} | Max Tokens: {{ selectedModelDetail.maxTokens }}</span>
        </el-form-item>

        <el-form-item label="模板类型">
          <el-select v-model="generateForm.type" style="width: 100%">
            <el-option label="RAG问答" value="RAG" />
            <el-option label="函数调用" value="FUNCTION_CALLING" />
            <el-option label="Agent决策" value="AGENT_DECISION" />
            <el-option label="Agent回答" value="AGENT_ANSWER" />
            <el-option label="总结" value="SUMMARY" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>

        <el-form-item label="语言">
          <el-radio-group v-model="generateForm.language">
            <el-radio label="zh-CN">中文</el-radio>
            <el-radio label="en-US">English</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="输出格式">
          <el-checkbox-group v-model="generateForm.outputFormats">
            <el-checkbox label="withSystemPrompt">包含系统提示词</el-checkbox>
            <el-checkbox label="withExamples">包含示例</el-checkbox>
            <el-checkbox label="withVariables">包含变量占位符</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" @click="generatePrompt" :loading="generating">
          <el-icon><MagicStick /></el-icon>
          生成提示词
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 生成结果对话框（可保存） ==================== -->
    <el-dialog
        v-model="showResultDialog"
        title="生成的提示词"
        width="800px"
        top="5vh"
        :key="dialogKey"
    >
      <div class="generate-result">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="模板名称">
            {{ generatedResult.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="版本">
            {{ generatedResult.version || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="使用的模型">
            <el-tag size="small" type="success">
              {{ generatedResult.modelUsed || generateForm.modelConfigId || '默认模型' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="置信度评分">
            <el-tag :type="getConfidenceType(generatedResult.confidenceScore)">
              {{ generatedResult.confidenceScore || 0 }} 分
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="生成时间" :span="2">
            {{ formatDate(generatedResult.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="generatedResult.modelParams && Object.keys(generatedResult.modelParams).length > 0" class="result-section">
          <h4>推荐参数</h4>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="Temperature">
              {{ generatedResult.modelParams.temperature ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Max Tokens">
              {{ generatedResult.modelParams.maxTokens ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Top P">
              {{ generatedResult.modelParams.topP ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Repeat Penalty">
              {{ generatedResult.modelParams.repeatPenalty ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="generatedResult.systemPrompt" class="result-section">
          <h4>
            系统提示词
            <el-button size="small" text @click="copyToClipboard(generatedResult.systemPrompt)">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </h4>
          <div class="prompt-content">
            <pre>{{ generatedResult.systemPrompt }}</pre>
          </div>
        </div>

        <div class="result-section">
          <h4>
            提示词内容
            <el-button size="small" text @click="copyToClipboard(generatedResult.prompt)">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </h4>
          <div class="prompt-content">
            <pre>{{ generatedResult.prompt || '暂无内容' }}</pre>
          </div>
        </div>

        <div v-if="generatedResult.suggestions && generatedResult.suggestions.length > 0" class="result-section">
          <h4>优化建议</h4>
          <ul class="suggestions-list">
            <li v-for="(suggestion, idx) in generatedResult.suggestions" :key="idx">
              <el-icon><Check /></el-icon>
              {{ suggestion }}
            </li>
          </ul>
        </div>
      </div>

      <template #footer>
        <el-button @click="showResultDialog = false">关闭</el-button>
        <el-button type="primary" @click="saveAsDraft" :loading="saving">保存为草稿</el-button>
      </template>
    </el-dialog>

    <!-- 其余模板列表部分保持不变 -->
    <!-- ==================== 查看模板对话框 ==================== -->
    <el-dialog
        v-model="showViewDialog"
        title="模板详情"
        width="800px"
        top="5vh"
    >
      <!-- 内容保持不变 -->
      <div class="generate-result">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="模板名称">{{ viewData.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getTypeLabel(viewData.type) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ viewData.version || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(viewData.status)">
              {{ getStatusLabel(viewData.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(viewData.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ viewData.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="result-section">
          <h4>模板内容</h4>
          <div class="prompt-content">
            <pre>{{ viewData.template || '暂无内容' }}</pre>
          </div>
        </div>

        <div v-if="viewData.modelParams && Object.keys(viewData.modelParams).length > 0" class="result-section">
          <h4>推荐参数</h4>
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="Temperature">{{ viewData.modelParams.temperature ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="Max Tokens">{{ viewData.modelParams.maxTokens ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="Top P">{{ viewData.modelParams.topP ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="Repeat Penalty">{{ viewData.modelParams.repeatPenalty ?? '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <template #footer>
        <el-button @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 创建/编辑模板对话框 ==================== -->
    <el-dialog v-model="showCreateDialog" :title="editingTemplate ? '编辑模板' : '创建模板'" width="800px" @close="resetTemplateForm">
      <el-form :model="templateForm" label-width="100px" ref="templateFormRef" :rules="templateRules">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="templateForm.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板类型" prop="type">
          <el-select v-model="templateForm.type" placeholder="请选择模板类型">
            <el-option label="RAG问答" value="RAG" />
            <el-option label="函数调用" value="FUNCTION_CALLING" />
            <el-option label="Agent决策" value="AGENT_DECISION" />
            <el-option label="Agent回答" value="AGENT_ANSWER" />
            <el-option label="总结" value="SUMMARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板内容" prop="template">
          <el-input
              v-model="templateForm.template"
              type="textarea"
              :rows="6"
              placeholder="请输入提示词模板..."
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" type="textarea" :rows="2" placeholder="请输入描述（可选）" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-slider v-model="templateForm.temperature" :min="0" :max="2" :step="0.1" />
          <span style="margin-left: 12px;">{{ templateForm.temperature }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="templateForm.status">
            <el-radio label="DRAFT">草稿</el-radio>
            <el-radio label="ACTIVE">启用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 模板列表 ==================== -->
    <el-table :data="filteredTemplates" stripe v-loading="loading" class="template-table">
      <el-table-column prop="name" label="模板名称" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag>{{ getTypeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="viewTemplate(row)">查看</el-button>
          <el-button text type="primary" size="small" @click="editTemplate(row)">编辑</el-button>
          <el-button text type="primary" size="small" @click="copyTemplate(row)">
            <el-icon><CopyDocument /></el-icon>
            复制
          </el-button>
          <el-button
              v-if="row.status === 'DRAFT'"
              text type="success"
              size="small"
              @click="activateTemplate(row)"
          >
            启用
          </el-button>
          <el-button
              v-if="row.status === 'ACTIVE'"
              text type="warning"
              size="small"
              @click="deactivateTemplate(row)"
          >
            禁用
          </el-button>
          <el-button text type="danger" size="small" @click="deleteTemplate(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Search, CopyDocument, Check, MagicStick } from '@element-plus/icons-vue'
import { promptApi } from '@/api/prompt'
import { modelConfigApi } from '@/api/chat'

// ==================== 响应式变量 ====================
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const templates = ref([])
const searchKeyword = ref('')
const dialogKey = ref(0)

// 模型相关
const modelList = ref([])
const selectedModelDetail = ref(null)

// 对话框控制
const showGenerateDialog = ref(false)
const showCreateDialog = ref(false)
const showResultDialog = ref(false)
const showViewDialog = ref(false)

const editingTemplate = ref(null)
const templateFormRef = ref(null)

// 生成的提示词结果
const generatedResult = ref({
  templateId: '',
  name: '',
  version: '',
  prompt: '',
  systemPrompt: '',
  userPromptTemplate: '',
  modelParams: {},
  createdAt: null,
  confidenceScore: 0,
  suggestions: [],
  modelUsed: ''
})

// 查看模式数据
const viewData = ref({
  name: '',
  type: '',
  version: '',
  template: '',
  status: '',
  description: '',
  createdAt: null,
  modelParams: {}
})

// 表单数据
const generateForm = ref({
  requirement: '',
  modelConfigId: null,  // 确保这个字段存在
  type: 'RAG',
  language: 'zh-CN',
  outputFormats: ['withSystemPrompt', 'withExamples', 'withVariables']
})

const templateForm = ref({
  name: '',
  type: 'RAG',
  template: '',
  description: '',
  temperature: 0.7,
  status: 'DRAFT'
})

// 表单验证规则
const templateRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  template: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

// ==================== 计算属性 ====================
const filteredTemplates = computed(() => {
  if (!searchKeyword.value) return templates.value
  return templates.value.filter(t =>
      t.name?.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      t.description?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// ==================== 模型相关 ====================
// 加载模型列表
const loadModelList = async () => {
  try {
    const res = await modelConfigApi.getEnabledConfigs()
    console.log('模型列表响应:', res)

    if (res.code === 200 && res.data && res.data.length > 0) {
      modelList.value = res.data
      console.log('加载模型列表成功:', modelList.value)
    } else {
      console.warn('没有可用的模型配置')
      // 尝试使用旧接口获取当前配置
      try {
        const currentRes = await modelConfigApi.getConfig()
        if (currentRes.code === 200 && currentRes.data) {
          modelList.value = [{
            id: 0,
            configName: '当前配置',
            type: currentRes.data.type,
            modelName: currentRes.data.modelName,
            baseUrl: currentRes.data.baseUrl,
            temperature: currentRes.data.temperature,
            maxTokens: currentRes.data.maxTokens
          }]
        }
      } catch (e) {
        console.error('获取当前配置失败:', e)
      }
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
    ElMessage.warning('加载模型列表失败，将使用默认模型')
  }
}

// 获取模型类型显示名称
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

// 模型选择变更 - 添加调试日志
const onModelChange = (configId) => {
  console.log('模型选择变更, configId:', configId)
  console.log('当前模型列表:', modelList.value)

  if (configId) {
    const model = modelList.value.find(m => m.id === configId)
    console.log('找到的模型:', model)
    selectedModelDetail.value = model
    // 确保 generateForm 中的 modelConfigId 被正确设置
    generateForm.value.modelConfigId = configId
    console.log('generateForm.modelConfigId 已设置为:', generateForm.value.modelConfigId)
  } else {
    selectedModelDetail.value = null
    generateForm.value.modelConfigId = null
  }
}

// ==================== 辅助函数 ====================
const getTypeLabel = (type) => {
  const labels = {
    'RAG': 'RAG问答',
    'FUNCTION_CALLING': '函数调用',
    'AGENT_DECISION': 'Agent决策',
    'AGENT_ANSWER': 'Agent回答',
    'SUMMARY': '总结',
    'CUSTOM': '自定义'
  }
  return labels[type] || type
}

const getStatusType = (status) => {
  const types = { 'DRAFT': 'info', 'ACTIVE': 'success', 'ARCHIVED': 'warning' }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = { 'DRAFT': '草稿', 'ACTIVE': '启用', 'ARCHIVED': '归档' }
  return labels[status] || status
}

const getConfidenceType = (score) => {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'info'
}

const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleString()
}

const copyToClipboard = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const resetGenerateForm = () => {
  generateForm.value = {
    requirement: '',
    modelConfigId: null,
    type: 'RAG',
    language: 'zh-CN',
    outputFormats: ['withSystemPrompt', 'withExamples', 'withVariables']
  }
  selectedModelDetail.value = null
  console.log('重置生成表单, modelConfigId:', generateForm.value.modelConfigId)
}

const resetTemplateForm = () => {
  templateForm.value = {
    name: '',
    type: 'RAG',
    template: '',
    description: '',
    temperature: 0.7,
    status: 'DRAFT'
  }
  editingTemplate.value = null
  templateFormRef.value?.clearValidate()
}

// ==================== API 调用 ====================
// 加载模板列表
const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await promptApi.listTemplates()
    console.log('模板列表响应:', res)

    if (res && res.code === 200 && res.data) {
      templates.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (error) {
    console.error('加载模板失败:', error)
    ElMessage.error('加载模板列表失败')
  } finally {
    loading.value = false
  }
}

// 生成提示词 - 修复版
const generatePrompt = async () => {
  if (!generateForm.value.requirement) {
    ElMessage.warning('请输入需求描述')
    return
  }

  generating.value = true
  try {
    // 构建请求参数，包含模型配置ID
    const requestData = {
      requirement: generateForm.value.requirement,
      type: generateForm.value.type,
      language: generateForm.value.language,
      outputFormat: 'JSON'  // 修改为后端期望的字段名
    }

    // 添加输出格式选项
    if (generateForm.value.outputFormats) {
      requestData.outputFormats = generateForm.value.outputFormats
    }

    // 关键修复：如果选择了模型，传递模型配置ID
    console.log('当前 generateForm.value.modelConfigId:', generateForm.value.modelConfigId)
    console.log('当前 selectedModelDetail.value:', selectedModelDetail.value)

    if (generateForm.value.modelConfigId) {
      requestData.modelConfigId = generateForm.value.modelConfigId
      console.log('传递模型配置ID:', requestData.modelConfigId)
    } else if (selectedModelDetail.value && selectedModelDetail.value.id) {
      // 备用方案：从 selectedModelDetail 获取
      requestData.modelConfigId = selectedModelDetail.value.id
      console.log('从 selectedModelDetail 获取模型配置ID:', requestData.modelConfigId)
    } else {
      console.log('未选择模型，将使用默认模型')
    }

    console.log('最终请求数据:', requestData)

    const res = await promptApi.generate(requestData)

    if (res && res.code === 200 && res.data) {
      const data = res.data

      showGenerateDialog.value = false

      generatedResult.value = {
        templateId: data.templateId || Date.now().toString(),
        name: data.name || generateForm.value.requirement.substring(0, 30) + '提示词',
        version: data.version || 'v1.0.0',
        prompt: data.prompt || '',
        systemPrompt: data.systemPrompt || '',
        userPromptTemplate: data.userPromptTemplate || '',
        modelParams: data.modelParams || {
          temperature: 0.5,
          maxTokens: 2048,
          topP: 0.9,
          repeatPenalty: 1.1
        },
        createdAt: data.createdAt ? new Date(data.createdAt) : new Date(),
        confidenceScore: data.confidenceScore || 80,
        suggestions: data.suggestions || [],
        modelUsed: selectedModelDetail.value ? `${selectedModelDetail.value.configName} (${selectedModelDetail.value.modelName})` : '默认模型'
      }

      dialogKey.value++
      showResultDialog.value = true
      ElMessage.success('生成成功')
    } else {
      ElMessage.error(res?.msg || '生成失败')
    }
  } catch (error) {
    console.error('生成失败:', error)
    ElMessage.error('生成失败：' + (error.message || '未知错误'))
  } finally {
    generating.value = false
  }
}

// 保存为草稿
const saveAsDraft = async () => {
  const saveData = {
    name: generatedResult.value.name || '新模板',
    type: generateForm.value.type,
    template: generatedResult.value.prompt || '',
    systemPrompt: generatedResult.value.systemPrompt,
    description: `通过AI生成的${generateForm.value.type}类型提示词模板`,
    temperature: generatedResult.value.modelParams?.temperature || 0.7,
    status: 'DRAFT'
  }

  saving.value = true
  try {
    const res = await promptApi.saveTemplate(saveData)

    if (res && res.code === 200) {
      ElMessage.success('已保存为草稿')
      showResultDialog.value = false
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 保存/更新模板
const saveTemplate = async () => {
  try {
    await templateFormRef.value?.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    let res
    if (editingTemplate.value) {
      res = await promptApi.updateTemplate(editingTemplate.value.id, templateForm.value)
    } else {
      res = await promptApi.saveTemplate(templateForm.value)
    }

    if (res && res.code === 200) {
      ElMessage.success(editingTemplate.value ? '更新成功' : '创建成功')
      showCreateDialog.value = false
      resetTemplateForm()
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 编辑模板
const editTemplate = async (template) => {
  editingTemplate.value = template
  try {
    const res = await promptApi.getTemplate(template.id)
    if (res && res.code === 200 && res.data) {
      const data = res.data
      templateForm.value = {
        name: data.name || template.name,
        type: data.type || template.type,
        template: data.template || template.template,
        description: data.description || template.description || '',
        temperature: data.modelParams?.temperature || template.modelParams?.temperature || 0.7,
        status: data.status || template.status || 'DRAFT'
      }
    } else {
      templateForm.value = {
        name: template.name,
        type: template.type,
        template: template.template,
        description: template.description || '',
        temperature: template.modelParams?.temperature || 0.7,
        status: template.status || 'DRAFT'
      }
    }
    showCreateDialog.value = true
  } catch (error) {
    console.error('获取模板详情失败:', error)
    ElMessage.error('获取模板详情失败')
  }
}

// 查看模板
const viewTemplate = (template) => {
  viewData.value = {
    name: template.name,
    type: template.type,
    version: template.version,
    template: template.template,
    status: template.status,
    description: template.description || '',
    createdAt: template.createdAt,
    modelParams: template.modelParams || {}
  }
  showViewDialog.value = true
}

// 复制模板
const copyTemplate = async (template) => {
  try {
    const newName = `${template.name} (副本)`
    const res = await promptApi.copyTemplate(template.id, newName)

    if (res && res.code === 200) {
      ElMessage.success('复制成功')
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '复制失败')
    }
  } catch (error) {
    console.error('复制失败:', error)
    ElMessage.error('复制失败：' + (error.message || '未知错误'))
  }
}

// 启用模板
const activateTemplate = async (template) => {
  try {
    await ElMessageBox.confirm(`确定启用模板「${template.name}」吗？`, '提示', { type: 'info' })
    const res = await promptApi.setStatus(template.id, 'ACTIVE')

    if (res && res.code === 200) {
      ElMessage.success('启用成功')
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '启用失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('启用失败:', error)
      ElMessage.error('启用失败：' + (error.message || '未知错误'))
    }
  }
}

// 禁用模板
const deactivateTemplate = async (template) => {
  try {
    await ElMessageBox.confirm(`确定禁用模板「${template.name}」吗？`, '提示', { type: 'warning' })
    const res = await promptApi.setStatus(template.id, 'ARCHIVED')

    if (res && res.code === 200) {
      ElMessage.success('禁用成功')
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '禁用失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('禁用失败:', error)
      ElMessage.error('禁用失败：' + (error.message || '未知错误'))
    }
  }
}

// 删除模板
const deleteTemplate = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此模板吗？', '提示', { type: 'warning' })
    const res = await promptApi.deleteTemplate(id)

    if (res && res.code === 200) {
      ElMessage.success('删除成功')
      await loadTemplates()
    } else {
      ElMessage.error(res?.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败：' + (error.message || '未知错误'))
    }
  }
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadTemplates()
  loadModelList()
})
</script>

<style scoped>
.prompt-template {
  padding: 24px;
  min-height: 100%;
  background: #0a0e27;
}

/* ========== 头部区域 ========== */
.template-header {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  align-items: center;
  flex-wrap: wrap;
}

/* 按钮样式 */
.template-header .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  color: #ffffff !important;
  font-weight: 500;
  transition: all 0.3s ease;
}

.template-header .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.template-header .el-button--default {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  color: #cbd5e6 !important;
  transition: all 0.3s ease;
}

.template-header .el-button--default:hover {
  background: #2a2f4a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
  transform: translateY(-1px);
}

/* 搜索框样式 */
.template-header .el-input {
  background: #1a1f3a !important;
  border-radius: 12px;
}

.template-header :deep(.el-input__wrapper) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px;
  box-shadow: none !important;
}

.template-header :deep(.el-input__wrapper:hover) {
  border-color: #667eea !important;
}

.template-header :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

.template-header :deep(.el-input__inner) {
  color: #ffffff !important;
}

.template-header :deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}

/* ========== 表格样式 ========== */
.template-table {
  margin-top: 20px;
  border-radius: 16px;
  overflow: hidden;
}

.template-table :deep(.el-table) {
  background: transparent !important;
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
}

.template-table :deep(.el-table__header) {
  background: transparent !important;
}

.template-table :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  padding: 14px 0 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.template-table :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
  padding: 12px 0 !important;
  font-size: 13px !important;
}

.template-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.06) !important;
}

.template-table :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: rgba(255, 255, 255, 0.02) !important;
}

/* 表格内标签样式 */
.template-table :deep(.el-tag) {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
  border-radius: 12px;
  padding: 0 10px;
  height: 26px;
  line-height: 24px;
}

.template-table :deep(.el-tag--success) {
  background: rgba(16, 185, 129, 0.15) !important;
  border-color: rgba(16, 185, 129, 0.3) !important;
  color: #34d399 !important;
}

.template-table :deep(.el-tag--warning) {
  background: rgba(245, 158, 11, 0.15) !important;
  border-color: rgba(245, 158, 11, 0.3) !important;
  color: #fbbf24 !important;
}

.template-table :deep(.el-tag--info) {
  background: rgba(100, 116, 139, 0.15) !important;
  border-color: rgba(100, 116, 139, 0.3) !important;
  color: #94a3b8 !important;
}

/* 表格内按钮样式 */
.template-table :deep(.el-button--primary.is-text) {
  color: #818cf8 !important;
  font-weight: 500;
  transition: all 0.2s ease;
}

.template-table :deep(.el-button--primary.is-text:hover) {
  color: #a78bfa !important;
  background: rgba(102, 126, 234, 0.1) !important;
}

.template-table :deep(.el-button--success.is-text) {
  color: #34d399 !important;
}

.template-table :deep(.el-button--success.is-text:hover) {
  color: #6ee7b7 !important;
  background: rgba(52, 211, 153, 0.1) !important;
}

.template-table :deep(.el-button--warning.is-text) {
  color: #fbbf24 !important;
}

.template-table :deep(.el-button--warning.is-text:hover) {
  color: #fcd34d !important;
  background: rgba(245, 158, 11, 0.1) !important;
}

.template-table :deep(.el-button--danger.is-text) {
  color: #f87171 !important;
}

.template-table :deep(.el-button--danger.is-text:hover) {
  color: #fca5a5 !important;
  background: rgba(239, 68, 68, 0.1) !important;
}

/* 操作列按钮组 */
.template-table :deep(.el-table__cell .el-button) {
  margin: 0 4px;
  padding: 4px 8px;
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
  padding: 24px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 24px;
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

/* 文本域样式 */
:deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  color: #ffffff !important;
}

:deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

:deep(.el-textarea__inner::placeholder) {
  color: #64748b !important;
}

/* 下拉选择器 */
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

/* 单选框 */
:deep(.el-radio__label) {
  color: #cbd5e6 !important;
}

:deep(.el-radio.is-checked .el-radio__label) {
  color: #a78bfa !important;
}

:deep(.el-radio__inner) {
  background: #0f1228 !important;
  border-color: #2a2f4a !important;
}

:deep(.el-radio.is-checked .el-radio__inner) {
  background: #667eea !important;
  border-color: #667eea !important;
}

/* 复选框 */
:deep(.el-checkbox__label) {
  color: #cbd5e6 !important;
}

:deep(.el-checkbox.is-checked .el-checkbox__label) {
  color: #a78bfa !important;
}

:deep(.el-checkbox__inner) {
  background: #0f1228 !important;
  border-color: #2a2f4a !important;
}

:deep(.el-checkbox.is-checked .el-checkbox__inner) {
  background: #667eea !important;
  border-color: #667eea !important;
}

/* 滑块 */
:deep(.el-slider__runway) {
  background-color: #2a2f4a;
}

:deep(.el-slider__bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

:deep(.el-slider__button) {
  border-color: #667eea;
}

/* 描述列表 */
:deep(.el-descriptions) {
  --el-descriptions-table-bg: transparent !important;
}

:deep(.el-descriptions__label) {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  border-color: #2a2f4a !important;
  font-weight: 500;
}

:deep(.el-descriptions__content) {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
  border-color: #2a2f4a !important;
}

/* 对话框底部按钮 */
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

/* ========== 生成结果区域 ========== */
.generate-result {
  max-height: 70vh;
  overflow-y: auto;
  padding: 4px;
}

.generate-result::-webkit-scrollbar {
  width: 6px;
}

.generate-result::-webkit-scrollbar-track {
  background: #0f1228;
  border-radius: 3px;
}

.generate-result::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

.result-section {
  margin-top: 24px;
}

.result-section h4 {
  margin-bottom: 12px;
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-section h4 .el-button {
  color: #818cf8 !important;
}

.result-section h4 .el-button:hover {
  color: #a78bfa !important;
}

.prompt-content {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.prompt-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #cbd5e6;
}

.suggestions-list {
  margin: 0;
  padding-left: 20px;
  list-style: none;
}

.suggestions-list li {
  margin: 8px 0;
  color: #cbd5e6;
  display: flex;
  align-items: center;
  gap: 8px;
}

.suggestions-list li .el-icon {
  color: #34d399;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.model-info {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 12px;
}

/* ========== 消息提示框 ========== */
:deep(.el-message) {
  background: #1a1f3a !important;
  backdrop-filter: blur(20px);
  border: 1px solid #2a2f4a !important;
  border-radius: 12px;
}

:deep(.el-message__content) {
  color: #ffffff !important;
}

:deep(.el-message-box) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px;
}

:deep(.el-message-box__title) {
  color: #ffffff !important;
}

:deep(.el-message-box__content) {
  color: #cbd5e6 !important;
}

:deep(.el-message-box__btns .el-button--default) {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

:deep(.el-message-box__btns .el-button--primary) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .prompt-template {
    padding: 16px;
  }

  .template-header {
    flex-direction: column;
    align-items: stretch;
  }

  .template-header .el-input {
    width: 100% !important;
    margin-left: 0 !important;
  }

  :deep(.el-dialog) {
    width: 95% !important;
    margin: 20px auto !important;
  }

  .template-table :deep(.el-table__header th),
  .template-table :deep(.el-table__body td) {
    font-size: 11px;
    padding: 8px 0;
  }

  .template-table :deep(.el-button--small) {
    padding: 4px 6px;
    font-size: 11px;
  }
}
</style>