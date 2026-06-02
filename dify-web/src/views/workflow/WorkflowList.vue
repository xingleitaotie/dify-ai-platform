<template>
  <div class="workflow-list">
    <div class="page-header">
      <h2>工作流管理</h2>
      <el-button type="primary" @click="createNewWorkflow">
        <el-icon><Plus /></el-icon>
        创建工作流
      </el-button>
    </div>

    <div class="table-wrapper">
      <el-table :data="workflowList" v-loading="loading" stripe class="workflow-table">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="工作流名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">v{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <!-- 1. 加宽操作列，改为弹性宽度 -->
        <el-table-column label="操作" min-width="260" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <!-- 编辑按钮：草稿可编辑，已发布显示"新版本" -->
              <el-button
                  size="small"
                  :type="row.status === 'PUBLISHED' ? 'info' : 'primary'"
                  @click="editWorkflow(row)"
              >
                <el-icon><Edit /></el-icon>
                {{ row.status === 'PUBLISHED' ? '新版本' : '编辑' }}
              </el-button>

              <!-- 测试按钮始终可用 -->
              <el-button size="small" type="success" plain @click="testWorkflow(row)">
                <el-icon><VideoPlay /></el-icon>
                测试
              </el-button>

              <!-- 发布按钮：只有草稿可发布 -->
              <el-button
                  v-if="row.status === 'DRAFT'"
                  size="small"
                  type="warning"
                  plain
                  @click="doPublish(row.id)"
              >
                <el-icon><Upload /></el-icon>
                发布
              </el-button>

              <!-- 归档/删除按钮 -->
              <el-dropdown trigger="click" @command="(cmd) => handleAction(cmd, row)">
                <el-button size="small">
                  更多 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <!-- 已发布的工作流可以归档 -->
                    <el-dropdown-item
                        v-if="row.status === 'PUBLISHED'"
                        command="archive"
                    >
                      <el-icon><FolderOpened /></el-icon>
                      归档
                    </el-dropdown-item>
                    <!-- 草稿可以删除 -->
                    <el-dropdown-item
                        v-if="row.status === 'DRAFT'"
                        command="delete"
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

    <!-- 测试对话框 -->
    <el-dialog
        v-model="testDialogVisible"
        title="测试工作流"
        width="600px"
        class="test-dialog"
        @close="clearTestResult"
    >
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
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="runTest" :loading="testing">
          执行
        </el-button>
      </template>

      <div v-if="testResult" class="test-result">
        <el-divider>执行结果</el-divider>
        <el-alert
            :title="testResult.status === 'SUCCESS' ? '执行成功' : '执行失败'"
            :type="testResult.status === 'SUCCESS' ? 'success' : 'error'"
            :closable="false"
        />
        <div class="result-content">
          <h4>输出结果：</h4>
          <pre>{{ JSON.stringify(testResult.output, null, 2) }}</pre>
          <h4>节点详情：</h4>
          <el-timeline>
            <el-timeline-item
                v-for="node in testResult.nodeResults"
                :key="node.nodeId"
                :type="node.status === 'SUCCESS' ? 'success' : 'danger'"
                :timestamp="'耗时: ' + node.costTime + 'ms'"
            >
              <h4>{{ node.nodeName }} ({{ node.nodeType }})</h4>
              <p>状态: {{ node.status }}</p>
              <details v-if="node.output">
                <summary>输出详情</summary>
                <pre>{{ JSON.stringify(node.output, null, 2) }}</pre>
              </details>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, VideoPlay, Upload, Delete, ArrowDown, FolderOpened  } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  getWorkflowList,
  deleteWorkflow,
  publishWorkflow,
  executeWorkflow
} from '@/api/workflow'

const router = useRouter()
const loading = ref(false)
const workflowList = ref([])
const testDialogVisible = ref(false)
const testing = ref(false)
const testResult = ref(null)
const currentWorkflow = ref(null)

// 归档工作流
const archiveWorkflow = (id) => {
  ElMessageBox.confirm('归档后工作流将无法执行，确定归档吗？', '确认归档', {
    confirmButtonText: '归档',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      // 注意：这里你原来的代码有个笔误，archiveWorkflow 函数名和变量名冲突了，这里给你修正为 api 调用
      const res = await apiArchiveWorkflow(id)  // 请替换为你实际的接口调用
      if (res.code === 200 && res.data) {
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

// 修改 handleAction 函数
const handleAction = (command, row) => {
  switch (command) {
    case 'test':
      testWorkflow(row)
      break
    case 'publish':
      doPublish(row.id)
      break
    case 'delete':
      doDelete(row.id)
      break
    case 'archive':
      archiveWorkflow(row.id)
      break
  }
}

const testForm = ref({
  sessionId: '',
  inputsJson: '{"query": "测试查询"}'
})

// 创建工作流
const createNewWorkflow = () => {
  router.push('/workflow/editor')
}

// 修改编辑逻辑：已发布的工作流创建新版本
const editWorkflow = (row) => {
  if (row.status === 'PUBLISHED') {
    // 已发布的工作流：创建新版本
    router.push(`/workflow/editor/new?sourceId=${row.id}`)
  } else {
    // 草稿：直接编辑
    router.push(`/workflow/editor/${row.id}`)
  }
}

// 加载工作流列表
const loadWorkflowList = async () => {
  loading.value = true
  try {
    const res = await getWorkflowList()
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
      const res = await deleteWorkflow(id)
      if (res.code === 200 && res.data) {
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
      const res = await publishWorkflow(id)
      if (res.code === 200 && res.data) {
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

    const res = await executeWorkflow({
      workflowId: currentWorkflow.value.id,
      sessionId: testForm.value.sessionId || `session_${Date.now()}`,
      inputs: inputs
    })

    if (res.code === 200) {
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

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleString()
}

onMounted(() => {
  loadWorkflowList()
})
</script>

<style scoped>
.workflow-list {
  padding: 20px;
  min-height: 100%;
  background: #0a0e27;
}

/* ========== 页面头部 ========== */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
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

/* ========== 表格滚动容器 ========== */
.table-wrapper {
  overflow-x: auto;
  border-radius: 16px;
}

/* ========== 表格样式 ========== */
.workflow-table {
  min-width: 1000px;
  background: transparent !important;
}

.workflow-table :deep(.el-table) {
  background: transparent !important;
  --el-table-bg-color: transparent !important;
}

.workflow-table :deep(.el-table__header) {
  background: transparent !important;
}

.workflow-table :deep(.el-table__header th) {
  background: #0f1228 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 13px !important;
  padding: 14px 0 !important;
  border-bottom: 2px solid #2a2f4a !important;
}

.workflow-table :deep(.el-table__header th .cell) {
  color: #ffffff !important;
}

.workflow-table :deep(.el-table__body tr) {
  background: transparent !important;
}

.workflow-table :deep(.el-table__body td) {
  color: #cbd5e6 !important;
  border-bottom: 1px solid #2a2f4a !important;
  padding: 12px 0 !important;
  font-size: 13px !important;
}

.workflow-table :deep(.el-table__body tr:hover > td) {
  background: rgba(102, 126, 234, 0.06) !important;
}

.workflow-table :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: rgba(255, 255, 255, 0.02) !important;
}

/* ========== 标签样式 ========== */
.workflow-table :deep(.el-tag--success) {
  background: rgba(16, 185, 129, 0.15) !important;
  border: 1px solid rgba(16, 185, 129, 0.3) !important;
  color: #34d399 !important;
}

.workflow-table :deep(.el-tag--info) {
  background: rgba(100, 116, 139, 0.15) !important;
  border: 1px solid rgba(100, 116, 139, 0.3) !important;
  color: #94a3b8 !important;
}

.workflow-table :deep(.el-tag--warning) {
  background: rgba(245, 158, 11, 0.15) !important;
  border: 1px solid rgba(245, 158, 11, 0.3) !important;
  color: #fbbf24 !important;
}

.workflow-table :deep(.el-tag--danger) {
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}

/* ========== 操作按钮组（核心优化） ========== */
.action-buttons {
  display: flex;
  gap: 6px; /* 缩小按钮间距 */
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
}

.action-buttons .el-button {
  border-radius: 8px !important;
  padding: 4px 8px !important; /* 缩小按钮内边距 */
  font-size: 12px !important;
  transition: all 0.2s ease;
  white-space: nowrap; /* 禁止按钮文字换行 */
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
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

.action-buttons .el-button--danger {
  background: linear-gradient(135deg, #ef4444, #dc2626) !important;
  border: none !important;
}

.action-buttons .el-dropdown .el-button {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.action-buttons .el-dropdown .el-button:hover {
  background: #3a3f5a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
}

/* ========== 测试对话框样式 ========== */
.test-dialog :deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 20px !important;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.test-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 20px 24px;
  margin: 0;
}

.test-dialog :deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
  font-size: 18px;
}

.test-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #94a3b8 !important;
}

.test-dialog :deep(.el-dialog__headerbtn .el-dialog__close:hover) {
  color: #f87171 !important;
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
  font-weight: 500;
}

.test-dialog :deep(.el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 10px;
  box-shadow: none !important;
}

.test-dialog :deep(.el-input__wrapper:hover) {
  border-color: #667eea !important;
}

.test-dialog :deep(.el-input__inner) {
  color: #ffffff !important;
}

.test-dialog :deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  color: #ffffff !important;
  border-radius: 10px;
}

.test-dialog :deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
}

.test-dialog :deep(.el-divider) {
  background-color: #2a2f4a !important;
}

.test-dialog :deep(.el-divider__text) {
  background-color: #1a1f3a;
  color: #cbd5e6;
}

/* 测试结果区域 */
.test-result {
  margin-top: 20px;
  background: #0f1228;
  border-radius: 12px;
  padding: 16px;
}

.result-content {
  max-height: 400px;
  overflow-y: auto;
  margin-top: 15px;
}

.result-content::-webkit-scrollbar {
  width: 6px;
}

.result-content::-webkit-scrollbar-track {
  background: #0f1228;
  border-radius: 3px;
}

.result-content::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 3px;
}

.result-content h4 {
  margin: 12px 0 8px 0;
  color: #ffffff;
  font-weight: 600;
}

.result-content pre {
  background: #1a1f3a;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  color: #cbd5e6;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  border: 1px solid #2a2f4a;
}

/* 时间线样式 */
.result-content :deep(.el-timeline-item__timestamp) {
  color: #94a3b8 !important;
}

.result-content :deep(.el-timeline-item__content) {
  color: #cbd5e6 !important;
}

.result-content details summary {
  color: #a78bfa;
  cursor: pointer;
  margin: 8px 0;
}

.result-content details pre {
  margin-top: 8px;
}

/* ========== 对话框底部按钮 ========== */
.test-dialog :deep(.el-dialog__footer .el-button--default) {
  background: #2a2f4a !important;
  border: 1px solid #3a3f5a !important;
  color: #cbd5e6 !important;
}

.test-dialog :deep(.el-dialog__footer .el-button--default:hover) {
  background: #3a3f5a !important;
  border-color: #667eea !important;
  color: #ffffff !important;
}

.test-dialog :deep(.el-dialog__footer .el-button--primary) {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== 空状态样式 ========== */
.workflow-table :deep(.el-table__empty-text) {
  color: #94a3b8 !important;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .workflow-list {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .page-header .el-button {
    width: 100%;
  }

  .action-buttons {
    flex-direction: column;
    gap: 6px;
  }

  .action-buttons .el-button {
    width: 100%;
  }

  .test-dialog :deep(.el-dialog) {
    width: 95% !important;
    margin: 20px auto !important;
  }
}

@media screen and (max-width: 1200px) {
  .table-wrapper {
    overflow-x: auto;
  }

  .workflow-table {
    min-width: 1000px;
  }
}
</style>