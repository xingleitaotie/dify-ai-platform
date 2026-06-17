<template>
  <el-form-item label="节点名称">
    <el-input v-model="localName" @change="updateName" />
  </el-form-item>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, default: '' }
})
const emit = defineEmits(['update:name'])

const localName = computed({
  get: () => props.name,
  set: (val) => emit('update:name', val)
})

const updateName = () => {
  emit('update:name', localName.value)
}
</script>