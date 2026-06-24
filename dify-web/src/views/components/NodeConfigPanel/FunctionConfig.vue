<template>
  <div class="function-config">
    <!-- 基础配置 -->
    <el-form-item label="函数名称" prop="functionName">
      <el-select
          v-model="functionName"
          placeholder="选择函数"
          filterable
          :loading="functionLoading"
          @change="onFunctionChange"
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

    <!-- 动态参数表单 -->
    <el-divider content-position="left">参数配置</el-divider>
    <div v-if="paramSchema && Object.keys(paramSchema).length">
      <el-form-item
          v-for="(paramDef, paramName) in paramSchema"
          :key="paramName"
          :label="paramName"
          :required="isParamRequired(paramName)"
          class="param-item"
      >
        <div class="param-input-wrapper">
          <el-input
              v-model="paramValues[paramName]"
              :placeholder="`请输入 ${paramDef.description || paramName}`"
              size="small"
              class="param-input"
          />
          <el-dropdown @command="(cmd) => insertParamVariable(paramName, cmd)">
            <el-button size="small" plain>
              插入变量 <el-icon><ArrowDown /></el-icon>
            </el-button>
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
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div v-if="paramDef.description" class="param-hint">{{ paramDef.description }}</div>
      </el-form-item>
    </div>
    <div v-else-if="functionName" class="no-params-hint">
      该函数无需参数
    </div>

    <!-- 输出配置（不变） -->
    <el-divider content-position="left">输出配置</el-divider>
    <el-form-item label="输出变量名" prop="outputVar">
      <el-input v-model="outputVar" placeholder="例如: function_result" />
      <div class="form-tip">其他节点可通过 <code>{{ outputVarDisplay }}</code> 引用</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { functionApi } from '@/api'

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

// ========== 直接操作 props.config（双向 computed） ==========
const functionName = computed({
  get: () => props.config.functionName || '',
  set: (val) => { props.config.functionName = val }
})

const outputVar = computed({
  get: () => props.config.outputVar || 'function_result',
  set: (val) => { props.config.outputVar = val }
})

const parameters = computed({
  get: () => props.config.parameters || {},
  set: (val) => { props.config.parameters = val }
})

// 为了便于模板中绑定，保留一个输出变量显示
const outputVarDisplay = computed(() => {
  const varName = outputVar.value
  return `{{var.${varName}}}`
})

// ========== 函数列表与参数 Schema ==========
const functionList = ref([])
const functionLoading = ref(false)
const paramSchema = ref({})
const paramValues = ref({}) // 用户填写的参数值

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

const onFunctionChange = async (funcName) => {
  if (!funcName) {
    paramSchema.value = {}
    paramValues.value = {}
    return
  }
  try {
    const res = await functionApi.getFunctionInfo(funcName)
    if (res.code === 200 && res.data) {
      const schemaStr = res.data.paramsSchema
      let parsedSchema = {}
      let props = {}
      let required = []
      if (schemaStr) {
        try {
          parsedSchema = JSON.parse(schemaStr)
          if (parsedSchema.properties) {
            props = parsedSchema.properties
            required = parsedSchema.required || []
          } else if (parsedSchema.type === 'object' && parsedSchema.properties) {
            props = parsedSchema.properties
            required = parsedSchema.required || []
          } else {
            const excludeKeys = ['type', 'required', 'properties', 'definitions', '$schema', 'additionalProperties']
            const filtered = {}
            Object.keys(parsedSchema).forEach(key => {
              if (!excludeKeys.includes(key)) filtered[key] = parsedSchema[key]
            })
            props = filtered
            if (parsedSchema.required) required = parsedSchema.required
          }
        } catch (e) {
          console.warn('解析 paramsSchema 失败', e)
        }
      }

      const flatSchema = {}
      Object.keys(props).forEach(key => {
        flatSchema[key] = {
          ...props[key],
          required: required.includes(key)
        }
      })
      paramSchema.value = flatSchema

      const newValues = {}
      const existingParams = parameters.value
      Object.keys(flatSchema).forEach(key => {
        if (existingParams && existingParams[key] !== undefined) {
          newValues[key] = existingParams[key]
        } else {
          const def = flatSchema[key].default
          newValues[key] = def !== undefined ? def : ''
        }
      })
      paramValues.value = newValues
    }
  } catch (e) {
    ElMessage.error('加载函数详情失败')
  }
}

const isParamRequired = (paramName) => {
  return paramSchema.value[paramName]?.required || false
}

// 插入变量
const insertParamVariable = (paramName, varPath) => {
  const variable = `{{${varPath}}}`
  const current = paramValues.value[paramName] || ''
  paramValues.value[paramName] = current + variable
}

// ========== 参数值变化 → 自动写回 config.parameters ==========
// 由于 paramValues 是本地 ref，需要手动同步到 computed 的 parameters
// 这里使用深度 watch 仍然不方便，所以改用 computed 的 get/set 结合 paramValues 变化同步
// 更简单的做法：直接在每次修改 paramValues 后同步
// 但 paramValues 是 ref，无法自动触发 set，因此用一个 watch 来同步参数
watch(paramValues, (newVal) => {
  parameters.value = { ...newVal }
}, { deep: true })

// 初始化
onMounted(() => {
  loadFunctionList()
  if (functionName.value) {
    onFunctionChange(functionName.value)
  }
})
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

.param-item {
  margin-bottom: 16px;
}

.param-input-wrapper {
  display: flex;
  gap: 8px;
  align-items: center;
}

.param-input-wrapper .param-input {
  flex: 1;
}

.param-hint {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.no-params-hint {
  color: #94a3b8;
  font-size: 13px;
  padding: 8px 0;
}

</style>