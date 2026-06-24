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

            <div class="condition-item">
              <label>运算符</label>
              <el-select v-model="branch.operator" size="small" style="width: 140px">
                <el-option label="等于" value="==" />
                <el-option label="不等于" value="!=" />
                <el-option label="大于" value=">" />
                <el-option label="大于等于" value=">=" />
                <el-option label="小于" value="<" />
                <el-option label="小于等于" value="<=" />
                <el-option label="包含" value="contains" />
                <el-option label="不包含" value="not_contains" />
                <el-option label="为空" value="empty" />
                <el-option label="不为空" value="not_empty" />
              </el-select>
            </div>

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
import { computed, inject, ref } from 'vue'
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

// 用 computed 安全读取/写入 branches，不再需要 reactive 本地副本
const branches = computed({
  get() {
    return props.config.branches || [
      { type: 'IF', variable: '', operator: '==', value: '' },
      { type: 'ELSE' }
    ]
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

const removeBranch = (index) => {
  if (branches.value[index].type === 'ELSE') return
  const newBranches = [...branches.value]
  newBranches.splice(index, 1)
  branches.value = newBranches
}

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
}
.var-code {
  font-family: monospace;
  font-size: 12px;
  color: #a78bfa;
}
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
</style>