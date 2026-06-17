<template>
  <div class="function-config">
    <el-divider content-position="left">基础配置</el-divider>

    <el-form-item label="函数名称" prop="functionName">
      <el-select
          v-model="localConfig.functionName"
          placeholder="选择函数"
          filterable
          :loading="functionLoading"
          @focus="loadFunctionList"
      >
        <el-option
            v-for="func in functionList"
            :key="func.name"
            :label="`${func.name} - ${func.desc}`"
            :value="func.name"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="参数配置">
      <div class="param-editor">
        <div class="param-toolbar">
          <el-dropdown @command="insertParamVariable">
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
          <el-button size="small" @click="formatParametersJson" plain>格式化JSON</el-button>
          <el-button size="small" @click="clearParameters" plain>清空</el-button>
        </div>
        <el-input
            v-model="parametersJson"
            type="textarea"
            :rows="6"
            placeholder='{"query": "{{input.query}}"}'
            @change="updateParameters"
        />
      </div>
    </el-form-item>

    <el-divider content-position="left">输出配置</el-divider>

    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="localConfig.outputVar" placeholder="例如: function_result" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { ref, reactive, watch, computed, inject } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { functionApi } from '@/api'

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

const localConfig = reactive(props.config)
const outputVarDisplay = computed(() => {
  const varName = localConfig.outputVar || 'function_result'
  return `{{var.${varName}}}`
})

const functionList = ref([])
const functionLoading = ref(false)
const parametersJson = ref('{}')

const insertParamVariable = (varPath) => {
  const variable = `"{{${varPath}}}"`
  if (parametersJson.value === '{}' || parametersJson.value === '') {
    parametersJson.value = `{\n  "field": ${variable}\n}`
  } else {
    const lastBrace = parametersJson.value.lastIndexOf('}')
    if (lastBrace !== -1) {
      const before = parametersJson.value.substring(0, lastBrace)
      const after = parametersJson.value.substring(lastBrace)
      if (before.trim().endsWith('{')) {
        parametersJson.value = before + `\n  "field": ${variable}` + after
      } else {
        parametersJson.value = before + `,\n  "field": ${variable}` + after
      }
    }
  }
}

const formatParametersJson = () => {
  if (!parametersJson.value || parametersJson.value.trim() === '') {
    parametersJson.value = '{}'
    return
  }
  try {
    const parsed = JSON.parse(parametersJson.value)
    parametersJson.value = JSON.stringify(parsed, null, 2)
    ElMessage.success('JSON格式化成功')
  } catch (e) {
    ElMessage.warning('JSON格式错误，请检查')
  }
}

const clearParameters = () => {
  parametersJson.value = '{}'
  localConfig.parameters = {}
}

const updateParameters = () => {
  try {
    if (parametersJson.value && parametersJson.value.trim()) {
      localConfig.parameters = JSON.parse(parametersJson.value)
    } else {
      localConfig.parameters = {}
    }
  } catch (e) {
    console.warn('参数JSON格式错误')
  }
}

const loadFunctionList = async () => {
  if (functionLoading.value) return
  functionLoading.value = true
  try {
    const res = await functionApi.getFunctionList()
    if (res.code === 200) functionList.value = res.data || []
  } catch (error) {
    console.error('加载函数列表失败:', error)
  } finally {
    functionLoading.value = false
  }
}

if (localConfig.parameters) {
  try {
    parametersJson.value = JSON.stringify(localConfig.parameters, null, 2)
  } catch (e) {
    parametersJson.value = '{}'
  }
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
.param-toolbar {
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