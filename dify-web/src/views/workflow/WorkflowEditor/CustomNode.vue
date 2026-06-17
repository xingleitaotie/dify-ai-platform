<template>
  <div class="workflow-node" :class="{ selected }">
    <Handle type="target" :position="Position.Left" class="handle handle-left" />

    <div class="node-header">
      <span class="node-icon">{{ icon }}</span>
      <span class="node-title">{{ data.name }}</span>
      <el-icon class="delete-icon" @click.stop="handleDelete"><Close /></el-icon>
    </div>

    <div class="node-content">
      <div class="node-preview">{{ preview }}</div>
    </div>

    <!-- 右侧输出点：END 节点隐藏 -->
    <Handle
        v-if="type !== 'END'"
        type="source"
        :position="Position.Right"
        class="handle handle-right"
    />
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { Close } from '@element-plus/icons-vue'

const props = defineProps({
  id: String,
  type: String,
  data: Object,
  selected: Boolean,
})

const deleteNodeFunc = inject('deleteNode')
const edges = inject('edges', [])

const icon = computed(() => {
  const icons = {
    START: '▶',
    END: '●',
    LLM: '🤖',
    RAG: '📚',
    FUNCTION: '⚙️',
    AGENT: '👤',
    CONDITION: '🔀',
    CODE: '</>'
  }
  return icons[props.type] || '📄'
})

const preview = computed(() => {
  const config = props.data.config || {}
  switch (props.type) {
    case 'LLM': return config.userPrompt?.substring(0, 50) || config.systemPrompt?.substring(0, 40) || '未配置提示词'
    case 'RAG': return config.query || '未配置查询'
    case 'FUNCTION': return config.functionName || '未选择函数'
    case 'AGENT': return config.agentId ? `Agent ID: ${config.agentId}` : '未选择Agent'
    case 'CONDITION': return config.expression || '未配置条件'
    case 'CODE': return config.language || 'JavaScript'
    default: return props.data.name
  }
})

function handleDelete() {
  if (deleteNodeFunc) deleteNodeFunc(props.id)
}
</script>

<style scoped>
.workflow-node {
  width: 220px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  user-select: none;
  font-size: 12px;
}
.workflow-node.selected {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.4);
}

.node-header {
  padding: 10px 14px;
  border-radius: 11px 11px 0 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: white;
  background: #252b48;
}

.node-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}
.delete-icon {
  cursor: pointer;
  opacity: 0.6;
}
.delete-icon:hover {
  opacity: 1;
  color: #f87171;
  transform: scale(1.1);
}
.node-content {
  padding: 10px 14px;
  color: #cbd5e6;
}
.node-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #94a3b8;
}

.handle {
  width: 14px;
  height: 14px;
  background: #667eea;
  border: 2px solid #1a1f3a;
  border-radius: 50%;
}

.handle-left {
  left: -7px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
}

.handle-right {
  right: -7px !important;
  top: 50% !important;
  transform: translateY(-50%) !important;
}

.handle-right:hover {
  background: #a78bfa;
}

</style>