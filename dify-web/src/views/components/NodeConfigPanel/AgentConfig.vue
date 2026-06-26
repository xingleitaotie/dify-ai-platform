<template>
  <div class="agent-config">
    <el-divider content-position="left">基础配置</el-divider>

    <!-- Agent 选择：自定义下拉（仅样式改动，逻辑不变） -->
    <el-form-item label="Agent" prop="agentId">
      <div class="agent-selector">
        <el-dropdown trigger="click" @command="selectAgent">
          <div class="agent-trigger">
            <span v-if="selectedAgentLabel" class="agent-value">{{ selectedAgentLabel }}</span>
            <span v-else class="agent-placeholder">请选择 Agent</span>
            <el-icon class="agent-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                  v-for="agent in agentList"
                  :key="agent.id"
                  :command="agent.id"
              >
                {{ agent.agentName || agent.name }}
              </el-dropdown-item>
              <el-dropdown-item v-if="agentList.length === 0" disabled>
                暂无 Agent
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button size="small" plain @click="loadAgentList" class="refresh-btn">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </el-form-item>

    <!-- 查询内容：浮动工具栏（仅样式改动，逻辑不变） -->
    <el-form-item label="查询内容" prop="query" class="query-form-item">
      <div class="prompt-editor">
        <el-input
            v-model="query"
            type="textarea"
            :rows="6"
            placeholder="输入数据，支持变量引用，如：{{input.query}}"
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

    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="outputVar" placeholder="例如: agent_result" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { ref, computed, inject } from 'vue'
import { ArrowDown, Refresh } from '@element-plus/icons-vue'
import { agentApi } from '@/api'

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

// 双向绑定，直接操作父组件草稿
const agentId = computed({
  get: () => props.config.agentId ?? null,
  set: (val) => { props.config.agentId = val }
})

const query = computed({
  get: () => props.config.query || '',
  set: (val) => { props.config.query = val }
})

const outputVar = computed({
  get: () => props.config.outputVar || 'agent_result',
  set: (val) => { props.config.outputVar = val }
})

const outputVarDisplay = computed(() => {
  const varName = outputVar.value
  return `{{var.${varName}}}`
})

const agentList = ref([])
const agentLoading = ref(false)

// 新增：计算当前选中的 Agent 显示名称（仅用于展示，不影响原有逻辑）
const selectedAgentLabel = computed(() => {
  if (!agentId.value) return ''
  const found = agentList.value.find(a => a.id === agentId.value)
  return found ? (found.agentName || found.name) : ''
})

const insertQueryVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  query.value = query.value + variable
}

// 新增：选择 Agent（仅封装，逻辑不变）
const selectAgent = (id) => {
  agentId.value = id
}

const loadAgentList = async () => {
  agentLoading.value = true
  try {
    const res = await agentApi.list()
    if (res.code === 200) agentList.value = res.data || []
  } catch (error) {
    console.error('加载Agent列表失败:', error)
  } finally {
    agentLoading.value = false
  }
}

loadAgentList()
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

/* ===== Agent 选择器 ===== */
.agent-selector {
  display: flex;
  gap: 8px;
  align-items: center;
}
.agent-trigger {
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
.agent-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.agent-value {
  color: #a78bfa;
}
.agent-placeholder {
  color: #64748b;
}
.agent-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.agent-trigger:hover .agent-arrow {
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

/* ===== 查询内容区域 ===== */
.query-form-item {
  width: 100%;
}
.prompt-editor {
  position: relative;
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
  min-height: 150px;
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

/* ===== 全局深色覆盖 ===== */
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

:deep(.el-divider) {
  border-color: #2a2f4a !important;
}
:deep(.el-divider__text) {
  color: #94a3b8 !important;
  font-weight: 500;
  font-size: 13px;
}
</style>