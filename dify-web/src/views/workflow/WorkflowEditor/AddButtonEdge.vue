<template>
  <g class="vue-flow__edge">
    <!-- 底层：实际连线 -->
    <path
        :d="path"
        class="vue-flow__edge-path"
        :stroke="style?.stroke || '#888'"
        stroke-width="2"
        fill="none"
    />

    <!-- 中层：透明热区，常态不拦截鼠标 -->
    <path
        :d="path"
        class="vue-flow__edge-hotpath"
        stroke="transparent"
        stroke-width="14"
        fill="none"
    />

    <!-- 顶层：加号按钮，永远最上层，强制接收鼠标点击 -->
    <g
        v-if="center"
        class="edge-add-group"
        @click.stop.prevent="handleAdd"
    >
      <circle
          :cx="center.x"
          :cy="center.y"
          r="13"
          fill="#667eea"
          stroke="#0a0e27"
          stroke-width="2"
          class="edge-add-circle"
      />
      <text
          :x="center.x"
          :y="center.y + 5"
          text-anchor="middle"
          fill="white"
          font-size="16"
          font-weight="bold"
          class="edge-add-text"
          pointer-events="none"
      >+</text>
    </g>
  </g>
</template>

<script setup>
import { computed, inject } from 'vue'

const props = defineProps({
  id: String,
  source: String,
  target: String,
  sourceX: Number,
  sourceY: Number,
  targetX: Number,
  targetY: Number,
  sourcePosition: String,
  targetPosition: String,
  style: Object,
  selected: Boolean,
  updatable: Boolean,
})

const onAddNodeOnEdge = inject('onAddNodeOnEdge', () => {
  console.error('注入失败 onAddNodeOnEdge')
})

const path = computed(() => {
  const { sourceX, sourceY, targetX, targetY } = props
  const offset = Math.min(Math.abs(targetX - sourceX) * 0.5, 150)
  const cx1 = sourceX + offset
  const cy1 = sourceY
  const cx2 = targetX - offset
  const cy2 = targetY
  return `M ${sourceX} ${sourceY} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${targetX} ${targetY}`
})

const center = computed(() => {
  if (props.sourceX == null || props.targetX == null) return null
  return {
    x: (props.sourceX + props.targetX) / 2,
    y: (props.sourceY + props.targetY) / 2,
  }
})

const handleAdd = (e) => {
  console.log('【加号点击触发】', props.id)
  e.stopPropagation()
  e.preventDefault()
  onAddNodeOnEdge({
    sourceId: props.source,
    targetId: props.target,
    centerPoint: center.value,
  })
}
</script>

<style>

.vue-flow__edge-hotpath {
  pointer-events: stroke;
}

/* 加号容器强制最高点击优先级 */
.edge-add-group {
  cursor: pointer;
  pointer-events: all !important;
}
.edge-add-circle {
  pointer-events: all !important;
}

.edge-add-circle,
.edge-add-text {
  opacity: 0;
  transition: opacity 0.2s ease;
}
.vue-flow__edge:hover .edge-add-circle,
.vue-flow__edge:hover .edge-add-text {
  opacity: 1;
}


/* 拖拽连线脱离节点时高亮红色提示可删除 */
.vue-flow__edge.dragging .vue-flow__edge-path {
  stroke: #f56c6c !important;
  stroke-dasharray: 6 4;
}
</style>