<template>
  <div class="rag-config">
    <el-divider content-position="left">基础配置</el-divider>

    <el-form-item label="查询内容" prop="query">
      <div class="prompt-editor">
        <el-input
            v-model="localConfig.query"
            type="textarea"
            :rows="4"
            placeholder="请输入查询内容，支持变量，如：{{input.query}}"
        />
        <div class="variable-toolbar">
          <el-dropdown @command="insertQueryVariable">
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
          <el-button size="small" @click="localConfig.query = ''" plain>清空</el-button>
        </div>
      </div>
    </el-form-item>

    <el-divider content-position="left">检索配置</el-divider>

    <el-form-item label="知识库">
      <el-select
          v-model="localConfig.kbName"
          placeholder="请选择知识库"
          filterable
          clearable
          :loading="loadingKbList"
          @focus="loadKnowledgeBaseList"
      >
        <el-option
            v-for="kb in knowledgeBaseList"
            :key="kb.id || kb.name"
            :label="kb.name || kb.kbName"
            :value="kb.name || kb.kbName"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="返回文档数量">
      <el-slider v-model="localConfig.topK" :min="1" :max="20" :step="1" />
      <div class="slider-value">返回 {{ localConfig.topK || 5 }} 个相关文档</div>
    </el-form-item>

    <el-form-item label="相似度阈值">
      <el-slider v-model="localConfig.threshold" :min="0" :max="1" :step="0.05" />
      <div class="slider-value">阈值: {{ (localConfig.threshold || 0) * 100 }}%</div>
    </el-form-item>

    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="localConfig.outputVar" placeholder="例如: rag_documents" />
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
import { ref, reactive, watch, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, VideoPlay } from '@element-plus/icons-vue'
import { ragApi } from '@/api'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})

const emit = defineEmits(['update'])

// 注入输入变量列表
const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

// 节点输出变量（过滤掉自身）
const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

const localConfig = reactive(props.config)
const outputVarDisplay = computed(() => {
  const varName = localConfig.outputVar || 'rag_documents'
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

const insertQueryVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.query = (localConfig.query || '') + variable
}

const testRagNode = async () => {
  if (!localConfig.query) {
    ElMessage.warning('请输入查询内容')
    return
  }
  testing.value = true
  try {
    const res = await ragApi.searchDocument({
      kb: localConfig.kbName,
      query: localConfig.query,
      topK: localConfig.topK || 5
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

watch(localConfig, (newVal) => {
  emit('update', newVal)
}, { deep: true })
</script>

<style scoped>
/* 变量显示样式（与 END 节点保持一致） */
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
.variable-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.form-tip {
  font-size: 12px;
  color: #8b8fa9;
  margin-top: 4px;
}
.slider-value {
  font-size: 12px;
  color: #8b8fa9;
  margin-top: 4px;
}
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
</style>