<template>
  <div class="rag-config">
    <el-divider content-position="left">基础配置</el-divider>

    <el-form-item label="查询内容" prop="query" class="query-form-item">
      <div class="prompt-editor">
        <el-input
            v-model="query"
            type="textarea"
            :rows="6"
            placeholder="请输入查询内容，支持变量，如：{{input.query}}"
            class="query-textarea"
        />
        <div class="floating-toolbar">
          <el-dropdown @command="insertQueryVariable">
            <el-button size="small" plain class="insert-btn">
              插入变量 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item divided><strong>📥 输入变量</strong></el-dropdown-item>
                <el-dropdown-item
                    v-for="input in inputVarList"
                    :key="`input.${input.name}`"
                    :command="`input.${input.name}`"
                >
                  <span class="var-code">{{ '{' }}{{ '{' }}input.{{ input.name }}{{ '}' }}{{ '}' }}</span>
                  <span class="var-desc"> - {{ input.description || input.name }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided><strong>📤 节点输出变量（{{ nodeOutputVars.length }}）</strong></el-dropdown-item>
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
          <el-button size="small" plain @click="query = ''" class="clear-btn">清空</el-button>
        </div>
      </div>
    </el-form-item>

    <el-divider content-position="left">检索配置</el-divider>

    <!-- 知识库选择：自定义下拉，替代 el-select -->
    <el-form-item label="知识库">
      <div class="kb-selector">
        <el-dropdown trigger="click" @command="selectKnowledgeBase">
          <div class="kb-trigger">
            <span v-if="kbName" class="kb-value">{{ kbName }}</span>
            <span v-else class="kb-placeholder">请选择知识库</span>
            <el-icon class="kb-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                  v-for="kb in knowledgeBaseList"
                  :key="kb.id || kb.name"
                  :command="kb.name || kb.kbName"
              >
                {{ kb.name || kb.kbName }}
              </el-dropdown-item>
              <el-dropdown-item v-if="knowledgeBaseList.length === 0" disabled>
                暂无知识库
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" plain @click="loadKnowledgeBaseList" class="refresh-btn">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </el-form-item>

    <el-form-item label="返回文档数量">
      <el-slider v-model="topK" :min="1" :max="20" :step="1" class="custom-slider" />
      <div class="slider-value">返回 {{ topK || 5 }} 个相关文档</div>
    </el-form-item>

    <el-form-item label="相似度阈值">
      <el-slider v-model="threshold" :min="0" :max="1" :step="0.05" class="custom-slider" />
      <div class="slider-value">阈值: {{ (threshold || 0) * 100 }}%</div>
    </el-form-item>

    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="outputVar" placeholder="例如: rag_documents" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>

    <el-divider content-position="left">测试</el-divider>

    <el-form-item>
      <el-button type="primary" plain @click="testRagNode" :loading="testing">
        <el-icon><VideoPlay /></el-icon> 测试检索
      </el-button>
    </el-form-item>

    <div v-if="testResult" class="test-result">
      <el-alert :title="testResult.success ? '检索成功' : '检索失败'" :type="testResult.success ? 'success' : 'error'" :closable="false" />
      <div v-if="testResult.success" class="test-result-content">
        <div class="result-stats">
          <el-tag type="info">检索到 {{ testResult.documentCount }} 个文档</el-tag>
        </div>
        <div class="result-documents">
          <div v-for="(doc, idx) in testResult.documents" :key="idx" class="result-document">
            <div class="doc-header">
              <span class="doc-index">文档 {{ idx + 1 }}</span>
              <el-tag size="small" type="warning" v-if="doc.score">相似度: {{ (doc.score * 100).toFixed(1) }}%</el-tag>
            </div>
            <div class="doc-content">{{ doc.content?.substring(0, 200) }}...</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, VideoPlay, Refresh } from '@element-plus/icons-vue'
import { ragApi } from '@/api'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})

const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

const query = computed({
  get: () => props.config.query || '',
  set: (val) => { props.config.query = val }
})

const kbName = computed({
  get: () => props.config.kbName || '',
  set: (val) => { props.config.kbName = val }
})

const topK = computed({
  get: () => props.config.topK ?? 5,
  set: (val) => { props.config.topK = val }
})

const threshold = computed({
  get: () => props.config.threshold ?? 0.7,
  set: (val) => { props.config.threshold = val }
})

const outputVar = computed({
  get: () => props.config.outputVar || 'rag_documents',
  set: (val) => { props.config.outputVar = val }
})

const outputVarDisplay = computed(() => {
  const varName = outputVar.value
  return `{{var.${varName}}}`
})

const knowledgeBaseList = ref([])
const loadingKbList = ref(false)
const testing = ref(false)
const testResult = ref(null)

const loadKnowledgeBaseList = async () => {
  if (knowledgeBaseList.value.length > 0) return
  loadingKbList.value = true
  try {
    const res = await ragApi.getKnowledgeBases()
    if (res.code === 200 && res.data) {
      knowledgeBaseList.value = res.data
    }
  } catch (error) {
    console.error('加载知识库列表失败:', error)
  } finally {
    loadingKbList.value = false
  }
}

const selectKnowledgeBase = (name) => {
  kbName.value = name
}

const insertQueryVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  query.value = query.value + variable
}

const testRagNode = async () => {
  if (!query.value) {
    ElMessage.warning('请输入查询内容')
    return
  }
  testing.value = true
  try {
    const res = await ragApi.searchDocument({
      kb: kbName.value,
      query: query.value,
      topK: topK.value
    })
    if (res.code === 200) {
      const data = res.data
      let documents = []
      if (data.details && Array.isArray(data.details)) {
        documents = data.details.map(doc => ({ content: doc.document || doc.content, score: doc.score }))
      }
      testResult.value = { success: true, documentCount: documents.length, documents }
      ElMessage.success(`检索成功，找到 ${documents.length} 个相关文档`)
    } else {
      testResult.value = { success: false, error: res.msg || '检索失败' }
      ElMessage.error(res.msg || '检索失败')
    }
  } catch (error) {
    testResult.value = { success: false, error: error.message }
    ElMessage.error('测试失败: ' + error.message)
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
/* ===== 变量和提示文字 ===== */
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
.form-tip {
  font-size: 12px;
  color: #8b8fa9;
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
  color: #8b8fa9;
  margin-top: 4px;
}

/* ===== 查询内容区域 ===== */
.prompt-editor {
  position: relative;
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 8px;
  padding: 8px;
}
.query-textarea :deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 6px !important;
  color: #ffffff !important;
  font-size: 13px;
  line-height: 1.6;
  min-height: 120px;
}
.query-textarea :deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}
.floating-toolbar {
  position: absolute;
  bottom: 14px;
  right: 16px;
  display: flex;
  gap: 6px;
  background: rgba(15, 18, 40, 0.8);
  padding: 4px 8px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
}
.insert-btn {
  padding: 4px 10px !important;
  font-size: 12px !important;
  background: rgba(102, 126, 234, 0.08) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
  color: #a78bfa !important;
}
.insert-btn:hover {
  background: rgba(102, 126, 234, 0.2) !important;
  border-color: #667eea !important;
}
.clear-btn {
  padding: 4px 10px !important;
  font-size: 12px !important;
  background: rgba(239, 68, 68, 0.08) !important;
  border: 1px solid rgba(239, 68, 68, 0.2) !important;
  color: #f87171 !important;
}
.clear-btn:hover {
  background: rgba(239, 68, 68, 0.2) !important;
  border-color: #ef4444 !important;
}

/* ===== 知识库自定义下拉 ===== */
.kb-selector {
  display: flex;
  gap: 8px;
  align-items: center;
}
.kb-trigger {
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
.kb-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.kb-value {
  color: #a78bfa;
}
.kb-placeholder {
  color: #64748b;
}
.kb-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.kb-trigger:hover .kb-arrow {
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

/* ===== 测试结果 ===== */
.test-result {
  margin-top: 12px;
}
.test-result-content {
  margin-top: 8px;
}
.result-stats {
  margin-bottom: 8px;
}
.result-documents {
  max-height: 200px;
  overflow-y: auto;
}
.result-document {
  background: #0f1228;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 6px;
  border: 1px solid #2a2f4a;
}
.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.doc-index {
  font-weight: bold;
  color: #a78bfa;
}
.doc-content {
  font-size: 13px;
  color: #c8d0e0;
  word-break: break-all;
}

/* ===== 全局深色覆盖（输入框、下拉菜单、按钮等） ===== */
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

:deep(.el-button--primary.is-plain) {
  background: rgba(102, 126, 234, 0.1) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}
:deep(.el-button--primary.is-plain:hover) {
  background: rgba(102, 126, 234, 0.2) !important;
  border-color: #667eea !important;
  color: #c4b5fd !important;
}

:deep(.el-divider) {
  border-color: #2a2f4a !important;
}
:deep(.el-divider__text) {
  color: #94a3b8 !important;
  font-weight: 500;
  font-size: 13px;
}

:deep(.el-alert) {
  background: rgba(102, 126, 234, 0.08) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
  border-radius: 6px !important;
}
:deep(.el-alert--success) {
  border-color: rgba(16, 185, 129, 0.3) !important;
}
:deep(.el-alert--error) {
  border-color: rgba(239, 68, 68, 0.3) !important;
}
:deep(.el-tag) {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
  color: #a78bfa !important;
}

/* 查询内容区域占满父容器宽度 */
.query-form-item {
  width: 100%;
}
.prompt-editor {
  width: 100%;
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 8px;
  padding: 8px;
}
.query-textarea {
  width: 100% !important;
}
.query-textarea :deep(.el-textarea__inner) {
  width: 100% !important;
  min-height: 150px;          /* 增高 */
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 6px !important;
  color: #ffffff !important;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}
.query-textarea :deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}
/* 工具栏浮动在右下角 */
.floating-toolbar {
  position: absolute;
  bottom: 14px;
  right: 16px;
  display: flex;
  gap: 6px;
  background: rgba(15, 18, 40, 0.85);
  padding: 4px 8px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
  z-index: 5;
}
</style>