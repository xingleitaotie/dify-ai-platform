<template>
  <div class="end-config">
    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量">
      <div class="output-vars-editor">
        <div v-for="(outputVar, index) in outputVariables" :key="index" class="output-var-row">
          <el-input
              v-model="outputVar.name"
              placeholder="变量名"
              size="small"
              style="width: 150px"
          />

          <div class="source-selector" style="flex: 1">
            <el-dropdown trigger="click" @command="(cmd) => setOutputVarSource(outputVar, cmd)">
              <div class="source-trigger">
                <span v-if="outputVar.source" class="source-value">{{ outputVar.source }}</span>
                <span v-else class="source-placeholder">选择来源变量</span>
                <el-icon class="source-arrow"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <!-- 输入变量部分保持不变 -->
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

                  <!-- 输出变量部分 —— 修改此处 -->
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
          </div>

          <el-button type="danger" size="small" @click="removeOutputVar(index)" :icon="Delete" />
        </div>
        <el-button size="small" type="primary" plain @click="addOutputVar">
          <el-icon><Plus /></el-icon> 添加输出变量
        </el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup>
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

// ✅ 直接操作父组件传递的 config.outputVariables（它是响应式草稿）
const outputVariables = computed({
  get() {
    if (!props.config.outputVariables) {
      props.config.outputVariables = []
    }
    return props.config.outputVariables
  },
  set(val) {
    props.config.outputVariables = val
  }
})

const addOutputVar = () => {
  // 通过 computed setter 替换整个数组，触发响应式更新
  outputVariables.value = [...outputVariables.value, { name: '', source: '' }]
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
.output-vars-editor {
  width: 100%;
}

.output-var-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  background: #0f1228;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid #2a2f4a;
}

.source-selector {
  flex: 1;
  min-width: 200px;
}

.source-trigger {
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
</style>