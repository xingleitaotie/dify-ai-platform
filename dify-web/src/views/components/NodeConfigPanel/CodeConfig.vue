<template>
  <div class="code-config">
    <!-- 语言选择 -->
    <el-form-item label="语言">
      <el-select v-model="language" placeholder="选择语言">
        <el-option label="JavaScript" value="js" />
        <el-option label="Python" value="python" disabled />
      </el-select>
      <div class="form-tip">当前仅支持 JavaScript</div>
    </el-form-item>

    <!-- 输入变量列表 -->
    <el-form-item label="输入变量">
      <div v-for="(input, index) in inputs" :key="index" class="var-row">
        <el-input
            v-model="input.name"
            placeholder="变量名"
            size="small"
            style="width: 140px"
        />
        <div class="input-value-wrapper">
          <el-input
              v-model="input.value"
              placeholder="变量值/引用"
              size="small"
              style="flex: 1"
          />
          <el-dropdown @command="(cmd) => insertInputValue(index, cmd)">
            <el-button size="small" plain>
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
        <el-button type="danger" size="small" @click="removeInput(index)">删除</el-button>
      </div>
      <el-button size="small" @click="addInput">添加输入</el-button>
      <div class="form-tip">变量值支持引用 {{ '{' }}{{ '{' }}input.xxx{{ '}' }}{{ '}' }} 或 {{ '{' }}{{ '{' }}var.xxx{{ '}' }}{{ '}' }}</div>
    </el-form-item>

    <!-- 代码编辑区 -->
    <el-form-item label="代码" prop="code">
      <div class="code-toolbar">
        <el-button size="small" @click="resetCodeTemplate">重置模板</el-button>
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

// 默认代码模板
const DEFAULT_CODE_TEMPLATE = `function main(params) {
    // 在此编写您的核心逻辑
    const arr = params.query || [];
    const result = arr.join('，');
    return { output: result };
}`

// ========== 双向 computed，直接操作 props.config ==========
const language = computed({
  get: () => props.config.language || 'js',
  set: (val) => { props.config.language = val }
})

const code = computed({
  get: () => props.config.code || DEFAULT_CODE_TEMPLATE,
  set: (val) => { props.config.code = val }
})

const outputVar = computed({
  get: () => props.config.outputVar || 'code_result',
  set: (val) => { props.config.outputVar = val }
})

const inputs = computed({
  get: () => props.config.inputs || [],
  set: (val) => { props.config.inputs = val }
})

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

.var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.input-value-wrapper {
  display: flex;
  flex: 1;
  gap: 4px;
  align-items: center;
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
  background: #0a0e27;
  color: #e2e8f0;
}

.form-tip code {
  background: #1a1f3a;
  padding: 1px 6px;
  border-radius: 4px;
  color: #a78bfa;
  font-size: 12px;
}
</style>