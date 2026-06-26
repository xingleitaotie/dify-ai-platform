<template>
  <div class="start-config">
    <el-divider content-position="left">输入变量配置</el-divider>

    <el-form-item label="输入变量">
      <div class="input-vars-editor">
        <div v-for="(inputVar, index) in inputVariables" :key="index" class="input-var-row">
          <el-input
              v-model="inputVar.name"
              placeholder="变量名"
              size="small"
              style="width: 150px"
          />

          <!-- 自定义类型选择器（模仿 EndConfig 的 source-selector） -->
          <div class="type-selector">
            <el-dropdown trigger="click" @command="(cmd) => setType(inputVar, cmd)">
              <div class="type-trigger">
                <span v-if="inputVar.type" class="type-value">{{ getTypeLabel(inputVar.type) }}</span>
                <span v-else class="type-placeholder">选择类型</span>
                <el-icon class="type-arrow"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                      v-for="type in typeOptions"
                      :key="type.value"
                      :command="type.value"
                  >
                    {{ type.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <el-button type="danger" size="small" @click="removeInputVar(index)" :icon="Delete" />
        </div>
        <el-button size="small" type="primary" plain @click="addInputVar">
          <el-icon><Plus /></el-icon> 添加输入变量
        </el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Delete, Plus, ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true }
})

// 类型选项
const typeOptions = [
  { label: '字符串', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔值', value: 'boolean' },
  { label: '数组', value: 'array' },
  { label: '对象', value: 'object' }
]

// 获取类型显示名称
const getTypeLabel = (type) => {
  const found = typeOptions.find(t => t.value === type)
  return found ? found.label : type
}

// 双向绑定 inputVariables
const inputVariables = computed({
  get() {
    if (!props.config.inputVariables) {
      props.config.inputVariables = []
    }
    return props.config.inputVariables
  },
  set(val) {
    props.config.inputVariables = val
  }
})

const addInputVar = () => {
  inputVariables.value = [
    ...inputVariables.value,
    { name: '', type: 'string' }  // 默认类型设为 string
  ]
}

const removeInputVar = (index) => {
  const newVars = [...inputVariables.value]
  newVars.splice(index, 1)
  inputVariables.value = newVars
}

// 设置类型
const setType = (inputVar, type) => {
  inputVar.type = type
}
</script>

<style scoped>
.start-config {
  padding: 4px 0;
}

.input-vars-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.input-var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #0f1228;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #2a2f4a;
}

/* ===== 类型选择器（模仿 EndConfig 的 source-selector） ===== */
.type-selector {
  flex: 1;
  min-width: 120px;
}

.type-trigger {
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
}

.type-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}

.type-value {
  color: #a78bfa;
  font-size: 13px;
}

.type-placeholder {
  color: #64748b;
  font-size: 13px;
}

.type-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}

.type-trigger:hover .type-arrow {
  color: #a78bfa;
}

/* ===== 下拉菜单样式（与 EndConfig 保持一致，已覆盖全局） ===== */
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

/* ===== 输入框深色覆盖（与 EndConfig 一致） ===== */
.input-var-row :deep(.el-input .el-input__wrapper) {
  background: #0f1228 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 6px !important;
  box-shadow: none !important;
}

.input-var-row :deep(.el-input .el-input__wrapper:hover) {
  border-color: #667eea !important;
}

.input-var-row :deep(.el-input .el-input__wrapper.is-focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

.input-var-row :deep(.el-input .el-input__inner) {
  color: #ffffff !important;
}

.input-var-row :deep(.el-input .el-input__inner::placeholder) {
  color: #64748b !important;
}

/* ===== 按钮样式 ===== */
.input-var-row :deep(.el-button--danger) {
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}

.input-var-row :deep(.el-button--danger:hover) {
  background: rgba(239, 68, 68, 0.25) !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
}

:deep(.el-button--primary.is-plain) {
  background: rgba(102, 126, 234, 0.1) !important;
  border: 1px dashed rgba(102, 126, 234, 0.4) !important;
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
</style>
