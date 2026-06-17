<template>
  <div class="code-config">
    <el-divider content-position="left">代码配置</el-divider>

    <el-form-item label="语言">
      <el-select v-model="localConfig.language" placeholder="选择语言">
        <el-option label="JavaScript" value="javascript" />
      </el-select>
    </el-form-item>

    <el-form-item label="输入数据" prop="inputData">
      <div class="prompt-editor">
        <div class="variable-toolbar">
          <el-dropdown @command="insertVariable">
            <el-button size="small" type="primary" plain>
              插入变量 <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- 输入变量分组 -->
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
          <el-button size="small" @click="localConfig.inputData = ''" plain>清空</el-button>
        </div>
        <el-input
            v-model="localConfig.inputData"
            type="textarea"
            :rows="4"
            :placeholder="'输入数据，支持变量引用，如：{{input.query}} 或 {{var.xxx}}'"
        />
      </div>
    </el-form-item>

    <el-form-item label="代码" prop="code">
      <el-input
          v-model="localConfig.code"
          type="textarea"
          :rows="8"
          placeholder='// 在此编写JavaScript代码'
      />
    </el-form-item>

    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="localConfig.outputVar" placeholder="例如: code_result" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { reactive, watch, computed, inject, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true },
  nodeOutputVars: { type: Array, default: () => [] }
})
const emit = defineEmits(['update'])

const inputVarList = inject('inputVarList', ref([
  { name: 'query', description: '用户输入的问题' }
]))

const nodeOutputVars = computed(() => {
  const vars = props.nodeOutputVars || []
  return vars.filter(v => v.outputVar && v.nodeId !== props.node.id)
})

const localConfig = reactive({
  language: 'javascript',
  code: '',
  outputVar: 'code_result',
  inputData: '',
  ...props.config
})

const outputVarDisplay = computed(() => {
  const varName = localConfig.outputVar || 'code_result'
  return `{{var.${varName}}}`
})

const insertVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.inputData = (localConfig.inputData || '') + variable
}

watch(localConfig, (newVal) => {
  emit('update', newVal)
}, { deep: true })
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
.variable-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.form-tip {
  font-size: 12px;
  color: #8b8fa9;
  margin-top: 4px;
}
</style>