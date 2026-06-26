<template>
  <div class="code-config">
    <!-- 语言选择：改为自定义下拉 -->
    <el-form-item label="语言">
      <div class="language-selector">
        <el-dropdown trigger="click" @command="setLanguage">
          <div class="language-trigger">
            <span class="language-value">{{ getLanguageLabel(language) }}</span>
            <el-icon class="language-arrow"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                  v-for="lang in languageOptions"
                  :key="lang.value"
                  :command="lang.value"
                  :disabled="lang.disabled"
              >
                {{ lang.label }}
                <span v-if="lang.disabled" style="color: #64748b; font-size: 12px; margin-left: 8px;">（暂不支持）</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="form-tip">当前仅支持 JavaScript</div>
    </el-form-item>

    <!-- 输入变量列表 -->
    <el-form-item label="输入变量">
      <div v-for="(input, index) in inputs" :key="index" class="var-row">
        <el-input
            v-model="input.name"
            placeholder="变量名"
            size="small"
            class="var-name-input"
        />
        <div class="input-value-wrapper">
          <el-input
              v-model="input.value"
              placeholder="变量值/引用"
              size="small"
              class="var-value-input"
          />
          <el-dropdown @command="(cmd) => insertInputValue(index, cmd)">
            <el-button size="small" plain class="insert-btn">
              插入变量 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 输入变量分组 -->
                <el-dropdown-item divided>
                  <strong>📥 输入变量</strong>
                </el-dropdown-item>
                <el-dropdown-item
                    v-for="item in inputVarList"
                    :key="`input.${item.name}`"
                    :command="`input.${item.name}`"
                >
                  <span class="var-code">{{ '{' }}{{ '{' }}input.{{ item.name }}{{ '}' }}{{ '}' }}</span>
                  <span class="var-desc"> - {{ item.description || item.name }}</span>
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
        </div>
        <el-button type="danger" size="small" @click="removeInput(index)" class="del-btn">删除</el-button>
      </div>
      <el-button size="small" @click="addInput">添加输入</el-button>
      <div class="form-tip">变量值支持引用 {{ '{' }}{{ '{' }}input.xxx{{ '}' }}{{ '}' }} 或 {{ '{' }}{{ '{' }}var.xxx{{ '}' }}{{ '}' }}</div>
    </el-form-item>

    <!-- 代码编辑区 -->
    <el-form-item label="代码" prop="code">
      <div class="code-toolbar">
        <el-button size="small" @click="resetCodeTemplate">样例代码</el-button>
        <el-button size="small" @click="formatCode" plain>格式化</el-button>
      </div>
      <el-input
          v-model="code"
          type="textarea"
          :rows="12"
          class="code-editor"
          placeholder="// 在 main 函数中编写核心逻辑"
      />
      <div class="form-tip">
        <strong>使用说明：</strong>
        <br>1. 入参通过 <code>params.xxx</code> 访问（如 <code>params.query</code>）
        <br>2. 必须返回一个对象，如 <code>return { output: result }</code>
        <br>3. 支持 JavaScript ES6+ 语法
      </div>
    </el-form-item>

    <!-- 输出配置 -->
    <el-divider content-position="left">输出配置</el-divider>
    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="outputVar" placeholder="例如: code_result" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

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

// 语言选项
const languageOptions = [
  { label: 'JavaScript', value: 'js', disabled: false },
  { label: 'Python', value: 'python', disabled: true }
]

// 默认代码模板
const DEFAULT_CODE_TEMPLATE = `function main(params) {
    // 在此编写您的核心逻辑
    const arr = params.query || [];
    const result = arr.join('，');
    return { output: result };
}`

// ========== 双向 computed，自动初始化默认值 ==========
const language = computed({
  get: () => {
    if (!props.config.language) {
      props.config.language = 'js'
    }
    return props.config.language
  },
  set: (val) => {
    props.config.language = val
  }
})

const code = computed({
  get: () => {
    if (!props.config.code) {
      // 关键：首次访问即写入默认模板
      props.config.code = DEFAULT_CODE_TEMPLATE
    }
    return props.config.code
  },
  set: (val) => {
    props.config.code = val
  }
})

const outputVar = computed({
  get: () => {
    if (!props.config.outputVar) {
      props.config.outputVar = 'code_result'
    }
    return props.config.outputVar
  },
  set: (val) => {
    props.config.outputVar = val
  }
})

const inputs = computed({
  get: () => {
    if (!props.config.inputs) {
      props.config.inputs = []
    }
    return props.config.inputs
  },
  set: (val) => {
    props.config.inputs = val
  }
})

// 获取语言显示名称
const getLanguageLabel = (value) => {
  const found = languageOptions.find(l => l.value === value)
  return found ? found.label : value
}

// 设置语言
const setLanguage = (value) => {
  language.value = value
}

// 输出变量显示
const outputVarDisplay = computed(() => {
  const varName = outputVar.value
  return `{{var.${varName}}}`
})

// 重置模板
const resetCodeTemplate = () => {
  code.value = DEFAULT_CODE_TEMPLATE
  ElMessage.success('已恢复默认模板')
}

// 格式化代码
const formatCode = () => {
  if (!code.value) return
  const lines = code.value.split('\n')
  const formatted = lines
      .map(line => line.trimEnd())
      .join('\n')
  code.value = formatted
  ElMessage.success('格式化完成')
}

// 添加输入
const addInput = () => {
  inputs.value = [...inputs.value, { name: '', value: '' }]
}

// 删除输入
const removeInput = (index) => {
  const newInputs = [...inputs.value]
  newInputs.splice(index, 1)
  inputs.value = newInputs
}

// 插入变量
const insertInputValue = (index, varPath) => {
  const variable = `{{${varPath}}}`
  const newInputs = inputs.value.map((item, i) => {
    if (i === index) {
      return { ...item, value: (item.value || '') + variable }
    }
    return item
  })
  inputs.value = newInputs
}
</script>

<style scoped>
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

.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  background: #0f1228;
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid #2a2f4a;
}
.var-name-input {
  flex: 0 0 30%;
  min-width: 100px;
}
.var-value-input {
  flex: 1;
}
.input-value-wrapper {
  display: flex;
  flex: 1;
  gap: 4px;
  align-items: center;
}
.insert-btn {
  padding: 5px 10px !important;
  font-size: 12px !important;
  background: rgba(102, 126, 234, 0.08) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
  color: #a78bfa !important;
  white-space: nowrap;
}
.insert-btn:hover {
  background: rgba(102, 126, 234, 0.2) !important;
  border-color: #667eea !important;
}
.del-btn {
  flex-shrink: 0;
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}
.del-btn:hover {
  background: rgba(239, 68, 68, 0.25) !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
}

.code-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.code-editor :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #0a0e27 !important;
  color: #e2e8f0 !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 8px !important;
}
.code-editor :deep(.el-textarea__inner:focus) {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

/* 语言自定义下拉 */
.language-selector {
  display: inline-block;
}
.language-trigger {
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
  min-width: 140px;
  font-size: 13px;
}
.language-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.language-value {
  color: #a78bfa;
}
.language-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.language-trigger:hover .language-arrow {
  color: #a78bfa;
}

/* 深色组件统一覆盖 */
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