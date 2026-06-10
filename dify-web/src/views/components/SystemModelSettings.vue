<template>
  <div class="system-model-settings">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span>系统模型配置</span>
          <div class="header-buttons">
            <el-button size="small" @click="loadAllData" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
          title="说明"
          type="info"
          :closable="false"
          show-icon
          class="info-alert"
      >
        <template #default>
          <p>系统模型配置用于设置AI平台各项功能使用的默认模型：</p>
          <ul>
            <li><strong>大语言模型</strong>：用于对话、推理、代码生成等任务</li>
            <li><strong>向量嵌入模型</strong>：用于知识库检索、文本向量化</li>
            <li><strong>重排序模型</strong>：优化检索结果排序，提升相关性</li>
            <li><strong>语音转文本</strong>：语音识别功能</li>
            <li><strong>文本转语音</strong>：语音合成功能</li>
            <li><strong>图片识别模型</strong>：图像理解、视觉问答</li>
          </ul>
        </template>
      </el-alert>

      <el-table
          :data="capabilityList"
          stripe
          v-loading="loading"
          style="width: 100%; margin-top: 20px;"
      >
        <el-table-column prop="capabilityName" label="能力类型" width="140">
          <template #default="{ row }">
            <div class="capability-info">
              <el-tag :type="getCapabilityTagType(row.capabilityType)" size="small">
                {{ row.capabilityName }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="capabilityType" label="标识" width="100">
          <template #default="{ row }">
            <code class="capability-code">{{ row.capabilityType }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="modelName" label="当前使用的模型" min-width="250">
          <template #default="{ row }">
            <div v-if="row.modelConfigId" class="model-info">
              <span class="model-name">{{ row.modelName || '-' }}</span>
              <span class="provider-name" v-if="row.providerName">({{ row.providerName }})</span>
            </div>
            <el-tag v-else type="danger" size="small">未配置</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button
                type="primary"
                link
                size="small"
                @click="openModelSelector(row)"
            >
              <el-icon><Edit /></el-icon>
              切换模型
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 模型选择器对话框 -->
    <el-dialog
        v-model="selectorVisible"
        :title="`选择${currentCapability?.capabilityName || '模型'}`"
        width="750px"
        class="model-selector-dialog"
    >
      <div class="model-selector">
        <!-- 搜索框 -->
        <el-input
            v-model="modelSearchKeyword"
            placeholder="搜索模型名称、标识或供应商"
            clearable
            :prefix-icon="Search"
            class="search-input"
        />

        <!-- 供应商筛选标签 -->
        <div class="provider-filters">
          <el-radio-group v-model="selectedProviderId" @change="onProviderFilterChange">
            <el-radio-button :value="null">全部</el-radio-button>
            <el-radio-button
                v-for="provider in providers"
                :key="provider.id"
                :value="provider.id"
            >
              {{ provider.providerName }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <!-- 模型列表 -->
        <div class="model-list" v-loading="modelsLoading">
          <div
              v-for="model in filteredModels"
              :key="model.id"
              class="model-item"
              :class="{ active: selectedModelId === model.id }"
              @click="selectModel(model)"
          >
            <div class="model-item-header">
              <div class="model-item-left">
                <span class="model-item-name">{{ model.modelName }}</span>
                <span class="model-item-key">{{ model.modelKey }}</span>
              </div>
              <div class="model-item-tags">
                <el-tag :type="getCapabilityTagType(model.capabilityType)" size="small">
                  {{ getCapabilityTypeLabel(model.capabilityType) }}
                </el-tag>
                <el-tag size="small" type="info">{{ model.provider?.providerName || '-' }}</el-tag>
              </div>
            </div>
            <div class="model-item-info">
              <span class="model-schema" v-if="model.modelSchema">协议: {{ model.modelSchema }}</span>
              <span class="model-context" v-if="model.contextLength">上下文: {{ model.contextLength }} tokens</span>
              <span class="model-dimension" v-if="model.dimension">维度: {{ model.dimension }}</span>
            </div>
            <div class="model-item-status" v-if="model.status !== 1">
              <el-tag type="danger" size="small">已禁用</el-tag>
            </div>
          </div>

          <el-empty v-if="filteredModels.length === 0" description="暂无可用模型">
            <template #extra>
              <el-button type="primary" size="small" @click="$router.push('/settings?tab=provider')">
                前往添加模型
              </el-button>
            </template>
          </el-empty>
        </div>

        <!-- 当前选中的模型信息 -->
        <div class="selected-info" v-if="selectedModelInfo">
          <el-divider />
          <div class="selected-title">
            <el-icon><Check /></el-icon>
            <span>已选择模型</span>
          </div>
          <div class="selected-content">
            <span class="selected-name">{{ selectedModelInfo.modelName }}</span>
            <span class="selected-provider">{{ selectedModelInfo.provider?.providerName }}</span>
            <span class="selected-key">{{ selectedModelInfo.modelKey }}</span>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="selectorVisible = false">取消</el-button>
          <el-button
              type="primary"
              @click="confirmModelSwitch"
              :loading="switching"
              :disabled="!selectedModelId"
          >
            确认切换
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Edit, Search, Check } from '@element-plus/icons-vue'
import { systemModelApi, providerApi } from '@/api/modelConfig'
import { useModelStore } from '@/stores/modelStore'

const loading = ref(false)
const switching = ref(false)
const modelsLoading = ref(false)
const selectorVisible = ref(false)
const modelSearchKeyword = ref('')
const selectedProviderId = ref(null)

const capabilityList = ref([])
const allModels = ref([])
const providers = ref([])
const currentCapability = ref(null)
const selectedModelId = ref(null)
const selectedModelInfo = ref(null)

const modelStore = useModelStore()

// 过滤后的模型列表
const filteredModels = computed(() => {
  if (!currentCapability.value) return []

  let models = allModels.value.filter(m =>
      m.capabilityType === currentCapability.value.capabilityType
  )

  // 按供应商过滤
  if (selectedProviderId.value) {
    models = models.filter(m => m.providerId === selectedProviderId.value)
  }

  // 按关键词搜索
  if (modelSearchKeyword.value) {
    const keyword = modelSearchKeyword.value.toLowerCase()
    models = models.filter(m =>
        m.modelName.toLowerCase().includes(keyword) ||
        m.modelKey.toLowerCase().includes(keyword) ||
        m.provider?.providerName?.toLowerCase().includes(keyword)
    )
  }

  // 排序：启用的排在前面
  return models.sort((a, b) => (b.status || 0) - (a.status || 0))
})

// 加载所有数据
const loadAllData = async () => {
  loading.value = true
  try {
    await Promise.all([loadCapabilities(), loadAllModels(), loadProviders()])
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 加载系统能力配置
const loadCapabilities = async () => {
  try {
    const res = await systemModelApi.getCapabilities()
    if (res.code === 200 && res.data) {
      const capabilities = []
      const data = res.data

      const capabilityMap = {
        chat: '大语言模型',
        embedding: '向量嵌入模型',
        rerank: '重排序模型',
        stt: '语音转文本',
        tts: '文本转语音',
        vision: '图片识别模型'
      }

      // 遍历 data 对象（data 是 Map 类型）
      Object.keys(data).forEach(key => {
        const item = data[key]
        capabilities.push({
          ...item,
          capabilityName: capabilityMap[item.capabilityType] || item.capabilityType,
          capabilityType: item.capabilityType
        })
      })

      capabilityList.value = capabilities
    }
  } catch (error) {
    console.error('加载能力配置失败:', error)
  }
}


// 加载所有模型
const loadAllModels = async () => {
  try {
    const res = await providerApi.getEnabledProviders()
    if (res.code === 200 && res.data) {
      const allModelsData = []
      for (const provider of res.data) {
        try {
          const detailRes = await providerApi.getProviderDetail(provider.id)
          if (detailRes.code === 200 && detailRes.data) {
            const models = detailRes.data.models || []
            models.forEach(model => {
              model.provider = {
                id: provider.id,
                providerName: provider.providerName,
                providerKey: provider.providerKey
              }
              model.providerId = provider.id
              allModelsData.push(model)
            })
          }
        } catch (e) {
          console.warn(`加载供应商${provider.providerName}的模型失败:`, e)
        }
      }
      allModels.value = allModelsData
    }
  } catch (error) {
    console.error('加载模型列表失败:', error)
  }
}

// 加载供应商列表
const loadProviders = async () => {
  try {
    const res = await providerApi.getEnabledProviders()
    if (res.code === 200) {
      providers.value = res.data || []
    }
  } catch (error) {
    console.error('加载供应商失败:', error)
  }
}

// 打开模型选择器
const openModelSelector = async (capability) => {
  currentCapability.value = capability
  selectedModelId.value = capability.modelConfigId

  // 查找当前选中的模型信息
  if (selectedModelId.value) {
    selectedModelInfo.value = allModels.value.find(m => m.id === selectedModelId.value)
  } else {
    selectedModelInfo.value = null
  }

  modelSearchKeyword.value = ''
  selectedProviderId.value = null

  selectorVisible.value = true
}

// 供应商筛选变化
const onProviderFilterChange = () => {
  // 筛选变化时不清空已选模型，但提示用户
  if (selectedModelId.value) {
    const stillInList = filteredModels.value.some(m => m.id === selectedModelId.value)
    if (!stillInList) {
      ElMessage.info('当前选中的模型不在筛选结果中，请重新选择')
    }
  }
}

// 选择模型
const selectModel = (model) => {
  selectedModelId.value = model.id
  selectedModelInfo.value = model
}

// 获取能力标签样式
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

// 获取能力类型中文名
const getCapabilityTypeLabel = (type) => {
  const labels = {
    chat: '对话模型',
    embedding: '嵌入模型',
    rerank: '重排模型',
    stt: '语音识别',
    tts: '语音合成',
    vision: '视觉模型'
  }
  return labels[type] || type
}

const confirmModelSwitch = async () => {
  if (!selectedModelId.value) {
    ElMessage.warning('请选择一个模型')
    return
  }

  switching.value = true
  try {
    const res = await systemModelApi.updateCapability(
        currentCapability.value.capabilityType,
        selectedModelId.value
    )

    if (res.code === 200 && res.data === true) {
      ElMessage.success(`已切换${currentCapability.value.capabilityName}模型`)
      await loadCapabilities()
      selectorVisible.value = false

      // 触发全局事件，通知对话页面更新
      window.dispatchEvent(new CustomEvent('model-config-changed'))
    } else {
      ElMessage.error(res.msg || '切换失败')
    }
  } catch (error) {
    console.error('切换模型失败:', error)
    ElMessage.error('切换模型失败')
  } finally {
    switching.value = false
  }
}

// 监听 store 变化
watch(() => modelStore.lastUpdateTime, (newTime, oldTime) => {
  if (newTime !== oldTime && newTime !== null) {
    console.log('检测到模型数据变化，重新加载...')
    loadAllData()
  }
})

// 监听自定义事件
const handleModelDataChanged = () => {
  console.log('收到模型数据变化事件，重新加载...')
  loadAllData()
}

onMounted(() => {
  // 1. 立即加载数据
  loadAllData()

  // 2. 添加事件监听（而不是移除）
  window.addEventListener('model-data-changed', handleModelDataChanged)
})

// 组件卸载时才移除监听
onUnmounted(() => {
  window.removeEventListener('model-data-changed', handleModelDataChanged)
})
</script>

<style scoped>
.system-model-settings {
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

.info-alert {
  background: rgba(102, 126, 234, 0.1) !important;
  border-color: rgba(102, 126, 234, 0.3) !important;
  border-radius: 12px;
}

.info-alert :deep(.el-alert__title) {
  color: #a78bfa !important;
}

.info-alert p, .info-alert ul, .info-alert li {
  color: #cbd5e6;
}

.model-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.model-name {
  color: #cbd5e6;
  font-weight: 500;
}

.provider-name {
  color: #64748b;
  font-size: 12px;
}

/* 模型选择器样式 */
.model-selector {
  min-height: 450px;
}

.search-input {
  margin-bottom: 16px;
}

.provider-filters {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #2a2f4a;
}

.provider-filters :deep(.el-radio-button__inner) {
  background: #0f1228;
  border-color: #2a2f4a;
  color: #94a3b8;
}

.provider-filters :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-color: #667eea;
  color: #ffffff;
}

.model-list {
  max-height: 380px;
  overflow-y: auto;
}

.model-item {
  position: relative;
  padding: 14px 16px;
  margin-bottom: 10px;
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.model-item:hover {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.05);
  transform: translateX(4px);
}

.model-item.active {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.1);
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.model-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.model-item-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.model-item-name {
  font-size: 14px;
  font-weight: 600;
  color: #ffffff;
}

.model-item-key {
  font-size: 12px;
  font-family: monospace;
  color: #64748b;
  background: #1a1f3a;
  padding: 2px 8px;
  border-radius: 4px;
}

.model-item-tags {
  display: flex;
  gap: 6px;
}

.model-item-info {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #64748b;
}

.model-item-info span {
  font-family: monospace;
}

.model-item-status {
  position: absolute;
  top: 12px;
  right: 12px;
}

.selected-info {
  margin-top: 16px;
}

.selected-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #a78bfa;
  margin-bottom: 10px;
}

.selected-content {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: rgba(102, 126, 234, 0.1);
  border-radius: 8px;
}

.selected-name {
  font-weight: 600;
  color: #ffffff;
}

.selected-provider {
  color: #a78bfa;
  font-size: 12px;
}

.selected-key {
  font-family: monospace;
  color: #64748b;
  font-size: 12px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.capability-code {
  font-family: monospace;
  font-size: 12px;
  color: #a78bfa;
  background: rgba(102, 126, 234, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

/* 滚动条样式 */
.model-list::-webkit-scrollbar {
  width: 6px;
}

.model-list::-webkit-scrollbar-track {
  background: #0f1228;
  border-radius: 3px;
}

.model-list::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

.model-list::-webkit-scrollbar-thumb:hover {
  background: #667eea;
}
</style>