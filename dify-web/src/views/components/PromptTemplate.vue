<template>
  <div class="prompt-template">
    <!-- 头部 -->
    <div class="template-header">
      <div class="header-buttons">
        <el-button
            type="primary"
            @click="showGenerateDialog = true"
            :disabled="!isModelConfigured"
        >
          <el-icon><Plus /></el-icon>
          生成提示词
        </el-button>
        <el-button @click="showCreateDialog = true">
          <el-icon><Edit /></el-icon>
          创建模板
        </el-button>
        <el-button @click="syncToVector" :loading="syncing" type="success" plain>
          <el-icon><Refresh /></el-icon>
          同步到向量库
        </el-button>
      </div>

      <el-input
          v-model="searchKeyword"
          placeholder="搜索模板..."
          :prefix-icon="Search"
          class="header-search"
          clearable
      />
    </div>

    <!-- 向量库状态卡片 -->
    <div class="vector-stats-card" v-if="vectorStats.totalCount > 0">
      <div class="stat-item">
        <div class="stat-value">{{ vectorStats.vectorCount }}</div>
        <div class="stat-label">已同步模板</div>
      </div>
      <div class="stat-item">
        <div class="stat-value">{{ vectorStats.totalCount }}</div>
        <div class="stat-label">启用中的模板</div>
      </div>
      <div class="stat-item">
        <div class="stat-value" :class="{ 'stat-warning': vectorStats.syncRate < 100 }">
          {{ vectorStats.syncRate }}%
        </div>
        <div class="stat-label">同步率</div>
      </div>
      <div class="stat-item">
        <el-button size="small" @click="showVectorStatsDialog = true">
          查看详情
        </el-button>
      </div>
    </div>

    <!-- 模型未配置提示 -->
    <el-alert
        v-if="!isModelConfigured && !modelLoading"
        title="系统模型未配置"
        type="warning"
        description="请先在「系统模型」中配置大语言模型，否则无法生成提示词"
        show-icon
        :closable="false"
        class="model-warning-alert"
    >
      <template #action>
        <el-button type="warning" size="small" @click="goToSystemModel">
          前往配置
        </el-button>
      </template>
    </el-alert>

    <!-- 路由测试区域 -->
    <div class="router-test-section">
      <div class="test-header">
        <span><el-icon><Search /></el-icon> 路由测试</span>
        <el-button text type="primary" @click="showRouterTest = !showRouterTest">
          {{ showRouterTest ? '收起' : '展开' }}
        </el-button>
      </div>
      <div v-show="showRouterTest" class="test-content">
        <div class="test-input-area">
          <el-input
              v-model="testQuery"
              placeholder="输入测试问题，测试系统会选择哪个模板..."
              @keyup.enter="testRouting"
          />
          <el-button type="primary" @click="testRouting" :loading="testing">
            测试路由
          </el-button>
        </div>
        <div v-if="testResult" class="test-result-area">
          <div class="result-header">
            <span>路由结果</span>
            <el-tag :type="getTypeTagType(testResult.type)">{{ getTypeLabel(testResult.type) }}</el-tag>
          </div>
          <div class="result-content">
            <div><strong>选中模板：</strong>{{ testResult.name }}</div>
            <div><strong>相似度：</strong>{{ testResult.score || '-' }}</div>
            <div class="template-preview">
              <strong>模板预览：</strong>
              <pre>{{ testResult.templatePreview }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 生成提示词对话框 -->
    <el-dialog v-model="showGenerateDialog" title="生成提示词" width="650px" @close="resetGenerateForm">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="使用模型">
          <div class="model-info-display">
            <el-tag v-if="isModelConfigured" type="success" size="large">
              <el-icon><Cpu /></el-icon>
              {{ currentModelDisplay }}
            </el-tag>
            <el-tag v-else type="danger" size="large">
              <el-icon><Warning /></el-icon>
              未配置模型
            </el-tag>
            <el-link
                type="primary"
                :underline="false"
                @click="goToSystemModel"
                style="margin-left: 12px"
            >
              切换模型
            </el-link>
          </div>
        </el-form-item>

        <el-form-item label="需求描述" required>
          <el-input
              v-model="generateForm.requirement"
              type="textarea"
              :rows="4"
              placeholder="请描述您需要的提示词功能，例如：你是一个专业的Python编程助手..."
          />
        </el-form-item>

        <el-form-item label="模板类型">
          <el-select v-model="generateForm.type" style="width: 100%" clearable>
            <el-option label="💬 通用聊天" value="GENERAL" />
            <el-option label="💬 自定义" value="CUSTOM" />
            <el-option label="📝 内容总结" value="SUMMARY" />
            <el-option label="🤖 Agent回答" value="AGENT_ANSWER" />
            <el-option label="🔧 函数调用" value="FUNCTION_CALLING" />
            <el-option label="🧠 Agent决策" value="AGENT_DECISION" />
            <el-option label="📚 RAG问答" value="RAG" />
            <el-option label="💻 代码生成" value="CODE" />
            <el-option label="🎨 创意写作" value="CREATIVE" />
            <el-option label="📊 数据分析" value="DATA" />
            <el-option label="⚡ 流式对话" value="STREAMING" />
          </el-select>
        </el-form-item>

        <el-form-item label="语言">
          <el-radio-group v-model="generateForm.language">
            <el-radio value="zh-CN">中文</el-radio>
            <el-radio value="en-US">English</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="输出格式">
          <el-radio-group v-model="generateForm.outputFormat">
            <el-radio value="TEXT">文本格式</el-radio>
            <el-radio value="JSON">JSON格式</el-radio>
            <el-radio value="MARKDOWN">Markdown格式</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button
            type="primary"
            @click="generatePrompt"
            :loading="generating"
            :disabled="!isModelConfigured || !generateForm.requirement"
        >
          <el-icon><MagicStick /></el-icon>
          生成提示词
        </el-button>
      </template>
    </el-dialog>

    <!-- 生成结果对话框 -->
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
          <el-descriptions-item label="生成时间" :span="2">
            {{ formatDate(generatedResult.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="generatedResult.modelParams" class="result-section">
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

        <div class="result-section">
          <h4>
            生成的提示词
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

    <!-- 查看模板对话框 -->
    <el-dialog v-model="showViewDialog" title="模板详情" width="800px" top="5vh">
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
          <el-descriptions-item label="向量状态">
            <el-tag :type="viewData.inVector ? 'success' : 'info'">
              {{ viewData.inVector ? '已同步' : '未同步' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(viewData.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ viewData.description || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="result-section">
          <h4>模板内容</h4>
          <div class="prompt-content">
            <pre>{{ viewData.template || '暂无内容' }}</pre>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 创建/编辑模板对话框 -->
    <el-dialog v-model="showCreateDialog" :title="editingTemplate ? '编辑模板' : '创建模板'" width="800px" @close="resetTemplateForm">
      <el-form :model="templateForm" label-width="100px" ref="templateFormRef" :rules="templateRules">
        <el-form-item label="模板名称" prop="name">
          <el-input v-model="templateForm.name" placeholder="请输入模板名称" />
        </el-form-item>
        <el-form-item label="模板类型" prop="type">
          <el-select v-model="templateForm.type" placeholder="请选择模板类型" clearable>
            <el-option label="💬 通用聊天" value="GENERAL" />
            <el-option label="💬 自定义" value="CUSTOM" />
            <el-option label="📝 内容总结" value="SUMMARY" />
            <el-option label="🤖 Agent回答" value="AGENT_ANSWER" />
            <el-option label="🔧 函数调用" value="FUNCTION_CALLING" />
            <el-option label="🧠 Agent决策" value="AGENT_DECISION" />
            <el-option label="📚 RAG问答" value="RAG" />
            <el-option label="💻 代码生成" value="CODE" />
            <el-option label="🎨 创意写作" value="CREATIVE" />
            <el-option label="📊 数据分析" value="DATA" />
            <el-option label="⚡ 流式对话" value="STREAMING" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板内容" prop="template">
          <el-input
              v-model="templateForm.template"
              type="textarea"
              :rows="10"
              placeholder="请输入提示词模板..."
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" type="textarea" :rows="2" placeholder="请输入描述（可选）" />
        </el-form-item>
        <!-- 修改 Temperature 表单项 -->
        <el-form-item label="Temperature">
          <el-slider
              v-model="templateForm.modelParams.temperature"
              :min="0"
              :max="2"
              :step="0.1"
          />
          <span style="margin-left: 12px;">{{ templateForm.modelParams.temperature }}</span>
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

    <!-- 向量库统计详情对话框 -->
    <el-dialog v-model="showVectorStatsDialog" title="向量库详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="集合名称">prompt_templates</el-descriptions-item>
        <el-descriptions-item label="向量库中的模板数">{{ vectorStats.vectorCount }}</el-descriptions-item>
        <el-descriptions-item label="启用中的模板数">{{ vectorStats.totalCount }}</el-descriptions-item>
        <el-descriptions-item label="同步率">{{ vectorStats.syncRate }}%</el-descriptions-item>
        <el-descriptions-item label="未同步模板">
          <div v-if="vectorStats.unsyncedTemplates.length > 0">
            <el-tag
                v-for="tpl in vectorStats.unsyncedTemplates"
                :key="tpl.id"
                size="small"
                style="margin: 2px"
            >
              {{ tpl.name }}
            </el-tag>
          </div>
          <span v-else>无</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="syncToVector" :loading="syncing">立即同步</el-button>
        <el-button @click="showVectorStatsDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 模板列表 -->
    <el-table :data="filteredTemplates" stripe v-loading="loading" class="template-table">
      <el-table-column prop="name" label="模板名称" min-width="180" />
      <el-table-column prop="type" label="类型" width="130">
        <template #default="{ row }">
          <el-tag :type="getTypeTagType(row.type)" effect="light">
            {{ getTypeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="向量状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.inVector" type="success" size="small">已同步</el-tag>
          <el-tag v-else type="info" size="small">未同步</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <div class="op-buttons">
            <el-button size="small" class="btn-purple" @click="viewTemplate(row)">查看</el-button>
            <el-button size="small" class="btn-purple" @click="editTemplate(row)">编辑</el-button>
            <el-button size="small" class="btn-purple" @click="copyTemplate(row)">
              <el-icon><CopyDocument /></el-icon>复制
            </el-button>

            <el-button
                v-if="row.status === 'DRAFT'"
                size="small"
                class="btn-green"
                @click="activateTemplate(row)"
            >启用</el-button>

            <el-button
                v-if="row.status === 'ACTIVE'"
                size="small"
                class="btn-orange"
                @click="deactivateTemplate(row)"
            >禁用</el-button>

            <el-button size="small" class="btn-red" @click="deleteTemplate(row.id)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Search, CopyDocument, Check, MagicStick, Cpu, Warning, Refresh } from '@element-plus/icons-vue'
import { promptApi } from '@/api/prompt'
import { systemModelApi, providerApi } from '@/api/modelConfig'

const router = useRouter()

// 响应式变量
const loading = ref(false)
const generating = ref(false)
const saving = ref(false)
const syncing = ref(false)
const testing = ref(false)
const templates = ref([])
const searchKeyword = ref('')
const dialogKey = ref(0)
const showRouterTest = ref(true)

// 模型相关
const currentModel = ref(null)
const modelLoading = ref(false)

// 向量库相关
const vectorStats = ref({
  totalCount: 0,
  vectorCount: 0,
  syncRate: 0,
  unsyncedTemplates: []
})

// 测试相关
const testQuery = ref('')
const testResult = ref(null)

// 对话框控制
const showGenerateDialog = ref(false)
const showCreateDialog = ref(false)
const showResultDialog = ref(false)
const showViewDialog = ref(false)
const showVectorStatsDialog = ref(false)

const editingTemplate = ref(null)
const templateFormRef = ref(null)

// 生成的提示词结果
const generatedResult = ref({
  name: '',
  version: '',
  prompt: '',
  systemPrompt: '',
  modelParams: {},
  createdAt: null,
  confidenceScore: 0,
  suggestions: []
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
  inVector: false
})

// 表单数据
const generateForm = ref({
  requirement: '',
  type: 'GENERAL',
  language: 'zh-CN',
  outputFormat: 'TEXT'
})

const templateForm = ref({
  name: '',
  type: 'GENERAL',
  template: '',
  description: '',
  modelParams: {
    temperature: 0.7,
    maxTokens: 2048,
    topP: 0.9,
    repeatPenalty: 1.1
  },
  status: 'DRAFT'
})

// 表单验证规则
const templateRules = {
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择模板类型', trigger: 'change' }],
  template: [{ required: true, message: '请输入模板内容', trigger: 'blur' }]
}

// 计算属性
const filteredTemplates = computed(() => {
  if (!searchKeyword.value) return templates.value
  return templates.value.filter(t =>
      t.name?.toLowerCase().includes(searchKeyword.value.toLowerCase()) ||
      t.description?.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

const isModelConfigured = computed(() => {
  return currentModel.value !== null && currentModel.value.id
})

const currentModelDisplay = computed(() => {
  if (!currentModel.value) return '未配置'
  return `${currentModel.value.modelName} (${currentModel.value.providerName || currentModel.value.type})`
})

// ==================== 向量库管理方法 ====================

// 加载向量库状态
const loadVectorStats = async () => {
  try {
    // 获取所有ACTIVE模板
    const activeTemplates = templates.value.filter(t => t.status === 'ACTIVE')
    const totalCount = activeTemplates.length

    // 获取向量库中的模板
    const vectorRes = await promptApi.listAllPromptTemplates()
    console.log('向量库模板列表:', vectorRes)

    const vectorIds = new Set()
    if (vectorRes.code === 200 && vectorRes.data) {
      vectorRes.data.forEach(item => {
        // 根据实际返回的数据结构获取templateId
        // 从日志看，返回的数据包含 templateId 字段
        const templateId = item.templateId || item.id
        if (templateId) {
          vectorIds.add(templateId)
        }
      })
    }

    console.log('向量库中的模板ID:', [...vectorIds])
    console.log('ACTIVE模板ID:', activeTemplates.map(t => t.id))

    // 更新模板的向量状态
    templates.value = templates.value.map(t => ({
      ...t,
      inVector: vectorIds.has(t.id)
    }))

    // 计算统计信息
    const vectorCount = activeTemplates.filter(t => vectorIds.has(t.id)).length
    const syncRate = totalCount > 0 ? Math.round((vectorCount / totalCount) * 100) : 100
    const unsyncedTemplates = activeTemplates.filter(t => !vectorIds.has(t.id))

    console.log('统计结果: totalCount=', totalCount, 'vectorCount=', vectorCount, 'syncRate=', syncRate)

    vectorStats.value = {
      totalCount,
      vectorCount,
      syncRate,
      unsyncedTemplates
    }
  } catch (error) {
    console.error('加载向量库状态失败:', error)
  }
}

// 同步到向量库
const syncToVector = async () => {
  syncing.value = true
  try {
    const res = await promptApi.syncAllToVector()
    if (res.code === 200) {
      ElMessage.success('同步成功')
      await loadVectorStats()
    } else {
      ElMessage.error(res.msg || '同步失败')
    }
  } catch (error) {
    console.error('同步失败:', error)
    ElMessage.error('同步失败：' + (error.message || '未知错误'))
  } finally {
    syncing.value = false
  }
}

// 测试路由
const testRouting = async () => {
  if (!testQuery.value.trim()) {
    ElMessage.warning('请输入测试问题')
    return
  }

  testing.value = true
  try {
    const res = await promptApi.route({ query: testQuery.value })
    if (res.code === 200 && res.data) {
      const template = res.data
      testResult.value = {
        name: template.name,
        type: template.type,
        score: template.score || '高',
        templatePreview: template.template?.substring(0, 500) + (template.template?.length > 500 ? '...' : '')
      }
      ElMessage.success(`路由结果: ${template.name}`)
    } else {
      ElMessage.error(res.msg || '路由失败')
    }
  } catch (error) {
    console.error('路由测试失败:', error)
    ElMessage.error('路由测试失败')
  } finally {
    testing.value = false
  }
}

// 模型相关
const loadSystemChatModel = async () => {
  modelLoading.value = true
  try {
    const capRes = await systemModelApi.getCapabilities()
    if (capRes.code !== 200 || !capRes.data) {
      currentModel.value = null
      return
    }

    const chatCapability = capRes.data.chat
    if (!chatCapability || !chatCapability.modelConfigId) {
      currentModel.value = null
      return
    }

    const modelConfigId = chatCapability.modelConfigId
    const providerRes = await providerApi.getEnabledProviders()

    if (providerRes.code !== 200 || !providerRes.data) {
      return
    }

    for (const provider of providerRes.data) {
      try {
        const detailRes = await providerApi.getProviderDetail(provider.id)
        if (detailRes.code === 200 && detailRes.data && detailRes.data.models) {
          const model = detailRes.data.models.find(m => m.id === modelConfigId)
          if (model) {
            currentModel.value = {
              id: model.id,
              modelName: model.modelName,
              modelKey: model.modelKey,
              type: provider.providerKey,
              providerName: provider.providerName,
              contextLength: model.contextLength || 4096
            }
            break
          }
        }
      } catch (e) {
        console.warn(`获取供应商 ${provider.providerName} 详情失败:`, e)
      }
    }
  } catch (error) {
    console.error('加载系统聊天模型失败:', error)
    currentModel.value = null
  } finally {
    modelLoading.value = false
  }
}

const goToSystemModel = () => {
  router.push('/settings?tab=system-model')
}

// 辅助函数
const getTypeLabel = (type) => {
  const labels = {
    'GENERAL': '💬 通用聊天',
    'CUSTOM': '💬 自定义',
    'SUMMARY': '📝 内容总结',
    'AGENT_ANSWER': '🤖 Agent回答',
    'FUNCTION_CALLING': '🔧 函数调用',
    'AGENT_DECISION': '🧠 Agent决策',
    'RAG': '📚 RAG问答',
    'CODE': '💻 代码生成',
    'CREATIVE': '🎨 创意写作',
    'DATA': '📊 数据分析',
    'STREAMING': '⚡ 流式对话'
  }
  return labels[type] || type || '未知类型'
}

const getTypeTagType = (type) => {
  const typeMap = {
    'GENERAL': 'primary',
    'CUSTOM': 'info',
    'SUMMARY': 'success',
    'AGENT_ANSWER': 'primary',
    'FUNCTION_CALLING': 'warning',
    'AGENT_DECISION': 'danger',
    'RAG': 'success',
    'CODE': 'warning',
    'CREATIVE': 'danger',
    'DATA': 'info',
    'STREAMING': 'primary'
  }
  return typeMap[type] || 'info'
}

const getStatusType = (status) => {
  const types = { 'DRAFT': 'info', 'ACTIVE': 'success', 'ARCHIVED': 'warning' }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = { 'DRAFT': '草稿', 'ACTIVE': '启用', 'ARCHIVED': '归档' }
  return labels[status] || status
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
    type: 'GENERAL',
    language: 'zh-CN',
    outputFormat: 'TEXT'
  }
}

const resetTemplateForm = () => {
  templateForm.value = {
    name: '',
    type: 'GENERAL',
    template: '',
    description: '',
    modelParams: {
      temperature: 0.7,
      maxTokens: 2048,
      topP: 0.9,
      repeatPenalty: 1.1
    },
    status: 'DRAFT'
  }
  editingTemplate.value = null
  templateFormRef.value?.clearValidate()
}

// API 调用
const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await promptApi.listTemplates()
    if (res && res.code === 200 && res.data) {
      templates.value = Array.isArray(res.data) ? res.data : []
      await loadVectorStats()
    }
  } catch (error) {
    console.error('加载模板失败:', error)
    ElMessage.error('加载模板列表失败')
  } finally {
    loading.value = false
  }
}

const generatePrompt = async () => {
  if (!generateForm.value.requirement) {
    ElMessage.warning('请输入需求描述')
    return
  }

  if (!isModelConfigured.value) {
    ElMessage.warning('系统模型未配置，请先在「系统模型」中配置大语言模型')
    return
  }

  generating.value = true
  try {
    const requestData = {
      requirement: generateForm.value.requirement,
      type: generateForm.value.type,
      language: generateForm.value.language,
      outputFormat: generateForm.value.outputFormat
    }

    if (currentModel.value && currentModel.value.id) {
      requestData.modelConfigId = currentModel.value.id
    }

    const res = await promptApi.generate(requestData)

    if (res && res.code === 200 && res.data) {
      showGenerateDialog.value = false

      generatedResult.value = {
        name: res.data.name || generateForm.value.requirement.substring(0, 30),
        version: res.data.version || 'v1.0.0',
        prompt: res.data.prompt || '',
        systemPrompt: res.data.systemPrompt || '',
        modelParams: res.data.modelParams || {},
        createdAt: res.data.createdAt ? new Date(res.data.createdAt) : new Date(),
        confidenceScore: res.data.confidenceScore || 80,
        suggestions: res.data.suggestions || []
      }

      dialogKey.value++
      showResultDialog.value = true
      ElMessage.success('生成成功')
    } else {
      ElMessage.error(res?.msg || '生成失败')
    }
  } catch (error) {
    console.error('生成失败:', error)
    if (error.response) {
      ElMessage.error(`生成失败: ${error.response.data?.msg || error.response.statusText}`)
    } else {
      ElMessage.error('生成失败：' + (error.message || '未知错误'))
    }
  } finally {
    generating.value = false
  }
}

const saveAsDraft = async () => {
  const saveData = {
    name: generatedResult.value.name || '新模板',
    type: generateForm.value.type,
    template: generatedResult.value.prompt || '',
    description: `通过AI生成的${getTypeLabel(generateForm.value.type)}提示词模板`,
    modelParams: {
      temperature: generatedResult.value.modelParams?.temperature || 0.7,
      maxTokens: generatedResult.value.modelParams?.maxTokens || 2048,
      topP: generatedResult.value.modelParams?.topP || 0.9,
      repeatPenalty: generatedResult.value.modelParams?.repeatPenalty || 1.1
    },
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

const saveTemplate = async () => {
  try {
    await templateFormRef.value?.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    let res
    // 构建符合后端期望的数据结构
    const saveData = {
      name: templateForm.value.name,
      type: templateForm.value.type,
      template: templateForm.value.template,
      description: templateForm.value.description,
      modelParams: templateForm.value.modelParams,  // 直接传递 modelParams 对象
      status: templateForm.value.status,
      streaming: false
    }

    if (editingTemplate.value) {
      res = await promptApi.updateTemplate(editingTemplate.value.id, saveData)
    } else {
      res = await promptApi.saveTemplate(saveData)
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
        modelParams: data.modelParams || {
          temperature: 0.7,
          maxTokens: 2048,
          topP: 0.9,
          repeatPenalty: 1.1
        },
        status: data.status || template.status || 'DRAFT'
      }
    } else {
      templateForm.value = {
        name: template.name,
        type: template.type,
        template: template.template,
        description: template.description || '',
        modelParams: template.modelParams || {
          temperature: 0.7,
          maxTokens: 2048,
          topP: 0.9,
          repeatPenalty: 1.1
        },
        status: template.status || 'DRAFT'
      }
    }
    showCreateDialog.value = true
  } catch (error) {
    console.error('获取模板详情失败:', error)
    ElMessage.error('获取模板详情失败')
  }
}

const viewTemplate = async (template) => {
  viewData.value = {
    name: template.name,
    type: template.type,
    version: template.version,
    template: template.template,
    status: template.status,
    description: template.description || '',
    createdAt: template.createdAt,
    inVector: template.inVector || false
  }
  showViewDialog.value = true
}

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

const deactivateTemplate = async (template) => {
  try {
    await ElMessageBox.confirm(`确定禁用模板「${template.name}」吗？禁用后将无法用于动态路由`, '提示', { type: 'warning' })
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

// 生命周期
onMounted(async () => {
  await loadTemplates()
  await loadSystemChatModel()
})
</script>

<style scoped>
.prompt-template {
  padding: 24px;
  min-height: 100%;
  width: 100%;
  background: #0a0e27;
  box-sizing: border-box;
}

.template-header {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  align-items: center;
  flex-wrap: wrap;
}

.header-buttons {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}

.header-search {
  margin-left: auto;
  width: 300px;
  flex-shrink: 0;
}

/* 向量库状态卡片 */
.vector-stats-card {
  display: flex;
  gap: 24px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #1a1f3a, #0f1228);
  border: 1px solid #2a2f4a;
  border-radius: 16px;
  margin-bottom: 20px;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #818cf8;
  line-height: 1.2;
}

.stat-value.stat-warning {
  color: #f59e0b;
}

.stat-label {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 4px;
}

/* 路由测试区域 */
.router-test-section {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 16px;
  margin-bottom: 20px;
  overflow: hidden;
}

.test-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #22284a;
  cursor: pointer;
  font-weight: 600;
  color: #ffffff;
}

.test-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.test-content {
  padding: 16px;
}

.test-input-area {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.test-input-area .el-input {
  flex: 1;
}

.test-result-area {
  background: #0f1228;
  border-radius: 12px;
  padding: 16px;
  margin-top: 12px;
}

.test-result-area .result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #2a2f4a;
}

.template-preview {
  margin-top: 12px;
}

.template-preview pre {
  background: #1a1f3a;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
  color: #cbd5e6;
  white-space: pre-wrap;
  word-break: break-word;
  margin-top: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.model-warning-alert {
  margin-bottom: 20px;
  border-radius: 12px;
}

.model-info-display {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.model-info-display .el-tag {
  font-size: 14px;
  padding: 8px 16px;
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

.prompt-content {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  padding: 16px;
  max-height: 400px;
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

.template-table {
  margin-top: 20px;
  border-radius: 16px;
  overflow: hidden;
  width: 100%;
}

.template-table :deep(.el-table) {
  background: transparent !important;
  width: 100%;
}

.template-table :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.template-table :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
}

.template-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.06) !important;
}

:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
}

:deep(.el-dialog__title) {
  color: #ffffff !important;
}

:deep(.el-form-item__label) {
  color: #cbd5e6 !important;
}

:deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
}

:deep(.el-input__inner) {
  color: #ffffff !important;
}

:deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  color: #ffffff !important;
}

:deep(.el-select-dropdown) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
}

:deep(.el-select-dropdown__item) {
  color: #cbd5e6 !important;
}

:deep(.el-select-dropdown__item:hover) {
  background: #2a2f4a !important;
}

:deep(.el-radio__label) {
  color: #cbd5e6 !important;
}

:deep(.el-checkbox__label) {
  color: #cbd5e6 !important;
}

/* 操作按钮容器 */
.op-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

:deep(.op-buttons .el-button) {
  border-radius: 6px !important;
  border: none !important;
  color: #fff !important;
  font-size: 12px !important;
  padding: 6px 12px !important;
  min-width: 50px !important;
  box-shadow: none !important;
}

:deep(.op-buttons .btn-purple) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
}

:deep(.op-buttons .btn-green) {
  background: linear-gradient(135deg, #10b981, #059669) !important;
}

:deep(.op-buttons .btn-orange) {
  background: linear-gradient(135deg, #f59e0b, #d97706) !important;
}

:deep(.op-buttons .btn-red) {
  background: linear-gradient(135deg, #ef4444, #dc2626) !important;
}

:deep(.op-buttons .el-button:hover) {
  opacity: 0.9 !important;
  transform: translateY(-1px) !important;
  transition: all 0.2s ease !important;
}

@media (max-width: 768px) {
  .prompt-template {
    padding: 16px;
  }

  .template-header {
    flex-wrap: wrap;
    gap: 12px;
  }

  .template-header .el-button {
    flex: 1;
  }

  .template-header .el-input {
    width: 100% !important;
    margin-left: 0 !important;
    margin-top: 8px;
  }

  .vector-stats-card {
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-item {
    min-width: 80px;
  }

  .op-buttons {
    gap: 4px;
  }

  :deep(.op-buttons .el-button) {
    padding: 4px 8px;
    font-size: 11px;
    min-width: 40px;
  }
}
</style>