<template>
  <div class="condition-config">
    <el-divider content-position="left">条件配置</el-divider>

    <el-form-item label="条件表达式" prop="expression">
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
          <el-button size="small" @click="localConfig.expression = ''" plain>清空</el-button>
        </div>
        <el-input
            v-model="localConfig.expression"
            type="textarea"
            :rows="2"
            :placeholder="'例如: {{var.llm_response.score}} > 0.8'"
        />
        <div class="form-tip">
          支持表达式，可以使用输入变量（{{ '{' }}{{ '{' }}input.xxx{{ '}' }}{{ '}' }}）和节点输出变量（{{ '{' }}{{ '{' }}var.xxx{{ '}' }}{{ '}' }}）
        </div>
      </div>
    </el-form-item>
  </div>
</template>

<script setup>
import { reactive, watch, inject, ref, computed } from 'vue'
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

const localConfig = reactive(props.config)

const insertVariable = (varPath) => {
  const variable = `{{${varPath}}}`
  localConfig.expression = (localConfig.expression || '') + variable
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