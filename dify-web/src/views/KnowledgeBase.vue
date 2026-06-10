<template>
  <div class="knowledge-base">
    <!-- 左右分栏布局 -->
    <div class="kb-layout">
      <!-- 左侧：知识库列表 -->
      <div class="kb-sidebar">
        <div class="sidebar-header">
          <h3>知识库</h3>
          <el-button type="primary" size="small" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            新建
          </el-button>
        </div>

        <div class="kb-list">
          <div
              v-for="kb in knowledgeBases"
              :key="kb.id"
              class="kb-item"
              :class="{ active: currentKB?.id === kb.id }"
              @click="selectKnowledgeBase(kb)"
          >
            <div class="kb-icon">
              <el-icon><Collection /></el-icon>
            </div>
            <div class="kb-info">
              <div class="kb-name">{{ kb.name }}</div>
              <div class="kb-stats">
                <span>{{ kb.docCount || 0 }} 文档</span>
                <span>{{ kb.chunkCount || 0 }} 分块</span>
              </div>
            </div>
            <div class="kb-actions" @click.stop>
              <el-dropdown trigger="click" @command="(cmd) => handleKbCommand(cmd, kb)">
                <el-button text size="small">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="upload">
                      <el-icon><Upload /></el-icon>
                      上传文档
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>
                      删除知识库
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>

          <div v-if="knowledgeBases.length === 0 && !loading" class="empty-kb-list">
            <el-empty description="暂无知识库" :image-size="80" />
            <el-button type="primary" size="small" @click="showCreateDialog = true">创建知识库</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧：知识库详情 -->
      <div class="kb-content" v-if="currentKB">
        <div class="content-header">
          <div class="header-info">
            <div class="kb-avatar">
              <el-icon :size="28"><FolderOpened /></el-icon>
            </div>
            <div class="header-details">
              <h2>{{ currentKB.name }}</h2>
              <p>{{ currentKB.description || '向量知识库' }}</p>
            </div>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="uploadToCurrentKb">
              <el-icon><Upload /></el-icon>
              上传文档
            </el-button>
            <el-button @click="refreshCurrentData" :loading="docLoading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>

        <!-- 上传文档对话框 -->
        <el-dialog
            v-model="showUploadDialog"
            :title="`上传文档到「${currentKB?.name}」`"
            width="500px"
            @close="handleDialogClose"
        >
          <el-upload
              class="upload-area"
              drag
              :action="uploadUrl"
              :headers="uploadHeaders"
              :data="{ kbName: currentKB?.name }"
              :on-success="handleUploadSuccess"
              :on-error="handleUploadError"
              :before-upload="beforeUpload"
              :on-progress="handleUploadProgress"
              :file-list="fileList"
              :auto-upload="true"
              :show-file-list="false"
          >
            <el-icon class="upload-icon"><UploadFilled /></el-icon>
            <div class="upload-text">将文件拖到此处，或点击上传</div>
            <div class="upload-hint">支持 .txt, .pdf, .md, .docx 等格式</div>
          </el-upload>

          <div v-if="uploading" class="upload-progress">
            <div class="progress-item">
              <span>{{ uploadingFileName }}</span>
              <el-progress :percentage="uploadProgress" :status="uploadStatus" />
            </div>
          </div>

          <template #footer>
            <el-button @click="closeUploadDialog">取消</el-button>
            <el-button type="primary" :loading="uploading" @click="handleManualUpload" :disabled="!selectedFile">
              确认上传
            </el-button>
          </template>
        </el-dialog>

        <!-- Tab 内容区域 -->
        <el-tabs v-model="activeTab" class="content-tabs">
          <el-tab-pane label="文档" name="documents">
            <div class="docs-container">
              <div class="docs-search">
                <el-input
                    v-model="docSearchKeyword"
                    placeholder="搜索文档..."
                    :prefix-icon="Search"
                    clearable
                    class="search-input"
                />
                <span class="total-info">共 {{ filteredDocuments.length }} 个文档</span>
              </div>

              <div v-if="docLoading" class="loading-container">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>加载文档中...</span>
              </div>

              <div v-else-if="filteredDocuments.length === 0" class="empty-docs">
                <el-empty description="暂无文档，请点击上传" :image-size="80">
                  <el-button type="primary" @click="uploadToCurrentKb">上传文档</el-button>
                </el-empty>
              </div>

              <div v-else class="docs-list">
                <div
                    v-for="doc in filteredDocuments"
                    :key="doc.id"
                    class="doc-item"
                >
                  <!-- 文档卡片头部 -->
                  <div class="doc-header" @click="toggleDocExpand(doc)">
                    <div class="doc-icon">
                      <el-icon><Document /></el-icon>
                    </div>
                    <div class="doc-info">
                      <div class="doc-name">{{ doc.name }}</div>
                      <div class="doc-meta">
                        <el-tag size="small" type="info">{{ doc.chunkCount || 0 }} 个分块</el-tag>
                        <span class="doc-date">{{ formatDate(doc.createTime) }}</span>
                      </div>
                    </div>
                    <div class="doc-actions" @click.stop>
                      <!-- 删除文档按钮 -->
                      <el-tooltip content="删除文档" placement="top">
                        <el-button
                            type="danger"
                            size="small"
                            circle
                            @click="deleteDocument(doc)"
                        >
                          <el-icon><Delete /></el-icon>
                        </el-button>
                      </el-tooltip>
                      <el-icon class="expand-icon">
                        <ArrowDown v-if="expandedDocId === doc.id" />
                        <ArrowRight v-else />
                      </el-icon>
                    </div>
                  </div>

                  <!-- 分块区域 -->
                  <div v-if="expandedDocId === doc.id" class="doc-chunks">
                    <div class="chunks-search">
                      <el-input
                          v-model="chunkSearchKeyword"
                          placeholder="搜索分块内容..."
                          :prefix-icon="Search"
                          clearable
                          size="small"
                      />
                    </div>

                    <div v-if="chunkLoading" class="loading-small">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>加载中...</span>
                    </div>

                    <div v-else-if="filteredChunksByDoc.length === 0" class="empty-chunks">
                      <span>暂无匹配的分块</span>
                    </div>

                    <div v-else class="chunks-list">
                      <div
                          v-for="(chunk, idx) in filteredChunksByDoc"
                          :key="chunk.id || idx"
                          class="chunk-item"
                      >
                        <div class="chunk-header">
                          <span class="chunk-index">#{{ idx + 1 }}</span>
                          <span class="chunk-size">{{ chunk.content?.length || 0 }} 字符</span>
                          <el-button text size="small" @click="copyChunkContent(chunk.content)">
                            <el-icon><CopyDocument /></el-icon>
                            复制
                          </el-button>
                        </div>
                        <div class="chunk-content">{{ chunk.content }}</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="向量检索" name="search">
            <div class="search-section">
              <div class="search-input-area">
                <el-alert
                    title="提示"
                    type="info"
                    description="输入问题，系统将从当前知识库中检索相关文档"
                    :closable="false"
                    show-icon
                />

                <!-- 在向量检索 Tab 中，修改使用模型部分 -->
                <el-form-item label="使用模型" style="margin-top: 16px">
                  <div class="model-info-wrapper">
                    <div class="model-badge" v-if="currentChatModelInfo">
                      <el-icon><Cpu /></el-icon>
                      <span class="model-name">{{ currentChatModelInfo.modelName }}</span>
                      <span class="model-provider" v-if="currentChatModelInfo.providerName">
        ({{ currentChatModelInfo.providerName }})
      </span>
                    </div>
                    <div class="model-badge model-badge-warning" v-else>
                      <el-icon><Warning /></el-icon>
                      <span>未配置模型</span>
                    </div>
                    <div class="model-tip">使用系统配置的大语言模型进行检索回答</div>
                  </div>
                </el-form-item>

                <el-input
                    v-model="searchQuery"
                    type="textarea"
                    :rows="3"
                    placeholder="输入问题，例如：什么是数据标准化？"
                />
                <div class="search-actions">
                  <el-button type="primary" @click="testSearch" :loading="searching">
                    <el-icon><Search /></el-icon>
                    检索
                  </el-button>
                </div>
              </div>

              <div v-if="searching" class="search-progress">
                <el-progress :percentage="searchProgress" :stroke-width="6" />
                <span>正在检索...</span>
              </div>

              <div v-if="searchResults.length > 0 || searchAnswer" class="results-area">
                <div v-if="searchAnswer" class="answer-box">
                  <div class="answer-header">
                    <el-icon><MagicStick /></el-icon>
                    <span>AI 回答</span>
                    <el-tag v-if="searchUsedModel" size="small" type="success">{{ searchUsedModel }}</el-tag>
                  </div>
                  <div class="answer-content">{{ searchAnswer }}</div>
                </div>

                <div v-if="searchResults.length > 0" class="related-docs">
                  <div class="related-header">相关文档（{{ searchResults.length }} 条）</div>
                  <div v-for="(result, idx) in searchResults" :key="idx" class="related-item">
                    <div class="related-score">相似度: {{ formatScore(result.score) }}</div>
                    <div class="related-content">{{ result.content }}</div>
                  </div>
                </div>
              </div>

              <div v-if="!searching && !searchResults.length && !searchAnswer && !searchError" class="empty-search">
                <el-empty description="输入问题开始检索" :image-size="100" />
              </div>

              <div v-if="searchError" class="error-area">
                <el-alert :title="searchError" type="error" show-icon />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 空状态：未选择知识库 -->
      <div class="kb-content-empty" v-else>
        <el-empty description="请选择或创建一个知识库" :image-size="120">
          <el-button type="primary" @click="showCreateDialog = true">创建知识库</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 创建知识库对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建知识库" width="550px">
      <el-form :model="newKB" :rules="kbRules" ref="kbFormRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="newKB.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="newKB.description" type="textarea" :rows="3" placeholder="请输入描述（可选）" />
        </el-form-item>
        <el-form-item label="Embedding模型">
          <el-select v-model="newKB.embeddingModel" placeholder="请选择Embedding模型" style="width: 100%">
            <el-option label="nomic-embed-text" value="nomic-embed-text" />
            <el-option label="text-embedding-ada-002" value="text-embedding-ada-002" />
            <el-option label="bge-large-zh" value="bge-large-zh" />
          </el-select>
        </el-form-item>
        <el-form-item label="分块大小">
          <el-input-number v-model="newKB.chunkSize" :min="100" :max="2000" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="分块重叠">
          <el-input-number v-model="newKB.chunkOverlap" :min="0" :max="500" :step="50" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createKnowledgeBase" :loading="creating">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection, Link, Refresh, Search, Loading, FolderOpened,
  MagicStick, Document, Upload, UploadFilled, Plus, Delete,
  MoreFilled, Grid, CopyDocument, ArrowDown, ArrowRight
} from '@element-plus/icons-vue'
import { ragApi } from '@/api/rag'
import { systemModelApi } from '@/api/modelConfig'

// ==================== 模型相关 - 使用系统配置 ====================
const currentChatModelId = ref(null)
const currentChatModelInfo = ref(null)
const modelLoading = ref(false)
const searchModelConfigId = ref(null)  // 添加这一行
const searchUsedModel = ref('')         // 添加这一行

// 加载系统配置的大语言模型
const loadSystemChatModel = async () => {
  modelLoading.value = true
  try {
    const res = await systemModelApi.getCapabilities()
    if (res.code === 200 && res.data) {
      const chatCapability = res.data.chat
      if (chatCapability && chatCapability.modelConfigId) {
        currentChatModelId.value = chatCapability.modelConfigId
        currentChatModelInfo.value = {
          id: chatCapability.modelConfigId,
          modelName: chatCapability.modelName || chatCapability.modelKey,
          modelKey: chatCapability.modelKey,
          providerName: chatCapability.providerName,
          capabilityType: 'chat'
        }
        // 自动选中当前模型用于检索
        searchModelConfigId.value = chatCapability.modelConfigId
        searchUsedModel.value = currentChatModelInfo.value.modelName
        console.log('加载系统大语言模型成功:', currentChatModelInfo.value)
      } else {
        ElMessage.warning('未配置系统大语言模型，请先在设置中配置')
      }
    }
  } catch (error) {
    console.error('加载系统模型失败:', error)
  } finally {
    modelLoading.value = false
  }
}

// 添加知识库菜单命令处理
const handleKbCommand = (command, kb) => {
  if (command === 'upload') {
    uploadToKb(kb)
  } else if (command === 'delete') {
    deleteKb(kb)
  }
}

// 复制分块内容
const copyChunkContent = async (content) => {
  if (!content) return
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now - d
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  return `${d.getMonth() + 1}-${d.getDate()}`
}

// 文档菜单命令
const handleDocCommand = (command, doc) => {
  if (command === 'delete') {
    deleteDocument(doc)
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

// ==================== 知识库相关 ====================
const knowledgeBases = ref([])
const currentKB = ref(null)
const loading = ref(false)
const creating = ref(false)
const showCreateDialog = ref(false)
const showUploadDialog = ref(false)
const kbFormRef = ref()

// ==================== 文档相关 ====================
const documents = ref([])
const docLoading = ref(false)
const docSearchKeyword = ref('')
const expandedDocId = ref(null)
const chunkSearchKeyword = ref('')
const activeTab = ref('search')

// ==================== 分块相关 ====================
const chunksCache = ref(new Map())
const chunkLoading = ref(false)

// ==================== 上传相关 ====================
const uploadUrl = '/api/rag/upload'
const uploadHeaders = {
  token: localStorage.getItem('token') || '',
}
const fileList = ref([])
const uploading = ref(false)
const uploadingFileName = ref('')
const uploadProgress = ref(0)
const uploadStatus = ref('')
const selectedFile = ref(null)

// ==================== 搜索相关 ====================
const searchQuery = ref('')
const searchResults = ref([])
const searchAnswer = ref('')
const searching = ref(false)
const searchError = ref('')
const searchProgress = ref(0)

let cancelTokenSource = null
let progressInterval = null

// 新知识库表单
const newKB = ref({
  name: '',
  description: '',
  embeddingModel: 'nomic-embed-text',
  chunkSize: 500,
  chunkOverlap: 50
})

const kbRules = {
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

// ==================== 计算属性 ====================
const filteredDocuments = computed(() => {
  if (!docSearchKeyword.value) return documents.value
  const keyword = docSearchKeyword.value.toLowerCase()
  return documents.value.filter(doc =>
      doc.name?.toLowerCase().includes(keyword)
  )
})

const filteredChunksByDoc = computed(() => {
  const chunks = chunksCache.value.get(expandedDocId.value) || []
  if (!chunkSearchKeyword.value) return chunks
  const keyword = chunkSearchKeyword.value.toLowerCase()
  return chunks.filter(chunk =>
      chunk.content?.toLowerCase().includes(keyword)
  )
})

// ==================== 知识库操作 ====================
const loadKnowledgeBases = async () => {
  loading.value = true
  try {
    const res = await ragApi.getKnowledgeBases()
    if (res.code === 200 && res.data) {
      // 过滤掉系统模板库
      knowledgeBases.value = res.data.filter(kb => kb.name !== 'prompt_templates')
    } else {
      knowledgeBases.value = []
    }
  } catch (error) {
    console.error('加载知识库列表失败', error)
    knowledgeBases.value = []
  } finally {
    loading.value = false
  }
}

const createKnowledgeBase = async () => {
  await kbFormRef.value.validate()
  creating.value = true
  try {
    const res = await ragApi.createKnowledgeBase({
      name: newKB.value.name,
      description: newKB.value.description,
      embeddingModel: newKB.value.embeddingModel,
      chunkSize: newKB.value.chunkSize,
      chunkOverlap: newKB.value.chunkOverlap
    })
    if (res.code === 200) {
      ElMessage.success('创建成功')
      showCreateDialog.value = false
      newKB.value = {
        name: '',
        description: '',
        embeddingModel: 'nomic-embed-text',
        chunkSize: 500,
        chunkOverlap: 50
      }
      await loadKnowledgeBases()
    } else {
      ElMessage.error(res.msg || '创建失败')
    }
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

const selectKnowledgeBase = async (kb) => {
  if (currentKB.value?.id === kb.id) return
  currentKB.value = kb
  expandedDocId.value = null
  chunksCache.value.clear()
  await loadDocuments()
}

const deleteKb = async (kb) => {
  try {
    await ElMessageBox.confirm(`确定删除知识库「${kb.name}」吗？`, '提示', { type: 'warning' })
    const res = await ragApi.deleteKnowledgeBase(kb.name)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (currentKB.value?.id === kb.id) {
        currentKB.value = null
      }
      await loadKnowledgeBases()
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// ==================== 文档操作 ====================
const loadDocuments = async () => {
  if (!currentKB.value) return
  docLoading.value = true
  try {
    const res = await ragApi.getDocumentsByKb(currentKB.value.name)
    if (res.code === 200 && res.data) {
      documents.value = res.data.map(doc => ({
        id: doc.id,
        name: doc.name,
        documentId: doc.documentId || doc.id,
        chunkCount: doc.chunkCount || 0,
        createTime: doc.createTime
      }))
      currentKB.value.docCount = documents.value.length
    }
  } catch (error) {
    console.error('加载文档失败', error)
    documents.value = []
  } finally {
    docLoading.value = false
  }
}

const loadDocumentChunks = async (docId, documentId) => {
  if (chunksCache.value.has(docId)) {
    return chunksCache.value.get(docId)
  }

  if (!currentKB.value) return []

  chunkLoading.value = true
  try {
    const res = await ragApi.getChunksByKb(currentKB.value.name)
    let allChunks = []
    if (res && res.code === 200 && res.data) {
      allChunks = Array.isArray(res.data) ? res.data : (res.data.chunks || [])
    }

    const docChunks = allChunks.filter(chunk => {
      const metaDocumentId = chunk.metadata?.documentId
      const chunkDocumentId = chunk.documentId
      const chunkId = chunk.id || ''

      return metaDocumentId === documentId ||
          chunkDocumentId === documentId ||
          chunkId.startsWith(documentId)
    }).map((chunk, index) => ({
      id: chunk.id,
      content: chunk.text || chunk.content,
      index: index
    }))

    docChunks.sort((a, b) => {
      const aIndex = parseInt(a.id?.split('_').pop()) || 0
      const bIndex = parseInt(b.id?.split('_').pop()) || 0
      return aIndex - bIndex
    })

    chunksCache.value.set(docId, docChunks)
    return docChunks
  } catch (error) {
    console.error('加载分块失败:', error)
    return []
  } finally {
    chunkLoading.value = false
  }
}

const toggleDocExpand = async (doc) => {
  if (expandedDocId.value === doc.id) {
    expandedDocId.value = null
    chunkSearchKeyword.value = ''
  } else {
    expandedDocId.value = doc.id
    chunkSearchKeyword.value = ''
    await loadDocumentChunks(doc.id, doc.documentId)
  }
}

const deleteDocument = async (doc) => {
  try {
    await ElMessageBox.confirm(`确定删除文档「${doc.name}」吗？`, '提示', { type: 'warning' })
    const res = await ragApi.deleteDocument(currentKB.value.name, doc.documentId)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      await loadDocuments()
      chunksCache.value.delete(doc.id)
      if (expandedDocId.value === doc.id) {
        expandedDocId.value = null
      }
    } else {
      ElMessage.error(res.msg || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// ==================== 上传操作 ====================
const resetUploadState = () => {
  uploading.value = false
  uploadingFileName.value = ''
  uploadProgress.value = 0
  uploadStatus.value = ''
  selectedFile.value = null
  fileList.value = []
}

const closeUploadDialog = () => {
  showUploadDialog.value = false
  resetUploadState()
}

const handleDialogClose = () => {
  resetUploadState()
}

const beforeUpload = (file) => {
  selectedFile.value = file
  uploadingFileName.value = file.name
  uploadProgress.value = 0
  uploadStatus.value = ''
  uploading.value = true
  return true
}

const handleUploadProgress = (event, file) => {
  if (event.total) {
    const progress = Math.round((event.loaded / event.total) * 100)
    uploadProgress.value = progress
  }
}

const handleUploadSuccess = (response, file) => {
  uploadProgress.value = 100
  uploadStatus.value = 'success'

  if (response && (response.code === 200 || response.success === true)) {
    ElMessage.success(`${file.name} 上传成功！`)
    setTimeout(() => {
      showUploadDialog.value = false
      resetUploadState()
      loadDocuments()
      loadKnowledgeBases()
    }, 1500)
  } else {
    uploadStatus.value = 'exception'
    ElMessage.error(response?.msg || response?.message || `${file.name} 上传失败`)
    uploading.value = false
  }
}

const handleUploadError = (error, file) => {
  uploadStatus.value = 'exception'
  uploadProgress.value = 0
  ElMessage.error(`${file.name} 上传失败：${error.message || '网络错误'}`)
  uploading.value = false
}

const handleManualUpload = () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
}

// ==================== 检索操作 ====================
const formatScore = (score) => {
  if (score === undefined || score === null) return '未知'
  return typeof score === 'number' ? score.toFixed(4) : score
}

const cancelSearch = () => {
  if (cancelTokenSource) {
    cancelTokenSource.cancel('用户取消搜索')
    cancelTokenSource = null
  }
  searching.value = false
  if (progressInterval) clearInterval(progressInterval)
  searchProgress.value = 0
  ElMessage.info('已取消搜索')
}

// 修改 testSearch 函数，传入模型配置ID
const testSearch = async () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  if (!currentKB.value) return

  // 检查是否配置了模型
  if (!currentChatModelId.value) {
    ElMessage.warning('未配置系统大语言模型，请先在设置中配置')
    return
  }

  if (cancelTokenSource) cancelTokenSource.cancel('新请求开始')

  searching.value = true
  searchError.value = ''
  searchResults.value = []
  searchAnswer.value = ''
  searchProgress.value = 0

  if (progressInterval) clearInterval(progressInterval)
  progressInterval = setInterval(() => {
    if (searchProgress.value < 90) searchProgress.value += 10
  }, 2000)

  try {
    // 构建请求参数，使用系统配置的模型ID
    const requestData = {
      text: searchQuery.value.trim(),
      topN: 5,
      configId: currentChatModelId.value  // 直接使用系统配置的模型ID
    }

    console.log('检索使用模型配置ID:', currentChatModelId.value)

    const res = await ragApi.searchInKb(currentKB.value.name, requestData)

    if (progressInterval) clearInterval(progressInterval)
    searchProgress.value = 100

    let responseData = null

    if (res && res.code === 200 && res.data) {
      responseData = res.data
    } else if (res && (res.answer || res.details)) {
      responseData = res
    } else if (res && typeof res === 'object') {
      responseData = res
    }

    if (responseData && (responseData.answer || responseData.details)) {
      if (responseData.answer) {
        searchAnswer.value = responseData.answer
      }

      let details = responseData.details || responseData.results || []
      searchResults.value = details.map((item, index) => ({
        id: index,
        content: item.content || item.text,
        score: item.score || item.distance
      }))

      ElMessage.success(`找到 ${searchResults.value.length} 条相关文档`)
    } else {
      searchError.value = res?.msg || res?.message || '检索失败'
      ElMessage.error(searchError.value)
    }
  } catch (error) {
    if (progressInterval) clearInterval(progressInterval)
    searchProgress.value = 0
    searchError.value = error.message || '检索失败'
    ElMessage.error('检索失败：' + (error.message || '网络错误'))
  } finally {
    searching.value = false
    setTimeout(() => {
      if (searchProgress.value === 100) searchProgress.value = 0
    }, 3000)
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadSystemChatModel()  // 先加载系统模型
  await loadKnowledgeBases()
  if (knowledgeBases.value.length > 0) {
    selectKnowledgeBase(knowledgeBases.value[0])
  }
})

onUnmounted(() => {
  if (progressInterval) clearInterval(progressInterval)
  if (cancelTokenSource) cancelTokenSource.cancel('组件卸载')
})

const loadAllChunks = async () => {
  if (!currentKB.value) return
  chunkLoading.value = true
  try {
    const res = await ragApi.getChunksByKb(currentKB.value.name)
    if (res.code === 200 && res.data) {
      const chunksList = res.data.map((item, index) => ({
        id: item.id || index,
        content: item.text || item.content,
        vectorized: !!(item.embedding && item.embedding.length > 0),
        documentId: item.documentId
      }))
      currentKB.value.chunkCount = chunksList.length
    }
  } catch (error) {
    console.error('加载分块失败:', error)
  } finally {
    chunkLoading.value = false
  }
}

const refreshCurrentData = () => {
  loadDocuments()
  loadAllChunks()
  ElMessage.success('数据已刷新')
}

const uploadToCurrentKb = () => {
  if (currentKB.value) {
    showUploadDialog.value = true
  }
}

const uploadToKb = (kb) => {
  if (currentKB.value?.id !== kb.id) {
    selectKnowledgeBase(kb)
  }
  showUploadDialog.value = true
}
</script>

<style scoped>
/* ========== 左右分栏布局 ========== */
.knowledge-base {
  height: 100%;
  background: #0a0e27;
  overflow: hidden;
}

.kb-layout {
  display: flex;
  height: 100%;
  gap: 0;
  overflow: hidden;
}

/* ========== 左侧知识库列表 ========== */
.kb-sidebar {
  width: 280px;
  background: #0f1228;
  border-right: 1px solid #2a2f4a;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 20px 16px;
  border-bottom: 1px solid #2a2f4a;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.sidebar-header .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
}

.kb-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.kb-list::-webkit-scrollbar {
  width: 4px;
}

.kb-list::-webkit-scrollbar-track {
  background: #0f1228;
}

.kb-list::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 2px;
}

.kb-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.kb-item:hover {
  background: #1a1f3a;
}

.kb-item.active {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(118, 75, 162, 0.1));
  border: 1px solid rgba(102, 126, 234, 0.3);
}

.kb-icon {
  width: 40px;
  height: 40px;
  background: #1a1f3a;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667eea;
}

.kb-item.active .kb-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #ffffff;
}

.kb-info {
  flex: 1;
  min-width: 0;
}

.kb-name {
  font-size: 14px;
  font-weight: 500;
  color: #ffffff;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #64748b;
}

.kb-actions {
  opacity: 0;
  transition: opacity 0.2s;
}

.kb-item:hover .kb-actions {
  opacity: 1;
}

.empty-kb-list {
  padding: 40px 16px;
  text-align: center;
}

/* ========== 右侧内容区域 ========== */
.kb-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0a0e27;
}

.kb-content-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0e27;
}

/* 内容头部 */
.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: #1a1f3a;
  border-bottom: 1px solid #2a2f4a;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.kb-avatar {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
}

.header-details h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
  color: #ffffff;
}

.header-details p {
  margin: 0;
  font-size: 13px;
  color: #94a3b8;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.header-actions .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

.header-actions .el-button--default {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

/* ========== Tab 样式 ========== */
.content-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
  background: #0f1228;
  flex-shrink: 0;
}

.content-tabs :deep(.el-tabs__item) {
  color: #94a3b8;
  font-weight: 500;
  font-size: 14px;
}

.content-tabs :deep(.el-tabs__item.is-active) {
  color: #a78bfa;
}

.content-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
}

.content-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

/* ========== 文档列表区域 ========== */
.docs-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.docs-search {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-shrink: 0;
}

.docs-search .search-input {
  width: 280px;
}

.docs-search :deep(.el-input__wrapper) {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  box-shadow: none;
}

.docs-search :deep(.el-input__wrapper:hover),
.docs-search :deep(.el-input__wrapper.is-focus) {
  border-color: #667eea;
}

.docs-search :deep(.el-input__inner) {
  color: #ffffff;
}

.total-info {
  font-size: 13px;
  color: #64748b;
}

.docs-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.docs-list::-webkit-scrollbar {
  width: 6px;
}

.docs-list::-webkit-scrollbar-track {
  background: #0f1228;
  border-radius: 3px;
}

.docs-list::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

/* 文档项 */
.doc-item {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 14px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.doc-item:hover {
  border-color: #667eea;
}

.doc-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  cursor: pointer;
  transition: background 0.2s;
}

.doc-header:hover {
  background: #22284a;
}

.doc-icon {
  width: 40px;
  height: 40px;
  background: rgba(102, 126, 234, 0.15);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a78bfa;
}

.doc-info {
  flex: 1;
}

.doc-name {
  font-size: 15px;
  font-weight: 500;
  color: #ffffff;
  margin-bottom: 6px;
}

.doc-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.doc-meta .el-tag {
  background: rgba(100, 116, 139, 0.2);
  border: 1px solid rgba(100, 116, 139, 0.3);
  color: #94a3b8;
  font-size: 11px;
  height: 22px;
}

.doc-date {
  font-size: 11px;
  color: #64748b;
}

.expand-icon {
  color: #64748b;
  transition: transform 0.2s;
}

/* 分块区域 */
.doc-chunks {
  padding: 0 18px 18px 18px;
  background: #0f1228;
  border-top: 1px solid #2a2f4a;
}

.chunks-search {
  margin-bottom: 16px;
  padding-top: 16px;
}

.chunks-search :deep(.el-input__wrapper) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 8px;
}

.chunks-list {
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunks-list::-webkit-scrollbar {
  width: 4px;
}

.chunk-item {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  padding: 12px;
  transition: all 0.2s;
}

.chunk-item:hover {
  border-color: #667eea;
}

.chunk-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #2a2f4a;
}

.chunk-index {
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 2px 8px;
  border-radius: 16px;
  font-size: 11px;
  font-weight: 600;
  color: #ffffff;
}

.chunk-size {
  font-size: 11px;
  color: #64748b;
}

.chunk-header .el-button {
  margin-left: auto;
  color: #818cf8 !important;
}

.chunk-content {
  font-size: 13px;
  line-height: 1.6;
  color: #cbd5e6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 120px;
  overflow-y: auto;
}

/* ========== 检索区域 ========== */
.search-section {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.search-input-area {
  flex-shrink: 0;
  margin-bottom: 20px;
}

.search-input-area :deep(.el-alert) {
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 10px;
}

.search-input-area :deep(.el-alert__title) {
  color: #cbd5e6;
}

.search-input-area :deep(.el-form-item__label) {
  color: #cbd5e6;
}

.search-input-area :deep(.el-textarea__inner) {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  color: #ffffff;
  border-radius: 10px;
}

.search-input-area :deep(.el-textarea__inner:focus) {
  border-color: #667eea;
}

.search-actions {
  margin-top: 16px;
}

.search-actions .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2);
  border: none;
  border-radius: 10px;
  padding: 10px 24px;
}

.search-progress {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  background: #0f1228;
  border-radius: 10px;
  margin-bottom: 20px;
}

.search-progress span {
  font-size: 13px;
  color: #94a3b8;
}

.results-area {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.answer-box {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 14px;
  overflow: hidden;
}

.answer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: #22284a;
  border-bottom: 1px solid #2a2f4a;
  font-weight: 600;
  color: #ffffff;
}

.answer-header .el-tag {
  margin-left: auto;
}

.answer-content {
  padding: 18px;
  line-height: 1.6;
  color: #cbd5e6;
  white-space: pre-wrap;
}

.related-docs {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 14px;
  overflow: hidden;
}

.related-header {
  padding: 14px 18px;
  background: #22284a;
  border-bottom: 1px solid #2a2f4a;
  font-weight: 600;
  color: #ffffff;
}

.related-item {
  padding: 14px 18px;
  border-bottom: 1px solid #2a2f4a;
}

.related-item:last-child {
  border-bottom: none;
}

.related-score {
  font-size: 11px;
  color: #34d399;
  margin-bottom: 6px;
}

.related-content {
  font-size: 13px;
  line-height: 1.5;
  color: #cbd5e6;
}

.empty-search {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-area {
  margin-top: 16px;
}

/* ========== 加载状态 ========== */
.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 60px;
  gap: 12px;
  color: #94a3b8;
}

.loading-small {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 30px;
  gap: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.empty-docs,
.empty-chunks {
  text-align: center;
  padding: 60px 20px;
  color: #64748b;
}

/* ========== 上传区域 ========== */
.upload-area :deep(.el-upload-dragger) {
  background: #0f1228 !important;
  border: 2px dashed #2a2f4a !important;
  border-radius: 12px;
}

.upload-area :deep(.el-upload-dragger:hover) {
  border-color: #667eea !important;
}

.upload-icon {
  font-size: 48px;
  color: #667eea;
  margin-bottom: 12px;
}

.upload-text {
  font-size: 14px;
  color: #cbd5e6;
  margin-bottom: 6px;
}

.upload-hint {
  font-size: 12px;
  color: #64748b;
}

.upload-progress {
  margin-top: 16px;
}

/* ========== 对话框样式 ========== */
:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-form-item__label) {
  color: #cbd5e6 !important;
}

:deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
}

:deep(.el-input__inner) {
  color: #ffffff !important;
}

:deep(.el-select .el-input__wrapper) {
  background: #0f1228 !important;
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

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .kb-sidebar {
    width: 240px;
  }

  .content-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .docs-search {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .docs-search .search-input {
    width: 100%;
  }

  .content-tabs :deep(.el-tabs__content) {
    padding: 16px;
  }
}

@media screen and (max-width: 480px) {
  .kb-sidebar {
    width: 200px;
  }

  .kb-name {
    font-size: 12px;
  }

  .kb-stats {
    font-size: 10px;
  }

  .header-details h2 {
    font-size: 16px;
  }
}

/* ========== 模型显示样式 ========== */
.model-info-wrapper {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.model-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.15), rgba(118, 75, 162, 0.08));
  border: 1px solid rgba(102, 126, 234, 0.3);
  border-radius: 24px;
  font-size: 13px;
  color: #a78bfa;
}

.model-badge .el-icon {
  font-size: 14px;
}

.model-badge .model-name {
  font-weight: 500;
  color: #ffffff;
}

.model-badge .model-provider {
  color: #94a3b8;
  font-size: 12px;
}

.model-badge-warning {
  background: rgba(245, 158, 11, 0.15);
  border-color: rgba(245, 158, 11, 0.3);
  color: #fbbf24;
}

.model-tip {
  font-size: 12px;
  color: #64748b;
}
</style>