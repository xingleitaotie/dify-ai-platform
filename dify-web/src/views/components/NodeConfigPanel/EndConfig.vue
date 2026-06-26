<template>
  <div class="end-config">
    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量">
      <div class="output-vars-editor">
        <div v-for="(outputVar, index) in outputVariables" :key="index" class="output-var-row">
          <!-- 变量名 -->
          <el-input
              v-model="outputVar.name"
              placeholder="变量名"
              size="small"
              class="var-name-input"
          />

          <!-- 值来源区域 -->
          <div class="source-selector">
            <!-- 模式切换 -->
            <div class="source-mode-toggle">
              <el-radio-group v-model="outputVar.mode" size="small">
                <el-radio-button label="variable">引用变量</el-radio-button>
                <el-radio-button label="text">自定义文本</el-radio-button>
              </el-radio-group>
            </div>

            <!-- 引用变量模式 -->
            <template v-if="outputVar.mode === 'variable'">
              <el-dropdown trigger="click" @command="(cmd) => setOutputVarSource(outputVar, cmd)">
                <div class="source-trigger">
                  <span v-if="outputVar.source" class="source-value">{{ outputVar.source }}</span>
                  <span v-else class="source-placeholder">选择来源变量</span>
                  <el-icon class="source-arrow"><ArrowDown /></el-icon>
                </div>
                <template #dropdown>
                  <el-dropdown-menu>
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

                    <el-dropdown-item divided>
                      <strong>📤 节点输出变量</strong>
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
            </template>

            <!-- 自定义文本模式 -->
            <template v-else>
              <el-input
                  v-model="outputVar.customText"
                  type="textarea"
                  :rows="1"
                  placeholder="请输入要输出的文本内容..."
                  class="custom-text-input"
              />
            </template>
          </div>

          <!-- 删除按钮 -->
          <el-button type="danger" size="small" @click="removeOutputVar(index)" :icon="Delete" class="del-btn" />
        </div>

        <el-button size="small" type="primary" plain @click="addOutputVar">
          <el-icon><Plus /></el-icon> 添加输出变量
        </el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup>
// 脚本部分完全保持不变，与之前一致
import { computed, inject, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})

const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

const nodeOutputVars = computed(() => props.nodeOutputVars || [])

const outputVariables = computed({
  get() {
    if (!props.config.outputVariables) {
      props.config.outputVariables = []
    }
    props.config.outputVariables.forEach(item => {
      if (item.mode === undefined) item.mode = 'variable'
      if (item.customText === undefined) item.customText = ''
    })
    return props.config.outputVariables
  },
  set(val) {
    props.config.outputVariables = val
  }
})

const addOutputVar = () => {
  outputVariables.value = [
    ...outputVariables.value,
    { name: '', source: '', mode: 'variable', customText: '' }
  ]
}

const removeOutputVar = (index) => {
  const newVars = [...outputVariables.value]
  newVars.splice(index, 1)
  outputVariables.value = newVars
}

const setOutputVarSource = (outputVar, source) => {
  outputVar.source = source
  if (!outputVar.name && source) {
    const parts = source.split('.')
    outputVar.name = parts[parts.length - 1]
  }
  ElMessage.success(`已设置来源: ${source}`)
}
</script>

<style scoped>
/* ===== 整体布局 ===== */
.output-vars-editor {
  width: 100%;
}

.output-var-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  background: #0f1228;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #2a2f4a;
}

/* ===== 变量名输入框 ===== */
.var-name-input {
  width: 120px;
  flex-shrink: 0;
}
.var-name-input :deep(.el-input__wrapper) {
  padding: 0 10px;
  min-height: 30px;
  height: 30px;
  border-radius: 6px;
}
.var-name-input :deep(.el-input__inner) {
  font-size: 12px;
  height: 30px;
  line-height: 30px;
}

/* ===== 来源选择区域（水平布局） ===== */
.source-selector {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

/* ---- 模式切换 ---- */
.source-mode-toggle {
  flex-shrink: 0;
}
.source-mode-toggle :deep(.el-radio-group) {
  background: #0f1228;
  border-radius: 6px;
  border: 1px solid #2a2f4a;
  padding: 2px;
}
.source-mode-toggle :deep(.el-radio-button) {
  --el-radio-button-bg-color: transparent;
}
.source-mode-toggle :deep(.el-radio-button .el-radio-button__inner) {
  background: transparent;
  border: none;
  color: #94a3b8;
  font-size: 12px;
  padding: 2px 12px;
  height: 24px;
  line-height: 24px;
}
.source-mode-toggle :deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: #667eea;
  color: #ffffff;
  border-radius: 4px;
}
.source-mode-toggle :deep(.el-radio-button:hover .el-radio-button__inner) {
  color: #a78bfa;
}

/* ---- 引用变量触发器 ---- */
.source-trigger {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 12px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  min-height: 30px;
}
.source-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.source-value {
  color: #a78bfa;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
}
.source-placeholder {
  color: #64748b;
  font-size: 12px;
}
.source-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.source-trigger:hover .source-arrow {
  color: #a78bfa;
}

/* ---- 自定义文本输入 ---- */
.custom-text-input {
  flex: 1;
}
.custom-text-input :deep(.el-textarea__inner) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 6px !important;
  color: #ffffff !important;
  font-size: 12px;
  line-height: 1.4;
  resize: vertical;
  min-height: 30px;
  padding: 4px 10px;
}
.custom-text-input :deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

/* ===== 删除按钮 ===== */
.del-btn {
  flex-shrink: 0;
  padding: 4px 8px !important;
  min-height: 30px;
  height: 30px;
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}
.del-btn:hover {
  background: rgba(239, 68, 68, 0.25) !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
}

/* ===== 变量代码样式 ===== */
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

/* ===== 添加按钮 ===== */
:deep(.el-button--primary.is-plain) {
  background: rgba(102, 126, 234, 0.1) !important;
  border: 1px dashed rgba(102, 126, 234, 0.4) !important;
  color: #a78bfa !important;
  font-size: 12px;
  padding: 5px 14px;
}
:deep(.el-button--primary.is-plain:hover) {
  background: rgba(102, 126, 234, 0.2) !important;
  border-color: #667eea !important;
  color: #c4b5fd !important;
}

/* ===== 分割线 ===== */
:deep(.el-divider) {
  border-color: #2a2f4a !important;
}
:deep(.el-divider__text) {
  color: #94a3b8 !important;
  font-weight: 500;
  font-size: 13px;
}

/* ===== 下拉菜单深色覆盖 ===== */
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
</style>