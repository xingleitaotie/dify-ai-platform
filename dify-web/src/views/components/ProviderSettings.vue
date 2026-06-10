<template>
  <div class="provider-settings">
    <!-- 供应商列表 -->
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span>模型供应商</span>
          <div class="header-buttons">
            <el-button size="small" @click="loadProviders" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="primary" size="small" @click="showAddProviderDialog">
              <el-icon><Plus /></el-icon>
              新增供应商
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="providerList" stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="providerName" label="供应商名称" width="150" />
        <el-table-column prop="providerKey" label="标识" width="120">
          <template #default="{ row }">
            <code>{{ row.providerKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="API地址" min-width="250" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showProviderDetail(row)">
              <el-icon><View /></el-icon>
              查看模型
            </el-button>
            <el-button type="warning" link size="small" @click="showEditProviderDialog(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button type="danger" link size="small" @click="deleteProvider(row)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
          v-if="total > 0"
          v-model:current-page="pageParams.pageNum"
          v-model:page-size="pageParams.pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadProviders"
          @current-change="loadProviders"
          class="pagination"
      />
    </el-card>

    <!-- 供应商详情对话框（包含模型列表） -->
    <el-dialog
        v-model="providerDetailVisible"
        :title="currentProvider?.providerName + ' - 模型配置'"
        width="800px"
    >
      <div class="provider-detail">
        <div class="provider-info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="供应商名称">{{ currentProvider?.providerName }}</el-descriptions-item>
            <el-descriptions-item label="标识">{{ currentProvider?.providerKey }}</el-descriptions-item>
            <el-descriptions-item label="API地址" span="2">{{ currentProvider?.baseUrl }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="currentProvider?.status === 1 ? 'success' : 'danger'" size="small">
                {{ currentProvider?.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="model-list-header">
          <span class="title">模型列表</span>
          <el-button type="primary" size="small" @click="showAddModelDialog">
            <el-icon><Plus /></el-icon>
            添加模型
          </el-button>
        </div>

        <el-table :data="currentModels" stripe style="width: 100%" size="small">
          <el-table-column prop="modelName" label="模型名称" width="180" />
          <el-table-column prop="modelKey" label="模型标识" width="150">
            <template #default="{ row }">
              <code>{{ row.modelKey }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="capabilityType" label="能力类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getCapabilityTagType(row.capabilityType)" size="small">
                {{ getCapabilityTypeLabel(row.capabilityType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="modelSchema" label="协议" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ row.modelSchema || 'openai' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="使用情况" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isUsed" type="warning" size="small">
                系统{{ getSystemUsedLabel(row.usedBy) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="showEditModelDialog(row)">
                编辑
              </el-button>
              <el-button type="danger" link size="small" @click="deleteModel(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 供应商编辑对话框 -->
    <el-dialog
        v-model="providerDialogVisible"
        :title="isEditProvider ? '编辑供应商' : '新增供应商'"
        width="550px"
    >
      <el-form :model="providerForm" :rules="providerRules" ref="providerFormRef" label-width="100px">
        <el-form-item label="供应商名称" prop="providerName">
          <el-input v-model="providerForm.providerName" placeholder="如：OpenAI" />
        </el-form-item>
        <el-form-item label="标识" prop="providerKey">
          <el-input v-model="providerForm.providerKey" placeholder="如：openai" />
          <div class="form-tip">唯一标识，建议使用英文小写</div>
        </el-form-item>
        <el-form-item label="API地址" prop="baseUrl">
          <el-input v-model="providerForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input v-model="providerForm.apiKey" type="password" show-password placeholder="请输入API Key" />
        </el-form-item>
        <el-form-item label="Secret" prop="secret">
          <el-input v-model="providerForm.secret" type="password" show-password placeholder="Secret（可选）" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="providerForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProvider" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 模型编辑对话框 -->
    <el-dialog
        v-model="modelDialogVisible"
        :title="isEditModel ? '编辑模型' : '添加模型'"
        width="650px"
    >
      <el-form :model="modelForm" :rules="modelRules" ref="modelFormRef" label-width="100px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="modelForm.modelName" placeholder="如：GPT-3.5 Turbo" />
        </el-form-item>
        <!-- 模型标识 - 必填，API调用时使用 -->
        <el-form-item label="模型标识" prop="modelKey" required>
          <el-input v-model="modelForm.modelKey" placeholder="如：Qwen/Qwen3-8B-GGUF, gpt-3.5-turbo" />
          <div class="form-tip">API调用时使用的模型名称，必填</div>
        </el-form-item>
        <el-form-item label="能力类型" prop="capabilityType">
          <el-select v-model="modelForm.capabilityType" placeholder="请选择能力类型" @change="onCapabilityTypeChange">
            <el-option
                v-for="item in capabilityTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="协议类型" prop="modelSchema">
          <el-select v-model="modelForm.modelSchema" placeholder="请选择协议类型">
            <el-option label="OpenAI协议" value="openai" />
            <el-option label="DashScope协议" value="dashscope" />
            <el-option label="文心一言协议" value="ernie" />
            <el-option label="讯飞星火协议" value="spark" />
            <el-option label="ModelScope协议" value="modelscope" />
            <el-option label="Cohere协议(Rerank)" value="cohere" />
          </el-select>
        </el-form-item>
        <el-form-item label="上下文长度" prop="contextLength" v-if="modelForm.capabilityType === 'chat'">
          <el-input-number v-model="modelForm.contextLength" :min="1024" :max="128000" :step="1024" />
          <span class="form-tip">单位：tokens</span>
        </el-form-item>
        <el-form-item label="向量维度" prop="dimension" v-if="modelForm.capabilityType === 'embedding'">
          <el-input-number v-model="modelForm.dimension" :min="128" :max="4096" :step="128" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="modelForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="modelDialogVisible = false">取消</el-button>
          <el-button type="warning" @click="testModelConnection" :loading="testingModel">
            <el-icon><Connection /></el-icon>
            测试连接
          </el-button>
          <el-button type="primary" @click="saveModel" :loading="saving">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>


<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, View, Edit, Delete, Connection } from '@element-plus/icons-vue'
import { providerApi } from '@/api/modelConfig'
import { modelConfigApi } from '@/api/chat'
import { useModelStore } from '@/stores/modelStore'

const loading = ref(false)
const saving = ref(false)
const providerDetailVisible = ref(false)
const providerDialogVisible = ref(false)
const modelDialogVisible = ref(false)
const isEditProvider = ref(false)
const isEditModel = ref(false)

const providerList = ref([])
const currentProvider = ref(null)
const currentModels = ref([])
const total = ref(0)

const modelStore = useModelStore()

const pageParams = reactive({
  pageNum: 1,
  pageSize: 10
})

const providerFormRef = ref(null)
const modelFormRef = ref(null)

const providerForm = reactive({
  id: null,
  providerName: '',
  providerKey: '',
  baseUrl: '',
  apiKey: '',
  secret: '',
  status: 1
})

const modelForm = reactive({
  id: null,
  providerId: null,
  modelName: '',
  modelKey: '',
  capabilityType: 'chat',
  modelSchema: 'openai',
  contextLength: 4096,
  dimension: 1536,
  status: 1
})

const providerRules = {
  providerName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  providerKey: [{ required: true, message: '请输入标识', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入API地址', trigger: 'blur' }]
}

const modelRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  modelKey: [{ required: true, message: '请输入模型标识', trigger: 'blur' }],
  capabilityType: [{ required: true, message: '请选择能力类型', trigger: 'change' }]
}

const capabilityTypes = ref([
  { value: 'chat', label: '大语言模型' },
  { value: 'embedding', label: '向量嵌入模型' },
  { value: 'rerank', label: '重排序模型' },
  { value: 'stt', label: '语音转文本' },
  { value: 'tts', label: '文本转语音' },
  { value: 'vision', label: '图片识别模型' }
])

const getCapabilityTagType = (type) => {
  const types = {
    chat: 'primary',
    embedding: 'success',
    rerank: 'warning',
    stt: 'info',
    tts: 'info',
    vision: 'danger'
  }
  return types[type] || 'info'
}

const getCapabilityTypeLabel = (type) => {
  const item = capabilityTypes.value.find(t => t.value === type)
  return item ? item.label : type
}

const getSystemUsedLabel = (usedBy) => {
  const labels = {
    chat: '大模型',
    embedding: '嵌入模型',
    rerank: '重排模型',
    stt: '语音识别',
    tts: '语音合成',
    vision: '视觉模型'
  }
  return labels[usedBy] || usedBy
}

const loadProviders = async () => {
  loading.value = true
  try {
    const res = await providerApi.getProviderPage(pageParams.pageNum, pageParams.pageSize)
    if (res.code === 200 && res.data) {
      providerList.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载供应商失败:', error)
    ElMessage.error('加载供应商失败')
  } finally {
    loading.value = false
  }
}

const showProviderDetail = async (row) => {
  currentProvider.value = row
  try {
    const res = await providerApi.getProviderDetail(row.id)
    if (res.code === 200 && res.data) {
      currentModels.value = res.data.models || []
      currentModels.value.forEach(model => {
        console.log(`模型: ${model.modelName}, modelKey: ${model.modelKey}`)
      })
      providerDetailVisible.value = true
    }
  } catch (error) {
    console.error('加载供应商详情失败:', error)
    ElMessage.error('加载供应商详情失败')
  }
}

const showAddProviderDialog = () => {
  isEditProvider.value = false
  resetProviderForm()
  providerDialogVisible.value = true
}

const showEditProviderDialog = (row) => {
  isEditProvider.value = true
  Object.assign(providerForm, {
    id: row.id,
    providerName: row.providerName,
    providerKey: row.providerKey,
    baseUrl: row.baseUrl,
    apiKey: row.apiKey || '',
    secret: row.secret || '',
    status: row.status
  })
  providerDialogVisible.value = true
}

const saveProvider = async () => {
  if (!providerFormRef.value) return
  await providerFormRef.value.validate()

  saving.value = true
  try {
    let res
    if (isEditProvider.value) {
      res = await providerApi.updateProvider(providerForm)
    } else {
      res = await providerApi.addProvider(providerForm)
    }
    if (res.code === 200 && res.data === true) {
      ElMessage.success(isEditProvider.value ? '更新成功' : '新增成功')
      providerDialogVisible.value = false
      await loadProviders()
    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (error) {
    console.error('保存供应商失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const deleteProvider = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除供应商"${row.providerName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await providerApi.deleteProvider(row.id)
    if (res.code === 200 && res.data === true) {
      ElMessage.success('删除成功')
      await loadProviders()
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

const showAddModelDialog = () => {
  isEditModel.value = false
  resetModelForm()
  modelForm.providerId = currentProvider.value.id
  modelDialogVisible.value = true
}

const showEditModelDialog = (row) => {
  isEditModel.value = true
  modelForm.id = row.id
  modelForm.modelName = row.modelName || ''
  modelForm.modelKey = row.modelKey || ''
  modelForm.capabilityType = row.capabilityType || 'chat'
  modelForm.modelSchema = row.modelSchema || 'openai'
  modelForm.contextLength = row.contextLength || 4096
  modelForm.dimension = row.dimension || 1536
  modelForm.status = row.status
  modelDialogVisible.value = true
}

const deleteModel = async (row) => {
  if (row.isUsed) {
    ElMessage.warning('该模型正在被系统使用，请先在系统模型配置中切换后再删除')
    return
  }

  try {
    await ElMessageBox.confirm(`确定删除模型"${row.modelName}"吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await providerApi.deleteModelConfig(row.id)
    if (res.code === 200 && res.data === true) {
      ElMessage.success('删除成功')
      await showProviderDetail(currentProvider.value)

      // 通知系统模型页面刷新数据
      await modelStore.refreshAllModels()
      window.dispatchEvent(new CustomEvent('model-data-changed'))

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

const resetProviderForm = () => {
  providerForm.id = null
  providerForm.providerName = ''
  providerForm.providerKey = ''
  providerForm.baseUrl = ''
  providerForm.apiKey = ''
  providerForm.secret = ''
  providerForm.status = 1
  if (providerFormRef.value) providerFormRef.value.resetFields()
}

// 添加测试连接相关状态
const testingModel = ref(false)

// 能力类型变化时的处理
const onCapabilityTypeChange = (type) => {
  // 根据能力类型设置默认协议
  if (type === 'rerank') {
    modelForm.modelSchema = 'cohere'
  } else if (type === 'embedding') {
    modelForm.modelSchema = 'openai'
  } else if (type === 'chat') {
    modelForm.modelSchema = 'openai'
  }
}

// 测试模型连接 - 需要传递完整的连接信息
const testModelConnection = async () => {
  // 验证模型名称
  if (!modelForm.modelName || modelForm.modelName.trim() === '') {
    ElMessage.warning('请先填写模型名称')
    return
  }

  // 验证模型标识
  if (!modelForm.modelKey || modelForm.modelKey.trim() === '') {
    ElMessage.warning('请先填写模型标识')
    return
  }

  // 获取当前供应商的完整信息
  const provider = currentProvider.value
  if (!provider) {
    ElMessage.warning('供应商信息不存在')
    return
  }

  // 验证供应商必要信息
  if (!provider.baseUrl) {
    ElMessage.warning('供应商API地址为空，请先编辑供应商填写API地址')
    return
  }

  testingModel.value = true
  try {
    // 构建测试数据 - 传递完整的模型标识
    const testData = {
      providerKey: provider.providerKey,
      modelKey: modelForm.modelKey,      // 传递模型标识
      modelName: modelForm.modelName,
      capabilityType: modelForm.capabilityType,
      modelSchema: modelForm.modelSchema,
      baseUrl: provider.baseUrl,
      apiKey: provider.apiKey || '',
      secret: provider.secret || ''
    }

    console.log('测试连接请求数据:', testData)

    const res = await providerApi.testModelConfig(testData)
    if (res.code === 200 && res.data === true) {
      ElMessage.success('连接测试成功！')
    } else {
      ElMessage.error(res.msg || '连接测试失败，请检查配置')
    }
  } catch (error) {
    console.error('测试连接失败:', error)
    ElMessage.error('测试连接失败：' + (error.message || '未知错误'))
  } finally {
    testingModel.value = false
  }
}

// 保存模型前自动测试（可选）
const saveModel = async () => {
  if (!modelFormRef.value) return
  await modelFormRef.value.validate()

  saving.value = true
  try {
    const saveData = {
      id: modelForm.id,
      providerId: modelForm.providerId,
      modelName: modelForm.modelName,
      // 使用供应商的标识作为模型标识
      modelKey: modelForm.modelKey,
      capabilityType: modelForm.capabilityType,
      modelSchema: modelForm.modelSchema,
      contextLength: modelForm.contextLength,
      dimension: modelForm.dimension,
      status: modelForm.status
    }

    let res
    if (isEditModel.value) {
      res = await providerApi.updateModelConfig(saveData)
    } else {
      res = await providerApi.addModelConfig(saveData)
    }

    if (res.code === 200 && res.data === true) {
      ElMessage.success(isEditModel.value ? '更新成功' : '新增成功')
      modelDialogVisible.value = false

      // 刷新当前供应商详情
      await showProviderDetail(currentProvider.value)

      // 通知系统模型页面刷新数据
      await modelStore.refreshAllModels()
      window.dispatchEvent(new CustomEvent('model-data-changed'))

    } else {
      ElMessage.error(res.msg || '操作失败')
    }
  } catch (error) {
    console.error('保存模型失败:', error)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 重置模型表单时重置协议
const resetModelForm = () => {
  modelForm.id = null
  modelForm.modelName = ''
  modelForm.capabilityType = 'chat'
  modelForm.modelSchema = 'openai'
  modelForm.contextLength = 4096
  modelForm.dimension = 1536
  modelForm.status = 1
  if (modelFormRef.value) {
    modelFormRef.value.resetFields()
  }
}


onMounted(() => {
  loadProviders()
})
</script>

<style scoped>
.provider-settings {
  padding: 0;
}

.settings-card {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 16px !important;
}

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

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.provider-detail {
  padding: 0;
}

.provider-info {
  margin-bottom: 24px;
}

.model-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0 12px 0;
}

.model-list-header .title {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

:deep(.el-descriptions__label) {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  border-color: #2a2f4a !important;
}

:deep(.el-descriptions__content) {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
  border-color: #2a2f4a !important;
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
</style>