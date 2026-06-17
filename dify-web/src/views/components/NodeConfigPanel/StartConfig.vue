<template>
  <div class="start-config">
    <el-divider content-position="left">输入变量配置</el-divider>

    <el-form-item label="输入变量">
      <div class="input-vars-editor">
        <div v-for="(inputVar, index) in localConfig.inputVariables" :key="index" class="input-var-row">
          <el-input v-model="inputVar.name" placeholder="变量名" size="small" style="width: 120px" />
          <el-select v-model="inputVar.type" placeholder="类型" size="small" style="width: 100px">
            <el-option label="字符串" value="string" />
            <el-option label="数字" value="number" />
            <el-option label="布尔值" value="boolean" />
            <el-option label="数组" value="array" />
            <el-option label="对象" value="object" />
          </el-select>
          <el-input v-model="inputVar.defaultValue" placeholder="默认值" size="small" style="width: 120px" />
          <el-button type="danger" size="small" @click="removeInputVar(index)" :icon="Delete" />
        </div>
        <el-button size="small" type="primary" plain @click="addInputVar">
          <el-icon><Plus /></el-icon> 添加输入变量
        </el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true }
})
const emit = defineEmits(['update'])

const localConfig = reactive({
  inputVariables: props.config?.inputVariables || []
})

const addInputVar = () => {
  localConfig.inputVariables.push({ name: '', type: 'string', defaultValue: '' })
}

const removeInputVar = (index) => {
  localConfig.inputVariables.splice(index, 1)
}

watch(localConfig, (newVal) => {
  emit('update', newVal)
}, { deep: true })
</script>