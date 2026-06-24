<template>
  <div class="start-config">
    <el-divider content-position="left">输入变量配置</el-divider>

    <el-form-item label="输入变量">
      <div class="input-vars-editor">
        <div v-for="(inputVar, index) in inputVariables" :key="index" class="input-var-row">
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
import { computed } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  config: { type: Object, required: true },
  node: { type: Object, required: true }
})

// 双向绑定 inputVariables，直接操作父组件草稿
const inputVariables = computed({
  get() {
    if (!props.config.inputVariables) {
      props.config.inputVariables = []
    }
    return props.config.inputVariables
  },
  set(val) {
    props.config.inputVariables = val
  }
})

const addInputVar = () => {
  inputVariables.value = [
    ...inputVariables.value,
    { name: '', type: 'string', defaultValue: '' }
  ]
}

const removeInputVar = (index) => {
  const newVars = [...inputVariables.value]
  newVars.splice(index, 1)
  inputVariables.value = newVars
}
</script>