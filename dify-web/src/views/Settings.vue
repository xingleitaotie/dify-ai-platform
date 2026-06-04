<template>
  <div class="settings">
    <h2>设置</h2>

    <el-tabs v-model="activeTab" class="settings-tabs">
      <el-tab-pane label="模型配置" name="model">
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>模型参数配置</span>
              <div class="header-buttons">
                <el-button type="success" size="small" @click="loadModelConfigs" :loading="configsLoading">
                  <el-icon>
                    <Refresh/>
                  </el-icon>
                  刷新列表
                </el-button>
                <el-button type="primary" size="small" @click="showAddDialog">
                  <el-icon>
                    <Plus/>
                  </el-icon>
                  新建配置
                </el-button>
              </div>
            </div>
          </template>

          <!-- 配置列表 -->

          <el-table
              :data="configList"
              stripe
              v-loading="configsLoading"
              style="width: 100%"
              :header-cell-style="{ background: '#0f1228', color: '#ffffff', fontWeight: '600' }"
          >
            <!-- 保持原有列定义，但给操作列设置固定宽度 -->
            <el-table-column prop="configName" label="配置名称" width="150" show-overflow-tooltip/>

            <el-table-column prop="type" label="模型类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getTypeTagType(row.type)" size="small">{{ getTypeLabel(row.type) }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="modelName" label="模型名称" width="200" show-overflow-tooltip/>

            <el-table-column prop="baseUrl" label="API地址" min-width="250" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="api-url">{{ row.baseUrl }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="maxTokens" label="Max Tokens" width="100" align="center">
              <template #default="{ row }">
                <span class="param-value">{{ row.maxTokens }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="temperature" label="Temperature" width="100" align="center">
              <template #default="{ row }">
                <span class="param-value">{{ row.temperature }}</span>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                  {{ row.status === 1 ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="默认" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault === 1" type="warning" size="small">默认</el-tag>
              </template>
            </el-table-column>

            <!-- 操作列固定宽度，不再使用 fixed="right" -->
            <el-table-column label="操作" width="240" align="center">
              <template #default="{ row }">
                <div class="action-buttons">
                  <el-button
                      type="primary"
                      link
                      size="small"
                      @click="showEditDialog(row)"
                  >
                    <el-icon>
                      <Edit/>
                    </el-icon>
                    <span>编辑</span>
                  </el-button>
                  <el-button
                      type="success"
                      link
                      size="small"
                      @click="switchToConfig(row)"
                      :disabled="row.isDefault === 1"
                  >
                    <el-icon>
                      <Switch/>
                    </el-icon>
                    <span>切换</span>
                  </el-button>
                  <el-button
                      type="danger"
                      link
                      size="small"
                      @click="deleteConfig(row)"
                      :disabled="row.isDefault === 1"
                  >
                    <el-icon>
                      <Delete/>
                    </el-icon>
                    <span>删除</span>
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>


          <!-- 添加空状态提示 -->
          <div v-if="!configsLoading && configList.length === 0" class="empty-state">
            <el-empty description="暂无配置数据，点击上方「新建配置」添加"/>
          </div>

          <!-- 分页 - 只在有数据时显示 -->
          <el-pagination
              v-if="total > 0"
              v-model:current-page="pageParams.pageNum"
              v-model:page-size="pageParams.pageSize"
              :page-sizes="[5, 10, 20, 50]"
              :total="total"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadModelConfigs"
              @current-change="loadModelConfigs"
          />
        </el-card>

        <!-- 当前使用的配置卡片 -->
        <el-card class="settings-card" style="margin-top: 20px">
          <template #header>
            <span>当前使用配置</span>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="配置名称">{{ currentConfig.configName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="模型类型">{{ getTypeLabel(currentConfig.type) }}</el-descriptions-item>
            <el-descriptions-item label="模型名称">{{ currentConfig.modelName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="API地址">{{ currentConfig.baseUrl || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Max Tokens">{{ currentConfig.maxTokens || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Temperature">{{ currentConfig.temperature || '-' }}</el-descriptions-item>
            <el-descriptions-item label="超时时间">{{ currentConfig.timeout || '-' }}秒</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentConfig.status === 1 ? 'success' : 'danger'">
                {{ currentConfig.status === 1 ? '已启用' : '已禁用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- 其他 Tab 保持不变 -->
      <el-tab-pane label="Prompt模板" name="prompt">
        <PromptTemplate/>
      </el-tab-pane>

      <el-tab-pane label="工具管理" name="tools">
        <el-card class="settings-card">
          <template #header>
            <span>已注册函数工具</span>
          </template>
          <el-table :data="tools" stripe v-loading="toolsLoading">
            <el-table-column prop="name" label="工具名称" width="180"/>
            <el-table-column prop="desc" label="描述" show-overflow-tooltip/>
            <el-table-column prop="params" label="参数" width="200">
              <template #default="{ row }">
                <el-tag v-for="param in row.params" :key="param" size="small" style="margin: 2px">
                  {{ param }}
                </el-tag>
                <span v-if="!row.params?.length">无参数</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="toggleTool(row)"/>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="系统设置" name="system">
        <el-card class="settings-card">
          <template #header>
            <span>系统偏好设置</span>
          </template>
          <el-form :model="systemSettings" label-width="120px">
            <el-form-item label="主题">
              <el-radio-group v-model="systemSettings.theme">
                <el-radio label="light">浅色</el-radio>
                <el-radio label="dark">深色</el-radio>
                <el-radio label="auto">跟随系统</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="语言">
              <el-select v-model="systemSettings.language">
                <el-option label="中文" value="zh-CN"/>
                <el-option label="English" value="en-US"/>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSystemSettings">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="关于" name="about">
        <el-card class="settings-card">
          <div class="about-content">
            <div class="logo">
              <el-icon :size="48">
                <DataAnalysis/>
              </el-icon>
              <h2>Dify AI Platform</h2>
            </div>
            <p>版本: 1.0.0</p>
            <p>智能AI应用开发平台</p>
            <p>支持大模型对话、知识库RAG、Agent编排、工作流、Function Calling等功能</p>
            <el-divider/>
            <h3>技术栈</h3>
            <div class="tech-stack">
              <el-tag>Vue 3</el-tag>
              <el-tag>Vite</el-tag>
              <el-tag>Element Plus</el-tag>
              <el-tag>Spring Boot</el-tag>
              <el-tag>ModelScope</el-tag>
            </div>
            <el-divider/>
            <p>© 2024 Dify AI Platform. All rights reserved.</p>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑配置对话框 -->
    <el-dialog
        v-model="dialogVisible"
        :title="dialogTitle"
        width="600px"
        @close="resetForm"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="120px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="formData.configName" placeholder="请输入配置名称"/>
        </el-form-item>

        <el-form-item label="模型类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择模型类型" @change="onTypeChange">
            <el-option
                v-for="item in modelTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="formData.modelName" placeholder="请输入模型名称"/>
          <div class="form-tip">{{ getModelNameTip(formData.type) }}</div>
        </el-form-item>

        <el-form-item label="API地址" prop="baseUrl">
          <el-input v-model="formData.baseUrl" :placeholder="getBaseUrlPlaceholder(formData.type)"/>
          <div class="form-tip">{{ getBaseUrlTip(formData.type) }}</div>
        </el-form-item>

        <el-form-item label="API Key" prop="apiKey">
          <el-input
              v-model="formData.apiKey"
              type="password"
              show-password
              placeholder="请输入API Key"
          />
        </el-form-item>

        <el-divider content-position="left">模型参数</el-divider>

        <el-form-item label="Temperature" prop="temperature">
          <div class="slider-container">
            <el-slider v-model="formData.temperature" :min="0" :max="2" :step="0.1" style="width: 300px"/>
            <span class="slider-value">{{ formData.temperature }}</span>
          </div>
        </el-form-item>

        <el-form-item label="Max Tokens" prop="maxTokens">
          <el-input-number v-model="formData.maxTokens" :min="1" :max="8192" :step="100"/>
        </el-form-item>

        <el-form-item label="超时时间(秒)" prop="timeout">
          <el-input-number v-model="formData.timeout" :min="30" :max="300" :step="10"/>
        </el-form-item>

        <el-form-item label="设为默认" prop="isDefault">
          <el-switch v-model="formData.isDefault" :active-value="1" :inactive-value="0"/>
          <span class="form-tip" style="margin-left: 8px">设为默认后，新对话将使用此配置</span>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-switch v-model="formData.status" :active-value="1" :inactive-value="0"/>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="testConfigBeforeSave" :loading="testingConfig">
          测试连接
        </el-button>
        <el-button type="success" @click="saveConfig" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, reactive, onMounted} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {DataAnalysis, Refresh, Plus} from '@element-plus/icons-vue'
import {functionApi} from '@/api/function'
import {modelConfigApi} from '@/api/chat'
import PromptTemplate from './components/PromptTemplate.vue'

const activeTab = ref('model')
const saving = ref(false)
const testing = ref(false)
const testingConfig = ref(false)
const configsLoading = ref(false)
const toolsLoading = ref(false)
const tools = ref([])
const dialogVisible = ref(false)
const dialogTitle = ref('新增配置')
const isEdit = ref(false)
const formRef = ref(null)

// 分页参数
const pageParams = reactive({
  pageNum: 1,
  pageSize: 10
})
const total = ref(0)
const configList = ref([])

// 当前使用的配置
const currentConfig = reactive({
  configName: '',
  type: '',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  maxTokens: 2048,
  temperature: 0.7,
  timeout: 60,
  status: 1,
  isDefault: 1
})

// 表单数据
const formData = reactive({
  id: null,
  configName: '',
  type: 'modelScope',
  modelName: '',
  baseUrl: '',
  apiKey: '',
  secret: '',
  maxTokens: 2048,
  temperature: 0.7,
  timeout: 60,
  isDefault: 0,
  status: 1
})

// 模型类型列表
const modelTypes = ref([
  {value: 'ollama', label: 'Ollama'},
  {value: 'openai', label: 'OpenAI'},
  {value: 'modelScope', label: 'ModelScope'},
  {value: 'qwen', label: '通义千问'},
  {value: 'ernie', label: '文心一言'},
  {value: 'spark', label: '讯飞星火'},
  {value: 'zhipu', label: '智谱AI'}
])

// 表单验证规则
const formRules = {
  configName: [{required: true, message: '请输入配置名称', trigger: 'blur'}],
  type: [{required: true, message: '请选择模型类型', trigger: 'change'}],
  modelName: [{required: true, message: '请输入模型名称', trigger: 'blur'}],
  baseUrl: [{required: true, message: '请输入API地址', trigger: 'blur'}]
}

// 系统设置
const systemSettings = reactive({
  theme: 'light',
  language: 'zh-CN'
})

// 获取模型类型标签
const getTypeLabel = (type) => {
  const item = modelTypes.value.find(t => t.value === type)
  return item ? item.label : type
}

const getTypeTagType = (type) => {
  const types = {
    ollama: 'info',
    openai: 'primary',
    modelScope: 'success',
    qwen: 'warning',
    ernie: 'danger',
    spark: 'info',
    zhipu: 'primary'
  }
  return types[type] || 'info'
}

const getModelNameTip = (type) => {
  const tips = {
    modelScope: '格式：LLM-Research/模型名称，如：LLM-Research/glm-4-9b-chat-GGUF',
    openai: '格式：gpt-3.5-turbo、gpt-4、gpt-4-turbo-preview',
    ollama: '格式：qwen2.5:7b、llama3:8b',
    qwen: '格式：qwen-turbo、qwen-plus、qwen-max',
    ernie: '格式：ernie-3.5、ernie-4.0',
    spark: '格式：generalv3、generalv3.5',
    zhipu: '格式：glm-4、glm-4-plus、glm-4-air'
  }
  return tips[type] || '请输入正确的模型名称'
}

const getBaseUrlPlaceholder = (type) => {
  const urls = {
    modelScope: 'https://api.modelscope.cn/v1',
    openai: 'https://api.openai.com/v1',
    ollama: 'http://localhost:11434',
    qwen: 'https://dashscope.aliyuncs.com/api/v1',
    ernie: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat',
    spark: 'https://spark-api.xf-yun.com/v3.5/chat',
    zhipu: 'https://open.bigmodel.cn/api/paas/v4'
  }
  return urls[type] || '请输入API地址'
}

const getBaseUrlTip = (type) => {
  const tips = {
    modelScope: 'ModelScope API地址',
    openai: 'OpenAI API地址',
    ollama: '本地Ollama服务地址',
    qwen: '通义千问API地址',
    ernie: '文心一言API地址',
    spark: '讯飞星火API地址',
    zhipu: '智谱AI API地址'
  }
  return tips[type] || 'API服务地址'
}

// 加载配置列表
const loadModelConfigs = async () => {
  configsLoading.value = true
  try {
    const res = await modelConfigApi.getConfigPage(
        pageParams.pageNum,
        pageParams.pageSize,
        null,
        1
    )
    console.log('API响应数据:', res) // 添加日志查看实际返回结构

    if (res.code === 200 && res.data) {
      // 兼容多种返回格式
      let records = []
      let totalCount = 0

      if (Array.isArray(res.data)) {
        // 如果直接返回数组
        records = res.data
        totalCount = res.data.length
      } else if (res.data.records) {
        // 标准分页格式
        records = res.data.records
        totalCount = res.data.total || 0
      } else if (res.data.list) {
        // 其他格式
        records = res.data.list
        totalCount = res.data.total || res.data.totalCount || 0
      } else if (res.data.content) {
        // Spring Boot 格式
        records = res.data.content
        totalCount = res.data.totalElements || 0
      }

      configList.value = records
      total.value = totalCount

      // 如果还是没有数据，检查后端接口
      if (totalCount === 0) {
        console.warn('未获取到配置数据，请检查后端接口返回格式')
      }
    } else {
      console.warn('接口返回异常:', res)
      configList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('加载配置列表失败:', error)
    ElMessage.error('加载配置列表失败：' + (error.message || '未知错误'))
    configList.value = []
    total.value = 0
  } finally {
    configsLoading.value = false
  }
}

// 加载当前配置
const loadCurrentConfig = async () => {
  try {
    const res = await modelConfigApi.getCurrentConfigDetail()
    if (res.code === 200 && res.data) {
      Object.assign(currentConfig, res.data)
    }
  } catch (error) {
    console.error('加载当前配置失败:', error)
  }
}

// 测试当前连接
const testConnection = async () => {
  testing.value = true
  try {
    const res = await modelConfigApi.testConnection()
    if (res.code === 200) {
      ElMessage.success(res.data || '连接正常')
    } else {
      ElMessage.error(res.msg || '连接失败')
    }
  } catch (error) {
    ElMessage.error('连接失败：' + (error.message || '未知错误'))
  } finally {
    testing.value = false
  }
}

// 测试指定配置连接
const testConfigBeforeSave = async () => {
  testingConfig.value = true
  try {
    const testData = {...formData}
    const res = await modelConfigApi.testConfig(testData)
    if (res.code === 200 && res.data === true) {
      ElMessage.success('连接测试成功')
    } else {
      ElMessage.error(res.msg || '连接测试失败')
    }
  } catch (error) {
    ElMessage.error('连接测试失败：' + (error.message || '未知错误'))
  } finally {
    testingConfig.value = false
  }
}

// 保存配置
const saveConfig = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  saving.value = true
  try {
    let res
    // 确保 isDefault 和 status 是数字类型
    const submitData = {
      id: formData.id,
      configName: formData.configName,
      type: formData.type,
      modelName: formData.modelName,
      baseUrl: formData.baseUrl,
      apiKey: formData.apiKey,
      secret: formData.secret || '',
      maxTokens: Number(formData.maxTokens),
      temperature: Number(formData.temperature),
      timeout: Number(formData.timeout),
      isDefault: Number(formData.isDefault),  // 强制转换为数字
      status: Number(formData.status)         // 强制转换为数字
    }

    console.log('提交数据:', submitData)

    if (isEdit.value) {
      res = await modelConfigApi.updateConfig(submitData)
    } else {
      res = await modelConfigApi.addConfig(submitData)
    }

    if (res.code === 200 && res.data === true) {
      ElMessage.success(isEdit.value ? '配置更新成功' : '配置新增成功')
      dialogVisible.value = false
      await loadModelConfigs()
      await loadCurrentConfig()
      resetForm()
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败：' + (error.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 显示新增对话框
const showAddDialog = () => {
  isEdit.value = false
  dialogTitle.value = '新增配置'
  resetForm()
  dialogVisible.value = true
}

// 显示编辑对话框
const showEditDialog = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑配置'

  formData.id = row.id
  formData.configName = row.configName
  formData.type = row.type
  formData.modelName = row.modelName
  formData.baseUrl = row.baseUrl
  formData.apiKey = row.apiKey || ''
  formData.secret = row.secret || ''
  formData.maxTokens = row.maxTokens
  formData.temperature = row.temperature
  formData.timeout = row.timeout
  // 确保转换为数字类型
  formData.isDefault = row.isDefault === 1 ? 1 : 0
  formData.status = row.status === 1 ? 1 : 0

  if (formData.apiKey && formData.apiKey.startsWith('***')) {
    formData.apiKey = ''
  }

  dialogVisible.value = true
}

// 切换配置
const switchToConfig = async (row) => {
  try {
    await ElMessageBox.confirm(`确定切换到配置"${row.configName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })

    const res = await modelConfigApi.switchConfig(row.id)
    if (res.code === 200 && res.data === true) {
      ElMessage.success(`已切换到配置：${row.configName}`)
      await loadCurrentConfig()
      await loadModelConfigs()
    } else {
      ElMessage.error(res.msg || '切换失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('切换失败:', error)
      ElMessage.error('切换失败')
    }
  }
}

// 删除配置
const deleteConfig = async (row) => {
  if (row.isDefault === 1) {
    ElMessage.warning('默认配置不能删除')
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除配置"${row.configName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const res = await modelConfigApi.deleteConfig(row.id)
    if (res.code === 200 && res.data === true) {
      ElMessage.success('删除成功')
      await loadModelConfigs()
      await loadCurrentConfig()
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

// 模型类型改变时的处理
const onTypeChange = (type) => {
  // 根据类型设置默认API地址
  const defaultUrls = {
    modelScope: 'https://api.modelscope.cn/v1',
    openai: 'https://api.openai.com/v1',
    ollama: 'http://localhost:11434',
    qwen: 'https://dashscope.aliyuncs.com/api/v1',
    ernie: 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat',
    spark: 'https://spark-api.xf-yun.com/v3.5/chat',
    zhipu: 'https://open.bigmodel.cn/api/paas/v4'
  }
  if (defaultUrls[type] && !formData.baseUrl) {
    formData.baseUrl = defaultUrls[type]
  }
}

// 重置表单
const resetForm = () => {
  formData.id = null
  formData.configName = ''
  formData.type = 'modelScope'
  formData.modelName = ''
  formData.baseUrl = 'https://api.modelscope.cn/v1'
  formData.apiKey = ''
  formData.secret = ''
  formData.maxTokens = 2048
  formData.temperature = 0.7
  formData.timeout = 60
  formData.isDefault = 0
  formData.status = 1
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

// 加载工具列表
const loadTools = async () => {
  toolsLoading.value = true
  try {
    const res = await functionApi.getFunctionList()
    if (res.code === 200) {
      tools.value = (res.data || []).map(tool => ({
        ...tool,
        enabled: true
      }))
    }
  } catch (error) {
    console.error('加载工具失败', error)
  } finally {
    toolsLoading.value = false
  }
}

// 切换工具状态
const toggleTool = (tool) => {
  ElMessage.success(`${tool.name} ${tool.enabled ? '已启用' : '已禁用'}`)
}

// 保存系统设置
const saveSystemSettings = () => {
  localStorage.setItem('systemSettings', JSON.stringify(systemSettings))
  ElMessage.success('系统设置保存成功')
}

onMounted(() => {
  loadModelConfigs()
  loadCurrentConfig()
  loadTools()

  // 加载系统设置
  const saved = localStorage.getItem('systemSettings')
  if (saved) {
    try {
      Object.assign(systemSettings, JSON.parse(saved))
    } catch (e) {
    }
  }
})
</script>

<style scoped>
.settings {
  padding: 24px;
  min-height: 100%;
  background: #0a0e27;
}

.settings h2 {
  margin-bottom: 24px;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #a5b4fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: -0.3px;
}

/* ========== Tabs 样式 ========== */
.settings-tabs {
  background: transparent;
}

.settings-tabs :deep(.el-tabs__header) {
  background: transparent;
  border-bottom: 1px solid #2a2f4a;
  margin-bottom: 24px;
}

.settings-tabs :deep(.el-tabs__nav-wrap::after) {
  background-color: #2a2f4a;
}

.settings-tabs :deep(.el-tabs__item) {
  color: #94a3b8;
  font-weight: 500;
  font-size: 15px;
  transition: all 0.3s ease;
}

.settings-tabs :deep(.el-tabs__item:hover) {
  color: #a78bfa;
}

.settings-tabs :deep(.el-tabs__item.is-active) {
  color: #667eea;
}

.settings-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
  height: 3px;
  border-radius: 3px;
}

/* ========== 卡片样式 ========== */
.settings-card {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 16px !important;
  margin-bottom: 24px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.settings-card:hover {
  border-color: rgba(102, 126, 234, 0.5);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}


.settings-card :deep(.el-card__header .card-header) {
  color: #ffffff;
  font-weight: 600;
  font-size: 16px;
}

.settings-card :deep(.el-card__body) {
  padding: 20px;
  background: #1a1f3a;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.card-header > span {
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.header-buttons {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

/* ========== 按钮样式 ========== */
.header-buttons .el-button--primary,
.header-buttons .el-button--success {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  color: #ffffff !important;
  font-weight: 500;
  transition: all 0.3s ease;
}

.header-buttons .el-button--primary:hover,
.header-buttons .el-button--success:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.header-buttons .el-button--default {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.header-buttons .el-button--default:hover {
  background: #3a3f5a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
}

/* ========== 表格样式 ========== */
.settings-card :deep(.el-table) {
  background: transparent !important;
  --el-table-bg-color: transparent !important;
  --el-table-tr-bg-color: transparent !important;
}

.settings-card :deep(.el-table__header) {
  background: transparent !important;
}

.settings-card :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.settings-card :deep(.el-table__header th .cell) {
  color: #ffffff !important;
}

.settings-card :deep(.el-table__body tr) {
  background: transparent !important;
}

.settings-card :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
}

.settings-card :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.08) !important;
}

.settings-card :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: rgba(255, 255, 255, 0.02) !important;
}

/* 表格内按钮 */
.settings-card :deep(.el-button--primary.is-link) {
  color: #818cf8 !important;
}

.settings-card :deep(.el-button--primary.is-link:hover) {
  color: #a78bfa !important;
}

.settings-card :deep(.el-button--success.is-link) {
  color: #34d399 !important;
}

.settings-card :deep(.el-button--success.is-link:hover) {
  color: #6ee7b7 !important;
}

.settings-card :deep(.el-button--danger.is-link) {
  color: #f87171 !important;
}

.settings-card :deep(.el-button--danger.is-link:hover) {
  color: #ef4444 !important;
}

/* ========== 标签样式 ========== */
.settings-card :deep(.el-tag--success) {
  background: rgba(16, 185, 129, 0.15) !important;
  border-color: rgba(16, 185, 129, 0.3) !important;
  color: #34d399 !important;
}

.settings-card :deep(.el-tag--danger) {
  background: rgba(239, 68, 68, 0.15) !important;
  border-color: rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}

.settings-card :deep(.el-tag--warning) {
  background: rgba(245, 158, 11, 0.15) !important;
  border-color: rgba(245, 158, 11, 0.3) !important;
  color: #fbbf24 !important;
}

.settings-card :deep(.el-tag--info) {
  background: rgba(100, 116, 139, 0.15) !important;
  border-color: rgba(100, 116, 139, 0.3) !important;
  color: #94a3b8 !important;
}

.settings-card :deep(.el-tag--primary) {
  background: rgba(102, 126, 234, 0.15) !important;
  border-color: rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

/* ========== 分页样式 ========== */
.settings-card :deep(.el-pagination) {
  margin-top: 20px;
  justify-content: flex-end;
}

.settings-card :deep(.el-pagination .el-pagination__total),
.settings-card :deep(.el-pagination .el-pagination__sizes .el-select .el-input__inner),
.settings-card :deep(.el-pagination .btn-prev),
.settings-card :deep(.el-pagination .btn-next),
.settings-card :deep(.el-pagination .el-pager li) {
  color: #cbd5e6 !important;
  background: transparent !important;
}

.settings-card :deep(.el-pagination .btn-prev:hover),
.settings-card :deep(.el-pagination .btn-next:hover),
.settings-card :deep(.el-pagination .el-pager li:hover) {
  color: #a78bfa !important;
}

.settings-card :deep(.el-pagination .el-pager li.is-active) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  color: #ffffff !important;
  border-radius: 8px;
}

/* ========== 描述列表样式 ========== */
.settings-card :deep(.el-descriptions) {
  --el-descriptions-table-bg: transparent !important;
}

.settings-card :deep(.el-descriptions__header) {
  margin-bottom: 16px;
}

.settings-card :deep(.el-descriptions__title) {
  color: #ffffff !important;
  font-weight: 600;
}

.settings-card :deep(.el-descriptions__label) {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  border-color: #2a2f4a !important;
  font-weight: 500;
}

.settings-card :deep(.el-descriptions__content) {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
  border-color: #2a2f4a !important;
}

/* ========== 表单样式 ========== */
.settings-card :deep(.el-form-item__label) {
  color: #cbd5e6 !important;
  font-weight: 500;
}

.settings-card :deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  box-shadow: none !important;
  border-radius: 10px;
}

.settings-card :deep(.el-input__wrapper:hover) {
  border-color: #667eea !important;
}

.settings-card :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

.settings-card :deep(.el-input__inner) {
  color: #ffffff !important;
}

.settings-card :deep(.el-input__inner::placeholder) {
  color: #64748b !important;
}

.settings-card :deep(.el-select .el-input__wrapper) {
  background: #0f1228 !important;
}

.settings-card :deep(.el-select-dropdown) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px;
}

.settings-card :deep(.el-select-dropdown__item) {
  color: #cbd5e6 !important;
}

.settings-card :deep(.el-select-dropdown__item:hover) {
  background: #2a2f4a !important;
  color: #ffffff !important;
}

.settings-card :deep(.el-select-dropdown__item.selected) {
  background: rgba(102, 126, 234, 0.15) !important;
  color: #a78bfa !important;
}

/* 滑块样式 */
.slider-container {
  display: flex;
  align-items: center;
  gap: 16px;
}

.slider-container :deep(.el-slider__runway) {
  background-color: #2a2f4a;
}

.slider-container :deep(.el-slider__bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.slider-container :deep(.el-slider__button) {
  border-color: #667eea;
}

.slider-value {
  min-width: 40px;
  color: #a78bfa;
  font-weight: 600;
}

/* 数字输入框 */
.settings-card :deep(.el-input-number .el-input__wrapper) {
  background: #0f1228 !important;
}

.settings-card :deep(.el-input-number__increase),
.settings-card :deep(.el-input-number__decrease) {
  background: #2a2f4a !important;
  color: #cbd5e6 !important;
}

.settings-card :deep(.el-input-number__increase:hover),
.settings-card :deep(.el-input-number__decrease:hover) {
  background: #3a3f5a !important;
  color: #ffffff !important;
}

/* 开关样式 */
.settings-card :deep(.el-switch__core) {
  background: #2a2f4a !important;
  border-color: #3a3f5a !important;
}

.settings-card :deep(.el-switch.is-checked .el-switch__core) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border-color: transparent !important;
}

/* 提示文字 */
.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

/* 分割线 */
.settings-card :deep(.el-divider) {
  background-color: #2a2f4a;
}

.settings-card :deep(.el-divider__text) {
  background-color: #1a1f3a;
  color: #cbd5e6;
  font-weight: 500;
}

/* ========== 关于页面 ========== */
.about-content {
  text-align: center;
  padding: 20px;
}

.logo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.logo .el-icon {
  color: #667eea;
  font-size: 64px;
}

.logo h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #a5b4fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.about-content p {
  color: #cbd5e6;
  margin: 8px 0;
  line-height: 1.6;
}

.about-content p:first-of-type {
  color: #94a3b8;
}

.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin: 20px 0;
}

.tech-stack :deep(.el-tag) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  color: #a78bfa !important;
  font-weight: 500;
  padding: 4px 16px;
}

/* ========== 对话框样式 ========== */
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

:deep(.el-dialog__footer .el-button--success) {
  background: linear-gradient(135deg, #10b981, #059669) !important;
  border: none !important;
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

/* ========== 工具管理表格样式修复 ========== */
.settings-card .el-table {
  background: transparent !important;
}

.settings-card .el-table__header th {
  background: #0f1228 !important;
  color: #ffffff !important;
}

.settings-card .el-table__body td {
  color: #cbd5e6 !important;
}

.settings-card .el-table__body tr:hover > td {
  background: rgba(102, 126, 234, 0.06) !important;
}

.settings-card .el-tag {
  background: rgba(102, 126, 234, 0.15) !important;
  border-color: rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

.settings-card .el-switch__core {
  background: #2a2f4a !important;
}

.settings-card .el-switch.is-checked .el-switch__core {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .action-buttons {
    gap: 4px;
  }

  .action-buttons .el-button {
    padding: 4px 6px;
  }

  .action-buttons .el-button span {
    font-size: 11px;
  }
}

/* 表格整体圆角和边框 */
.settings-card :deep(.el-table) {
  border-radius: 12px;
  overflow: hidden;
}

/* 表头样式 */
.settings-card :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  padding: 14px 0 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

/* 单元格样式 */
.settings-card :deep(.el-table__body td) {
  padding: 12px 0 !important;
  font-size: 13px !important;
}

/* API地址样式 - 等宽字体 */
.api-url {
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  color: #94a3b8;
}

/* 参数数值样式 */
.param-value {
  font-weight: 500;
  color: #a78bfa;
}

/* ========== 操作列按钮样式优化 ========== */
/* 操作按钮样式优化 - 确保不换行 */
.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: center;
  flex-wrap: nowrap; /* 强制不换行 */
  white-space: nowrap;
}

.action-buttons .el-button {
  padding: 4px 10px;
  margin: 0;
  font-weight: 500;
  transition: all 0.2s ease;
  border-radius: 6px;
  white-space: nowrap;
  flex-shrink: 0; /* 防止按钮被压缩 */
}

/* 编辑按钮 - 亮蓝色 */
.action-buttons .el-button--primary {
  color: #60a5fa !important;
  background: rgba(96, 165, 250, 0.1) !important;
}

.action-buttons .el-button--primary:hover {
  color: #93c5fd !important;
  background: rgba(96, 165, 250, 0.2) !important;
  transform: translateY(-1px);
}

/* 切换按钮 - 亮绿色 */
.action-buttons .el-button--success {
  color: #34d399 !important;
  background: rgba(52, 211, 153, 0.1) !important;
}

.action-buttons .el-button--success:hover {
  color: #6ee7b7 !important;
  background: rgba(52, 211, 153, 0.2) !important;
  transform: translateY(-1px);
}

/* 删除按钮 - 亮红色 */
.action-buttons .el-button--danger {
  color: #f87171 !important;
  background: rgba(248, 113, 113, 0.1) !important;
}

.action-buttons .el-button--danger:hover {
  color: #fca5a5 !important;
  background: rgba(248, 113, 113, 0.2) !important;
  transform: translateY(-1px);
}

/* 禁用状态 */
.action-buttons .el-button.is-disabled,
.action-buttons .el-button.is-disabled:hover {
  opacity: 0.4;
  transform: none;
  cursor: not-allowed;
}

/* 按钮内图标和文字间距 */
.action-buttons .el-button .el-icon {
  font-size: 14px;
  margin-right: 4px;
}

.action-buttons .el-button span {
  font-size: 12px;
}

/* 标签大小调整 */
.settings-card :deep(.el-tag--small) {
  padding: 0 8px;
  height: 24px;
  line-height: 22px;
  font-size: 12px;
}

/* 表格悬停效果增强 */
.settings-card :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.06) !important;
}

/* 表格边框统一 */
.settings-card :deep(.el-table td),
.settings-card :deep(.el-table th.is-leaf) {
  border-bottom: 1px solid #2a2f4a;
}

/* 固定列阴影 */
.settings-card :deep(.el-table__fixed-right) {
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.2);
}

/* 响应式：小屏幕时调整列宽 */
@media (max-width: 1200px) {
  .settings-card :deep(.el-table) {
    overflow-x: auto;
  }

  .settings-card :deep(.el-table__header th),
  .settings-card :deep(.el-table__body td) {
    white-space: nowrap;
  }
}

/* 添加的修复样式 */
.el-card__header span,
.el-card__header .card-header span {
  color: #ffffff !important;
  font-weight: 600 !important;
}

/* 修改为更精确的选择器 */
.settings-card .el-card__header {
  background: #0f1228 !important;
  border-bottom: 1px solid #2a2f4a !important;
  padding: 16px 20px !important;
}

.settings-card .el-card__header .card-header {
  color: #ffffff;
  font-weight: 600;
  font-size: 16px;
}

.settings-card .el-card__header span {
  color: #ffffff !important;
}

/* ========== 关于页面样式修复 ========== */

/* 关于页面卡片 */
.about-content {
  text-align: center;
  padding: 20px;
}

/* 标题样式 */
.about-content .logo h2,
.about-content h2,
.about-content h3 {
  color: #ffffff !important;
  font-weight: 600;
}

/* 关于页面所有文字 */
.about-content p {
  color: #cbd5e6 !important;
  margin: 8px 0;
  line-height: 1.6;
}

/* 技术栈标题 */
.about-content h3 {
  color: #ffffff !important;
  margin: 16px 0 12px 0;
}

/* 分割线 */
.about-content .el-divider {
  background-color: #2a2f4a !important;
  margin: 20px 0;
}

/* 技术栈中的标签 */
.tech-stack {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin: 20px 0;
}

.tech-stack .el-tag {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
  font-weight: 500;
  padding: 4px 16px;
  border-radius: 20px;
}

/* 版权信息 */
.about-content p:last-child {
  color: #64748b !important;
  font-size: 12px;
  margin-top: 20px;
}

/* 空状态样式 */
.empty-state {
  padding: 40px 0;
  background: #1a1f3a;
  border-radius: 12px;
  text-align: center;
}

.empty-state :deep(.el-empty__description) {
  color: #94a3b8;
}

.empty-state :deep(.el-empty__description p) {
  color: #94a3b8;
}
</style>