<template>
  <div class="workflow-list">
    <div class="page-header">
      <div class="header-left">
        <h2>工作流管理</h2>
        <el-tag type="info" size="large" effect="plain">共 {{ workflowList.length }} 个工作流</el-tag>
      </div>
      <el-button type="primary" @click="createNewWorkflow">
        <el-icon><Plus /></el-icon>
        创建工作流
      </el-button>
    </div>

    <div class="table-wrapper">
      <el-table
          :data="workflowList"
          v-loading="loading"
          stripe
          class="workflow-table"
          :header-cell-style="{ background: '#0f1228', color: '#ffffff', fontWeight: '600' }"
      >
        <!-- 序号列 -->
        <el-table-column label="序号" width="60" align="center" type="index">
          <template #default="{ $index }">
            <span class="index-number">{{ $index + 1 }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="工作流名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="workflow-name-cell">
              <span class="workflow-name">{{ row.name || '未命名' }}</span>
              <el-tag v-if="row.status === 'PUBLISHED'" size="small" type="success" effect="dark">已发布</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="description-text">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="version" label="版本" width="70" align="center">
          <template #default="{ row }">
            <span class="version-text">v{{ row.version || 1 }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="130" align="center">
          <template #default="{ row }">
            {{ formatDateShort(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <!-- 编辑按钮 -->
              <el-tooltip :content="row.status === 'PUBLISHED' ? '创建新版本' : '编辑'" placement="top">
                <el-button
                    :type="row.status === 'PUBLISHED' ? 'info' : 'primary'"
                    size="small"
                    circle
                    @click="editWorkflow(row)"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>

              <!-- 测试按钮 -->
              <el-tooltip content="运行" placement="top">
                <el-button type="success" size="small" circle @click="testWorkflow(row)">
                  <el-icon><VideoPlay /></el-icon>
                </el-button>
              </el-tooltip>

              <!-- 发布按钮（仅草稿） -->
              <el-tooltip v-if="row.status === 'DRAFT'" content="发布" placement="top">
                <el-button type="warning" size="small" circle @click="doPublish(row.id)">
                  <el-icon><Upload /></el-icon>
                </el-button>
              </el-tooltip>

              <!-- 更多操作 -->
              <el-dropdown trigger="click" @command="(cmd) => handleAction(cmd, row)">
                <el-button size="small" circle>
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                        v-if="row.status === 'PUBLISHED'"
                        command="archive"
                    >
                      <el-icon><FolderOpened /></el-icon>
                      归档
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-if="row.status === 'DRAFT'"
                        command="delete"
                        divided
                    >
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 测试对话框（保持不变） -->
    <el-dialog
        v-model="testDialogVisible"
        title="测试工作流"
        width="600px"
        class="test-dialog"
        @close="clearTestResult"
    >
      <!-- 内容保持不变 -->
      <el-form :model="testForm" label-width="100px">
        <el-form-item label="会话ID">
          <el-input v-model="testForm.sessionId" placeholder="留空则自动生成" />
        </el-form-item>
        <el-form-item label="输入参数">
          <el-input
              v-model="testForm.inputsJson"
              type="textarea"
              :rows="6"
              placeholder='{"query": "测试内容"}'
          />
          <div class="form-tip">支持 JSON 格式，如 {"query": "你好"}</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="runTest" :loading="testing">
          <el-icon><VideoPlay /></el-icon>
          执行
        </el-button>
      </template>

      <div v-if="testResult" class="test-result">
        <el-divider>执行结果</el-divider>
        <el-alert
            :title="testResult.status === 'SUCCESS' ? '执行成功' : '执行失败'"
            :type="testResult.status === 'SUCCESS' ? 'success' : 'error'"
            :closable="false"
            show-icon
        />
        <div class="result-content">
          <div class="result-section">
            <h4>📤 输出结果</h4>
            <pre>{{ JSON.stringify(testResult.output, null, 2) }}</pre>
          </div>
          <div class="result-section">
            <h4>🔗 节点执行详情</h4>
            <div class="node-list">
              <div
                  v-for="node in testResult.nodeResults"
                  :key="node.nodeId"
                  class="node-item"
                  :class="{ 'node-success': node.status === 'SUCCESS', 'node-error': node.status !== 'SUCCESS' }"
              >
                <div class="node-header">
                  <div class="node-title">
                    <span class="node-icon">{{ getNodeIcon(node.nodeType) }}</span>
                    <span class="node-name">{{ node.nodeName }}</span>
                    <el-tag :type="node.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
                      {{ node.status === 'SUCCESS' ? '成功' : '失败' }}
                    </el-tag>
                  </div>
                  <span class="node-time">⏱️ {{ node.costTime }}ms</span>
                </div>
                <details class="node-details">
                  <summary>查看详情</summary>
                  <div class="node-output">
                    <pre>{{ JSON.stringify(node.output, null, 2) }}</pre>
                  </div>
                </details>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, VideoPlay, Upload, Delete, MoreFilled, FolderOpened } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { workflowApi } from '@/api'

const router = useRouter()
const loading = ref(false)
const workflowList = ref([])
const testDialogVisible = ref(false)
const testing = ref(false)
const testResult = ref(null)
const currentWorkflow = ref(null)

const testForm = ref({
  sessionId: '',
  inputsJson: '{"query": "测试查询"}'
})

// 创建新工作流
const createNewWorkflow = () => {
  router.push('/workflow/editor')
}

// 编辑工作流
const editWorkflow = (row) => {
  if (row.status === 'PUBLISHED') {
    router.push(`/workflow/editor/new?sourceId=${row.id}`)
  } else {
    router.push(`/workflow/editor/${row.id}`)
  }
}

// 加载工作流列表
const loadWorkflowList = async () => {
  loading.value = true
  try {
    const res = await workflowApi.getList()
    if (res.code === 200) {
      workflowList.value = res.data || []
    } else {
      ElMessage.error(res.msg || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

// 删除工作流
const doDelete = (id) => {
  ElMessageBox.confirm('确定要删除该工作流吗？', '确认删除', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await workflowApi.delete(id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadWorkflowList()
      } else {
        ElMessage.error(res.msg || '删除失败')
      }
    } catch (error) {
      ElMessage.error('删除失败: ' + error.message)
    }
  }).catch(() => {})
}

// 发布工作流
const doPublish = (id) => {
  ElMessageBox.confirm('发布后工作流将生效，确定发布吗？', '确认发布', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    try {
      const res = await workflowApi.publish(id)
      if (res.code === 200) {
        ElMessage.success('发布成功')
        loadWorkflowList()
      } else {
        ElMessage.error(res.msg || '发布失败')
      }
    } catch (error) {
      ElMessage.error('发布失败: ' + error.message)
    }
  }).catch(() => {})
}

// 归档工作流
const doArchive = (id) => {
  ElMessageBox.confirm('归档后工作流将无法执行，确定归档吗？', '确认归档', {
    confirmButtonText: '归档',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await workflowApi.archive(id)
      if (res.code === 200) {
        ElMessage.success('归档成功')
        loadWorkflowList()
      } else {
        ElMessage.error(res.msg || '归档失败')
      }
    } catch (error) {
      ElMessage.error('归档失败: ' + error.message)
    }
  }).catch(() => {})
}

// 测试工作流
const testWorkflow = (workflow) => {
  currentWorkflow.value = workflow
  testForm.value.sessionId = `test_${Date.now()}`
  testDialogVisible.value = true
  testResult.value = null
}

// 运行测试
const runTest = async () => {
  testing.value = true
  testResult.value = null

  try {
    let inputs = {}
    try {
      inputs = JSON.parse(testForm.value.inputsJson)
    } catch (e) {
      ElMessage.error('输入参数JSON格式错误')
      return
    }

    const res = await workflowApi.execute({
      workflowId: currentWorkflow.value.id,
      sessionId: testForm.value.sessionId || `session_${Date.now()}`,
      inputs: inputs
    })

    if (res.code === 200 && res.data) {
      testResult.value = res.data
      ElMessage.success('执行完成')
    } else {
      ElMessage.error(res.msg || '执行失败')
    }
  } catch (error) {
    ElMessage.error('执行失败: ' + error.message)
  } finally {
    testing.value = false
  }
}

// 清除测试结果
const clearTestResult = () => {
  testResult.value = null
  currentWorkflow.value = null
}

// 统一操作处理
const handleAction = (command, row) => {
  switch (command) {
    case 'delete':
      doDelete(row.id)
      break
    case 'archive':
      doArchive(row.id)
      break
  }
}

// 获取节点图标
const getNodeIcon = (nodeType) => {
  const icons = {
    'START': '▶️',
    'END': '🔚',
    'LLM': '🤖',
    'RAG': '📚',
    'FUNCTION': '⚙️',
    'AGENT': '👤',
    'CONDITION': '🔀',
    'CODE': '</>'
  }
  return icons[nodeType] || '📄'
}

// 获取状态类型
const getStatusType = (status) => {
  const map = {
    'DRAFT': 'info',
    'PUBLISHED': 'success',
    'ARCHIVED': 'danger'
  }
  return map[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const map = {
    'DRAFT': '草稿',
    'PUBLISHED': '已发布',
    'ARCHIVED': '已归档'
  }
  return map[status] || status
}

// 格式化日期（简短版）
const formatDateShort = (date) => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now - d

  // 今天
  if (diff < 24 * 60 * 60 * 1000 && d.getDate() === now.getDate()) {
    return `今天 ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  }
  // 昨天
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (d.getDate() === yesterday.getDate()) {
    return `昨天 ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
  }
  // 其他
  return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

// 生命周期
onMounted(() => {
  loadWorkflowList()
})
</script>

<style scoped>
.workflow-list {
  padding: 24px;
  min-height: 100%;
  background: #0a0e27;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #a5b4fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.page-header .el-tag {
  background: rgba(100, 116, 139, 0.2);
  border: 1px solid rgba(100, 116, 139, 0.3);
  color: #94a3b8;
}

.page-header .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  border-radius: 10px;
  padding: 10px 20px;
}

.page-header .el-button--primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

/* ========== 表格样式 ========== */
.table-wrapper {
  overflow-x: auto;
  border-radius: 16px;
}

.workflow-table {
  min-width: 900px;
  width: 100%;
  background: transparent !important;
}

.workflow-table :deep(.el-table) {
  background: transparent !important;
  --el-table-bg-color: transparent !important;
}

.workflow-table :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  padding: 14px 0 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.workflow-table :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
  padding: 14px 0 !important;
  font-size: 13px !important;
}

.workflow-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.05) !important;
}

/* 工作流名称单元格 */
.workflow-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.workflow-name {
  font-weight: 500;
  color: #ffffff;
}

.description-text {
  color: #94a3b8;
  font-size: 12px;
}

.version-text {
  font-family: monospace;
  color: #a78bfa;
}

/* ========== 操作按钮组 ========== */
.action-buttons {
  display: flex;
  gap: 6px;
  justify-content: center;
  align-items: center;
}

.action-buttons .el-button {
  transition: all 0.2s ease;
}

.action-buttons .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

.action-buttons .el-button--success {
  background: linear-gradient(135deg, #10b981, #059669) !important;
  border: none !important;
}

.action-buttons .el-button--warning {
  background: linear-gradient(135deg, #f59e0b, #d97706) !important;
  border: none !important;
}

.action-buttons .el-button--default {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.action-buttons .el-button:hover {
  transform: translateY(-2px);
}

/* ========== 测试对话框样式 ========== */
.test-dialog :deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
}

.test-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 20px 24px;
}

.test-dialog :deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
}

.test-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.test-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 24px;
}

.test-dialog :deep(.el-form-item__label) {
  color: #cbd5e6 !important;
}

.test-dialog :deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
}

.test-dialog :deep(.el-input__inner) {
  color: #ffffff !important;
}

.test-dialog :deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  color: #ffffff !important;
}

.form-tip {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.test-result {
  margin-top: 20px;
  background: #0f1228;
  border-radius: 12px;
  padding: 16px;
}

.result-content {
  max-height: 500px;
  overflow-y: auto;
  margin-top: 16px;
}

.result-section {
  margin-bottom: 20px;
}

.result-section h4 {
  margin: 0 0 12px 0;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
}

.result-section pre {
  background: #1a1f3a;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  color: #cbd5e6;
  font-family: 'SF Mono', 'Fira Code', monospace;
  font-size: 12px;
  border: 1px solid #2a2f4a;
  margin: 0;
}

/* 节点列表样式 */
.node-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.node-item {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  padding: 12px;
  transition: all 0.2s;
}

.node-item.node-success {
  border-left: 3px solid #10b981;
}

.node-item.node-error {
  border-left: 3px solid #ef4444;
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.node-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  font-size: 16px;
}

.node-name {
  font-weight: 600;
  font-size: 13px;
  color: #ffffff;
}

.node-time {
  font-size: 11px;
  color: #64748b;
}

.node-details {
  margin-top: 8px;
}

.node-details summary {
  font-size: 12px;
  color: #a78bfa;
  cursor: pointer;
  padding: 4px 0;
}

.node-details summary:hover {
  color: #c4b5fd;
}

.node-output {
  margin-top: 8px;
  padding: 8px;
  background: #0f1228;
  border-radius: 8px;
}

.node-output pre {
  margin: 0;
  font-size: 11px;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .workflow-list {
    padding: 16px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .page-header .el-button {
    width: 100%;
  }

  .action-buttons {
    flex-wrap: wrap;
  }

  .test-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 20px auto !important;
  }
}

/* 序号样式 */
.index-number {
  display: inline-block;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  background: rgba(102, 126, 234, 0.15);
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
  color: #a78bfa;
}
</style>