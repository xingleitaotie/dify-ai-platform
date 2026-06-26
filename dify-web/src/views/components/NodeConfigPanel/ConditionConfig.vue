<template>
  <div class="condition-config">
    <el-divider content-position="left">条件分支</el-divider>

    <div class="branches-container">
      <div
          v-for="(branch, index) in branches"
          :key="index"
          class="branch-item"
          :class="{ 'else-branch': branch.type === 'ELSE' }"
      >
        <div class="branch-header">
          <span class="branch-tag" :class="`tag-${branch.type}`">
            {{ branchLabel(branch.type) }}
          </span>
          <el-button
              v-if="branch.type !== 'ELSE'"
              type="danger"
              size="small"
              plain
              @click="removeBranch(index)"
          >
            删除
          </el-button>
        </div>

        <!-- 条件配置（ELSE 无配置） -->
        <template v-if="branch.type !== 'ELSE'">
          <div class="condition-row">
            <!-- 变量 -->
            <div class="condition-item">
              <label>变量</label>
              <div class="input-with-dropdown">
                <el-input
                    v-model="branch.variable"
                    placeholder="选择或输入变量"
                    size="small"
                    clearable
                />
                <el-dropdown @command="(cmd) => insertToField('variable', index, cmd)">
                  <el-button size="small" plain>
                    <el-icon><ArrowDown /></el-icon>
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
                      <el-dropdown-item divided><strong>📤 节点输出变量</strong></el-dropdown-item>
                      <el-dropdown-item
                          v-for="v in nodeOutputVars"
                          :key="v.nodeId"
                          :command="`var.${v.outputVar}`"
                      >
                        <span class="var-code">{{ '{' }}{{ '{' }}var.{{ v.outputVar }}{{ '}' }}{{ '}' }}</span>
                        <span class="var-desc"> - {{ v.nodeName }}（{{ v.nodeType }}）</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <!-- 运算符：改为自定义下拉 -->
            <div class="condition-item">
              <label>运算符</label>
              <div class="operator-selector">
                <el-dropdown trigger="click" @command="(cmd) => setOperator(index, cmd)">
                  <div class="operator-trigger">
                    <span v-if="branch.operator" class="operator-value">{{ getOperatorLabel(branch.operator) }}</span>
                    <span v-else class="operator-placeholder">选择运算符</span>
                    <el-icon class="operator-arrow"><ArrowDown /></el-icon>
                  </div>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                          v-for="op in operatorOptions"
                          :key="op.value"
                          :command="op.value"
                      >
                        {{ op.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <!-- 比较值（仅非 empty/not_empty） -->
            <div class="condition-item" v-if="!['empty', 'not_empty'].includes(branch.operator)">
              <label>比较值</label>
              <div class="input-with-dropdown">
                <el-input
                    v-model="branch.value"
                    placeholder="值"
                    size="small"
                />
                <el-dropdown @command="(cmd) => insertToField('value', index, cmd)">
                  <el-button size="small" plain>
                    <el-icon><ArrowDown /></el-icon>
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
                      <el-dropdown-item divided><strong>📤 节点输出变量</strong></el-dropdown-item>
                      <el-dropdown-item
                          v-for="v in nodeOutputVars"
                          :key="v.nodeId"
                          :command="`var.${v.outputVar}`"
                      >
                        <span class="var-code">{{ '{' }}{{ '{' }}var.{{ v.outputVar }}{{ '}' }}{{ '}' }}</span>
                        <span class="var-desc"> - {{ v.nodeName }}（{{ v.nodeType }}）</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="add-branch-area">
        <el-button size="small" @click="addBranch('IF')">添加条件 (IF)</el-button>
        <el-button size="small" @click="addBranch('ELSE_IF')">添加否则如果 (ELSE IF)</el-button>
        <el-tooltip content="ELSE 已存在" :disabled="!hasElse">
          <el-button size="small" :disabled="hasElse" @click="addBranch('ELSE')">添加否则 (ELSE)</el-button>
        </el-tooltip>
      </div>
    </div>

    <div class="form-tip">
      条件节点按顺序匹配分支，命中后执行对应连线。
    </div>
  </div>
</template>

<script setup>
import { computed, inject, ref, onMounted } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})

const inputVarList = inject('inputVarList', ref([]))

const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

// 运算符选项
const operatorOptions = [
  { label: '等于', value: '==' },
  { label: '不等于', value: '!=' },
  { label: '大于', value: '>' },
  { label: '大于等于', value: '>=' },
  { label: '小于', value: '<' },
  { label: '小于等于', value: '<=' },
  { label: '包含', value: 'contains' },
  { label: '不包含', value: 'not_contains' },
  { label: '为空', value: 'empty' },
  { label: '不为空', value: 'not_empty' }
]

// 获取运算符显示名称
const getOperatorLabel = (value) => {
  const found = operatorOptions.find(op => op.value === value)
  return found ? found.label : value
}

// branches 双向绑定
const branches = computed({
  get() {
    if (!props.config.branches) {
      props.config.branches = [
        { type: 'IF', variable: '', operator: '==', value: '' },
        { type: 'ELSE' }
      ]
    }
    return props.config.branches
  },
  set(val) {
    props.config.branches = val
  }
})

const hasElse = computed(() => branches.value.some(b => b.type === 'ELSE'))

const branchLabel = (type) => ({
  IF: '如果 (IF)',
  ELSE_IF: '否则如果 (ELSE IF)',
  ELSE: '否则 (ELSE)'
}[type])

// 新增分支
const addBranch = (type) => {
  if (type === 'ELSE' && hasElse.value) return
  const newBranch = { type, variable: '', operator: '==', value: '' }
  if (type === 'ELSE') {
    delete newBranch.variable
    delete newBranch.operator
    delete newBranch.value
  }
  const currentBranches = [...branches.value]
  const elseIdx = currentBranches.findIndex(b => b.type === 'ELSE')
  if (type === 'ELSE') {
    currentBranches.push(newBranch)
  } else {
    if (elseIdx >= 0) {
      currentBranches.splice(elseIdx, 0, newBranch)
    } else {
      currentBranches.push(newBranch)
    }
  }
  branches.value = currentBranches
}

// 删除分支
const removeBranch = (index) => {
  if (branches.value[index].type === 'ELSE') return
  const newBranches = [...branches.value]
  newBranches.splice(index, 1)
  branches.value = newBranches
}

// 设置运算符
const setOperator = (index, operator) => {
  const newBranches = branches.value.map((b, i) => {
    if (i === index) {
      return { ...b, operator }
    }
    return b
  })
  branches.value = newBranches
}

// 插入变量
const insertToField = (field, index, varPath) => {
  const variable = `{{${varPath}}}`
  const currentBranches = branches.value.map((b, i) => {
    if (i === index) {
      return { ...b, [field]: (b[field] || '') + variable }
    }
    return b
  })
  branches.value = currentBranches
}

// 初始化（如无分支则设置默认）
onMounted(() => {
  if (!props.config.branches) {
    props.config.branches = [
      { type: 'IF', variable: '', operator: '==', value: '' },
      { type: 'ELSE' }
    ]
  }
})
</script>

<style scoped>
.branches-container {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  padding: 16px;
}
.branch-item {
  background: #0f1228;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #2a2f4a;
}
.else-branch {
  border-color: #e6a23c;
  background: #1a1a2e;
}
.branch-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.branch-tag {
  font-weight: 600;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 12px;
}
.tag-IF { background: rgba(64, 158, 255, 0.15); color: #409eff; }
.tag-ELSE_IF { background: rgba(144, 147, 153, 0.15); color: #909399; }
.tag-ELSE { background: rgba(230, 162, 60, 0.15); color: #e6a23c; }
.condition-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex-wrap: wrap;
}
.condition-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.condition-item label {
  font-size: 11px;
  color: #94a3b8;
}
.input-with-dropdown {
  display: flex;
  gap: 4px;
  align-items: center;
}

/* ===== 运算符自定义下拉（仿 EndConfig） ===== */
.operator-selector {
  min-width: 120px;
}
.operator-trigger {
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
  font-size: 13px;
}
.operator-trigger:hover {
  border-color: #667eea;
  background: #22284a;
}
.operator-value {
  color: #a78bfa;
}
.operator-placeholder {
  color: #64748b;
}
.operator-arrow {
  color: #94a3b8;
  transition: transform 0.2s;
}
.operator-trigger:hover .operator-arrow {
  color: #a78bfa;
}

/* ===== 变量代码样式 ===== */
.var-code {
  font-family: monospace;
  font-size: 12px;
  color: #a78bfa;
}
.var-desc {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}

/* ===== 按钮、输入框等统一深色 ===== */
.add-branch-area {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
.form-tip {
  font-size: 12px;
  color: #8b8fa9;
  margin-top: 12px;
}

/* ===== 深度覆盖输入框、下拉菜单（与 EndConfig 一致） ===== */
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

:deep(.el-button--danger) {
  background: rgba(239, 68, 68, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
  color: #f87171 !important;
}
:deep(.el-button--danger:hover) {
  background: rgba(239, 68, 68, 0.25) !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
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