<template>
  <div class="node-config-panel">
    <el-form ref="configFormRef" :model="localConfig" :rules="formRules" label-width="100px">
      <!-- 通用配置：节点名称 -->
      <BaseConfig v-model:name="nodeName" />

      <!-- 根据节点类型渲染对应的配置组件 -->
      <component
          :is="configComponent"
          v-if="configComponent"
          v-bind="componentProps"
      />
    </el-form>

    <div class="panel-actions">
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" @click="saveConfig">保存</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, inject } from 'vue'
import { cloneDeep } from 'lodash-es'
import { ElMessage } from 'element-plus'
import BaseConfig from './BaseConfig.vue'
import LLMConfig from './LLMConfig.vue'
import RAGConfig from './RAGConfig.vue'
import FunctionConfig from './FunctionConfig.vue'
import AgentConfig from './AgentConfig.vue'
import ConditionConfig from './ConditionConfig.vue'
import CodeConfig from './CodeConfig.vue'
import StartConfig from './StartConfig.vue'
import EndConfig from './EndConfig.vue'

const props = defineProps({
  node: { type: Object, required: true },
  nodes: { type: Object, required: true },
  edges: { type: Object, required: true }
})

// 在 setup 中，props 定义之后立即打印
console.log('【NodeConfigPanel】收到的 nodes:', props.nodes?.value)
console.log('【NodeConfigPanel】收到的 edges:', props.edges?.value)
console.log('【NodeConfigPanel】当前节点 ID:', props.node.id)
console.log('【NodeConfigPanel】全部 props:', props)

const emit = defineEmits(['update', 'close'])


// 节点类型到组件的映射
const componentMap = {
  'LLM': LLMConfig,
  'RAG': RAGConfig,
  'FUNCTION': FunctionConfig,
  'AGENT': AgentConfig,
  'CONDITION': ConditionConfig,
  'CODE': CodeConfig,
  'START': StartConfig,
  'END': EndConfig
}

const configComponent = computed(() => componentMap[props.node.type])

// 注入的变量（来自父组件）
// 注入全局数据
const inputVarList = inject('inputVarList', [])

// 计算前置节点的输出变量
const preNodeOutputVars = computed(() => {
  const currentId = props.node.id;
  // 兼容处理：props.nodes 可能是 ref 或数组
  const nodesArr = Array.isArray(props.nodes) ? props.nodes : (props.nodes?.value || []);
  const edgesArr = Array.isArray(props.edges) ? props.edges : (props.edges?.value || []);
  const visited = new Set();
  const queue = [currentId];
  const ancestors = [];

  while (queue.length > 0) {
    const nodeId = queue.shift();
    const incomingEdges = edgesArr.filter(e => e.target === nodeId);
    for (const edge of incomingEdges) {
      const sourceId = edge.source;
      if (!visited.has(sourceId)) {
        visited.add(sourceId);
        queue.push(sourceId);
        const sourceNode = nodesArr.find(n => n.id === sourceId);
        if (sourceNode && sourceNode.data?.config?.outputVar) {
          ancestors.push({
            nodeId: sourceNode.id,
            nodeName: sourceNode.data.name,
            nodeType: sourceNode.type,
            outputVar: sourceNode.data.config.outputVar
          });
        }
      }
    }
  }
  // 去重
  const unique = [];
  const seen = new Set();
  for (const item of ancestors) {
    if (!seen.has(item.nodeId)) {
      seen.add(item.nodeId);
      unique.push(item);
    }
  }
  return unique;
})

// 本地配置
const localConfig = reactive(cloneDeep(props.node.config))

// 本地节点名称
const nodeName = ref(props.node.name)

// 动态表单验证规则
const formRules = computed(() => {
  const type = props.node.type
  const rules = {}
  if (type === 'LLM') {
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  if (type === 'RAG') {
    rules.query = [{ required: true, message: '请输入查询内容', trigger: 'blur' }]
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  if (type === 'FUNCTION') {
    rules.functionName = [{ required: true, message: '请选择函数', trigger: 'change' }]
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  if (type === 'AGENT') {
    rules.agentId = [{ required: true, message: '请选择Agent', trigger: 'change' }]
    rules.query = [{ required: true, message: '请输入查询内容', trigger: 'blur' }]
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  if (type === 'CODE') {
    rules.code = [{ required: true, message: '请编写代码', trigger: 'blur' }]
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  if (type === 'CONDITION') {
    rules.expression = [{ required: true, message: '请填写条件表达式', trigger: 'blur' }]
  }
  if (type === 'AGENT') {
    rules.agentId = [{ required: true, message: '请选择Agent', trigger: 'change' }]
    rules.query = [{ required: true, message: '请输入查询内容', trigger: 'blur' }]
    rules.outputVar = [{ required: true, message: '请填写输出变量名', trigger: 'blur' }]
  }
  return rules
})

const configFormRef = ref(null)

// 传递深拷贝后的本地副本给子组件
const componentProps = computed(() => ({
  config: localConfig,
  node: props.node,
  inputVarList,
  nodeOutputVars: preNodeOutputVars.value,
  formRef: configFormRef
}))

onMounted(() => {
  Object.assign(localConfig, props.node.config)
  // 如果 props.node.config 中没有 inputs，保留已有 inputs
  if (localConfig.inputs === undefined) {
    localConfig.inputs = []
  }
})

// 保存：将本地草稿提交到父组件
const saveConfig = async () => {
  if (configFormRef.value) {
    try {
      await configFormRef.value.validate()
    } catch {
      ElMessage.warning('请完善表单必填项')
      return
    }
  }
  // ✅ 将深拷贝后的纯净数据提交，避免外部再次引用
  emit('update', {
    ...cloneDeep(localConfig),
    name: nodeName.value
  })
  emit('close')
  ElMessage.success('配置保存成功')
}

</script>

<style scoped>
@import './styles/config-common.css';
</style>