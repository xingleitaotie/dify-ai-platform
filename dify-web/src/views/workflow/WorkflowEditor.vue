<template>
  <div class="workflow-editor">
    <!-- 工作流名称栏 -->
    <div class="workflow-header">
      <div class="workflow-info">
        <div class="workflow-name-wrapper">
          <el-input
              v-if="isEditingName"
              ref="nameInputRef"
              v-model="editingNameValue"
              size="default"
              style="width: 350px"
              @blur="saveWorkflowName"
              @keyup.enter="saveWorkflowName"
          />
          <span v-else class="workflow-name" @click="startEditName">
            {{ workflowName || '未命名工作流' }}
            <el-icon class="edit-icon"><Edit /></el-icon>
          </span>
        </div>
        <span class="workflow-id" v-if="route.params.id && route.params.id !== 'new'">
          ID: {{ route.params.id }}
        </span>
      </div>
      <div class="workflow-actions">
        <el-button size="small" @click="showWorkflowInfo" plain>
          <el-icon><InfoFilled /></el-icon>
          信息
        </el-button>
      </div>
    </div>

    <!-- 修改工具栏按钮组 -->
    <div class="editor-toolbar">
      <el-button-group>
        <el-button type="primary" @click="saveWorkflow" :loading="saving">
          <el-icon><DocumentAdd /></el-icon>
          保存
        </el-button>
        <el-button type="success" @click="doPublishWorkflow" :loading="publishing">
          <el-icon><Upload /></el-icon>
          发布
        </el-button>
        <el-button type="warning" @click="executeWorkflow" :loading="executing">
          <el-icon><VideoPlay /></el-icon>
          执行
        </el-button>
        <el-button @click="clearCanvas">
          <el-icon><Delete /></el-icon>
          清空
        </el-button>
        <el-button @click="variableDrawerVisible = true">
          <el-icon><List /></el-icon>
          变量
        </el-button>
        <el-button @click="showLastExecutionResult">
          <el-icon><Clock /></el-icon>
          查看历史
        </el-button>
      </el-button-group>

      <div class="node-palette">
        <div
            v-for="nodeType in nodeTypes"
            :key="nodeType.type"
            class="palette-item"
            draggable="true"
            @dragstart="onDragStart($event, nodeType)"
        >
          <span class="palette-icon" :style="{ background: nodeType.color }">
            {{ nodeType.icon }}
          </span>
          <span>{{ nodeType.label }}</span>
        </div>
      </div>
    </div>
    <!-- 画布部分保持不变 -->
    <div
        class="canvas-wrapper"
        ref="canvasWrapper"
        @mousemove="onGlobalMouseMove"
        @mouseup="onGlobalMouseUp"
    >
      <!-- SVG 连线层 -->
      <svg
          class="edges-canvas"
          :style="{
          width: canvasWidth + 'px',
          height: canvasHeight + 'px',
          position: 'absolute',
          top: 0,
          left: 0,
          pointerEvents: 'none'
        }"
      >
        <g v-for="edge in edges" :key="edge.id">
          <path
              :d="getEdgePath(edge)"
              class="edge"
              :class="{ 'edge-selected': selectedEdgeId === edge.id }"
              fill="none"
              stroke="#b1b1b7"
              stroke-width="2"
              marker-end="url(#arrowhead)"
              @click.stop="selectEdge(edge)"
          />
        </g>

        <path
            v-if="isConnecting"
            :d="tempPath"
            fill="none"
            stroke="#409eff"
            stroke-width="2"
            stroke-dasharray="5,5"
            marker-end="url(#arrowhead-temp)"
        />

        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#b1b1b7" />
          </marker>
          <marker id="arrowhead-temp" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#409eff" />
          </marker>
        </defs>
      </svg>

      <!-- 节点层 -->
      <div
          v-for="node in nodes"
          :key="node.id"
          class="workflow-node"
          :style="getNodeStyle(node)"
          :class="{
          selected: selectedNodeId === node.id,
          'drag-over': dragOverNodeId === node.id
        }"
          @mousedown="onNodeMouseDown($event, node)"
          @click.stop="selectNode(node)"
      >
        <div class="node-header" :class="getNodeHeaderClass(node.type)">
          <span class="node-icon">{{ getNodeIcon(node.type) }}</span>
          <span class="node-title">{{ node.name }}</span>
          <el-icon class="delete-icon" @click.stop="deleteNode(node.id)">
            <Close />
          </el-icon>
        </div>
        <div class="node-content">
          <div class="node-preview">{{ getNodePreview(node) }}</div>
        </div>
        <div class="anchor anchor-right" @mousedown.stop="startConnect($event, node)" title="拖拽连接到下一个节点"></div>
        <div class="anchor anchor-left" @mousedown.stop="startConnect($event, node, 'left')" title="从上一个节点连接到此"></div>
      </div>
    </div>

    <!-- 节点配置抽屉 -->
    <el-drawer
        v-model="nodeConfigDrawer"
        :title="'配置节点: ' + (currentNode ? currentNode.name : '')"
        size="40%"
    >
      <NodeConfigPanel
          v-if="currentNode"
          :node="currentNode"
          @update="updateNodeConfig"
          @close="nodeConfigDrawer = false"
      />
    </el-drawer>

    <!-- 变量面板抽屉 -->
    <el-drawer
        v-model="variableDrawerVisible"
        title="工作流变量"
        size="30%"
        direction="rtl"
    >
      <div class="variable-panel">
        <el-tabs>
          <el-tab-pane label="输入变量">
            <el-alert type="info" :closable="false" style="margin-bottom: 12px;">
              <template #title>
                默认输入变量：query
              </template>
              测试运行时，输入的文本会自动赋值给 <code>input.query</code>
            </el-alert>
            <el-table :data="inputVariables" size="small" border>
              <el-table-column prop="name" label="变量名" />
              <el-table-column prop="description" label="说明" />
              <el-table-column prop="example" label="使用方式" />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="节点输出变量">
            <div class="node-vars-list">
              <div v-for="node in nodes" :key="node.id" class="node-var-item">
                <div class="node-name">{{ node.name }} ({{ node.type }})</div>
                <div class="node-outputs">
                  <template v-if="node.config?.outputVar">
                    <el-tag size="small" type="success">
                      var.{{ node.config.outputVar }}
                    </el-tag>
                    <span class="var-desc"> - 该节点的输出变量</span>
                  </template>
                  <template v-else>
                    <el-tag size="small" type="info">未设置输出变量</el-tag>
                  </template>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>

        <div class="variable-usage">
          <el-divider>变量使用说明</el-divider>
          <div class="usage-tips" v-pre>
            <p><strong>输入变量：</strong></p>
            <ul>
              <li><code>{{input.query}}</code> - 用户在测试面板输入的内容</li>
            </ul>
            <p><strong>节点输出变量：</strong></p>
            <ul>
              <li>在节点配置中设置"输出变量名"后，其他节点可通过 <code>{{var.变量名}}</code> 引用</li>
              <li>示例：<code>{{var.llm_output}}</code> 引用 LLM 节点的输出</li>
            </ul>
            <p><strong>提示词示例：</strong></p>
            <ul>
              <li><code>请回答：{{input.query}}</code></li>
              <li><code>根据{{var.rag_documents}}回答问题：{{input.query}}</code></li>
            </ul>
          </div>
        </div>
      </div>
    </el-drawer>
    <!-- 执行结果对话框 -->
    <el-dialog
        v-model="executionDialogVisible"
        title="工作流执行结果"
        width="70%"
        class="execution-dialog"
    >
      <el-tabs>
        <el-tab-pane label="最终输出">
          <div class="final-output">
            <pre class="execution-output">{{ executionResult }}</pre>
          </div>
        </el-tab-pane>

        <el-tab-pane label="节点执行详情">
          <div class="node-details-list">
            <div
                v-for="node in executionNodeResults"
                :key="node.nodeId"
                class="node-detail-card"
                :class="{ 'node-success': node.status === 'SUCCESS', 'node-error': node.status !== 'SUCCESS' }"
            >
              <!-- 节点头部 -->
              <div class="node-detail-header">
                <div class="node-title-info">
                  <span class="node-icon">{{ getNodeIconByType(node.nodeType) }}</span>
                  <span class="node-name">{{ node.nodeName }}</span>
                  <el-tag
                      :type="node.status === 'SUCCESS' ? 'success' : 'danger'"
                      size="small"
                      effect="dark"
                  >
                    {{ node.status === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                </div>
                <div class="node-meta">
                  <span class="node-time">⏱️ 耗时: {{ node.costTime }}ms</span>
                </div>
              </div>

              <!-- 输入信息：极简显示 -->
              <div class="node-detail-section" v-if="node.input && node.nodeType !== 'END'">
                <div class="section-header">
                  <el-icon><Upload /></el-icon>
                  <span>输入</span>
                </div>
                <div class="section-content">
                  <pre>{{ formatNodeInput(node.input, node.nodeType) }}</pre>
                </div>
              </div>

              <!-- 输出信息：极简显示 + 开始节点不显示 -->
              <div class="node-detail-section" v-if="node.output && node.nodeType !== 'START'">
                <div class="section-header">
                  <el-icon><Download /></el-icon>
                  <span>输出</span>
                </div>
                <div class="section-content">
                  <pre>{{ formatNodeOutput(node.output, node.nodeType) }}</pre>
                </div>
              </div>

              <!-- 错误信息 -->
              <div class="node-detail-section error" v-if="node.errorMsg">
                <div class="section-header">
                  <el-icon><CircleClose /></el-icon>
                  <span>错误信息</span>
                </div>
                <div class="section-content error-content">
                  <pre>{{ node.errorMsg }}</pre>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 工作流信息对话框 -->
    <el-dialog
        v-model="infoDialogVisible"
        title="工作流信息"
        width="400px"
    >
      <el-form :model="workflowInfoForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="workflowInfoForm.name" placeholder="工作流名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
              v-model="workflowInfoForm.description"
              type="textarea"
              :rows="3"
              placeholder="工作流描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="infoDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="updateWorkflowInfo">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, provide, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentAdd, Upload, VideoPlay, Delete, Close, List, Edit, InfoFilled, Download, CircleClose } from '@element-plus/icons-vue'
import {
  createWorkflow,
  updateWorkflow,
  getWorkflowDetail,
  publishWorkflow,
  executeWorkflow as executeWorkflowApi
} from '@/api/workflow'
import NodeConfigPanel from '../components/NodeConfigPanel.vue'

const route = useRoute()
const router = useRouter()

// 工作流名称和描述
const workflowName = ref('')
const workflowDescription = ref('')
const isEditingName = ref(false)
const editingNameValue = ref('')
const nameInputRef = ref(null)
const infoDialogVisible = ref(false)
const workflowInfoForm = ref({
  name: '',
  description: ''
})

// 根据节点类型获取图标
const getNodeIconByType = (nodeType) => {
  const icons = {
    'START': '▶',
    'END': '●',
    'LLM': '🤖',
    'RAG': '📚',
    'FUNCTION': '⚙️',
    'AGENT': '👤',
    'CONDITION': '🔀',
    'CODE': '</>'
  }
  return icons[nodeType] || '📄'
}

// ====================== 【极简格式化：输入】 ======================
const formatNodeInput = (input, nodeType) => {
  if (!input) return "";

  // 结束节点：直接返回空，UI 不渲染
  if (nodeType === "END") {
    return "";
  }

  // 开始节点：只显示用户输入
  if (nodeType === "START") {
    return input.inputs?.query || input.query || "";
  }

  // 其他节点：优先上一步输出（prevOutput）
  if (input.runtime?.prevOutput) {
    const prev = input.runtime.prevOutput;
    if (typeof prev === "object") {
      // 适配 {llm_output: [...]} 这种常见结构
      if (prev.llm_output && Array.isArray(prev.llm_output)) {
        return prev.llm_output.join("\n");
      }
      return JSON.stringify(prev, null, 2);
    }
    return prev;
  }

  // 兜底
  if (input.config?.query) return input.config.query;
  if (input.runtime?.input?.query) return input.runtime.input.query;
  if (input.query) return input.query;
  if (input.inputs?.query) return input.inputs.query;

  return "";
}

// ====================== 【极简格式化：输出】 ======================
const formatNodeOutput = (output, nodeType) => {
  if (!output) return "无输出"
  if (nodeType === "START") return "无输出"

  // 纯文本优先
  if (typeof output === "string") return output

  // LLM / 结束节点：取核心答案
  if (output.content) return output.content
  if (output.result) return output.result
  if (output.answer) return output.answer
  if (output.response) return output.response

  // RAG：返回上下文
  if (output.context) return output.context

  // 兜底：返回字符串
  return JSON.stringify(output, null, 2)
}

// 显示工作流信息对话框
const showWorkflowInfo = () => {
  workflowInfoForm.value = {
    name: workflowName.value,
    description: workflowDescription.value
  }
  infoDialogVisible.value = true
}

// 更新工作流信息
const updateWorkflowInfo = async () => {
  if (!workflowInfoForm.value.name || !workflowInfoForm.value.name.trim()) {
    ElMessage.warning('工作流名称不能为空')
    return
  }

  workflowName.value = workflowInfoForm.value.name.trim()
  workflowDescription.value = workflowInfoForm.value.description || ''
  infoDialogVisible.value = false

  // 如果已经有ID，直接保存
  if (route.params.id && route.params.id !== 'new') {
    await saveWorkflow()
  }
}

// 获取拓扑排序（节点执行顺序）
const getTopologicalOrder = () => {
  const order = []
  const visited = new Set()
  const inDegree = new Map()

  // 初始化入度
  nodes.value.forEach(node => {
    inDegree.set(node.id, 0)
  })

  // 计算入度
  edges.value.forEach(edge => {
    inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1)
  })

  // 找到开始节点（入度为0）
  const queue = []
  nodes.value.forEach(node => {
    if (inDegree.get(node.id) === 0) {
      queue.push(node.id)
    }
  })

  // BFS 拓扑排序
  while (queue.length > 0) {
    const nodeId = queue.shift()
    if (visited.has(nodeId)) continue
    visited.add(nodeId)
    order.push(nodeId)

    // 找到所有后继节点
    edges.value.forEach(edge => {
      if (edge.source === nodeId) {
        const targetId = edge.target
        const newDegree = (inDegree.get(targetId) || 0) - 1
        inDegree.set(targetId, newDegree)
        if (newDegree === 0 && !visited.has(targetId)) {
          queue.push(targetId)
        }
      }
    })
  }

  return order
}

// 开始编辑名称
const startEditName = () => {
  editingNameValue.value = workflowName.value
  isEditingName.value = true
  nextTick(() => {
    nameInputRef.value?.focus()
  })
}

// 保存工作流名称
const saveWorkflowName = async () => {
  isEditingName.value = false
  if (editingNameValue.value && editingNameValue.value.trim()) {
    workflowName.value = editingNameValue.value.trim()
    // 如果已经有ID，自动保存
    if (route.params.id && route.params.id !== 'new') {
      await saveWorkflow()
    }
  }
}

const nodeTypes = [
  { type: 'START', label: '开始节点', icon: '▶', color: '#67c23a' },
  { type: 'END', label: '结束节点', icon: '●', color: '#f56c6c' },
  { type: 'LLM', label: '大模型节点', icon: '🤖', color: '#409eff' },
  { type: 'RAG', label: '知识库节点', icon: '📚', color: '#e6a23c' },
  { type: 'FUNCTION', label: '函数节点', icon: '⚙️', color: '#909399' },
  { type: 'AGENT', label: 'Agent节点', icon: '👤', color: '#67c23a' },
  { type: 'CONDITION', label: '条件节点', icon: '🔀', color: '#f56c6c' },
  { type: 'CODE', label: '代码节点', icon: '</>', color: '#909399' }
]

const nodes = ref([])
const edges = ref([])
const selectedNodeId = ref(null)
const selectedEdgeId = ref(null)
const saving = ref(false)
const publishing = ref(false)
const testing = ref(false)
const executing = ref(false)
const nodeConfigDrawer = ref(false)
const currentNode = ref(null)
const executionDialogVisible = ref(false)
const executionResult = ref('')
const executionNodeResults = ref([])
const variableDrawerVisible = ref(false)
// ==========================================
// 会话缓存：仅保存【最新一条执行结果】（不存库）
// ==========================================
const SESSION_KEY = computed(() => {
  const wid = route.params.id || 'new'
  return `workflow_last_exec_${wid}`
})

// 加载最新历史
const loadLastExecutionResult = () => {
  try {
    const json = sessionStorage.getItem(SESSION_KEY.value)
    if (json) {
      const data = JSON.parse(json)
      executionResult.value = data.executionResult || ''
      executionNodeResults.value = data.executionNodeResults || []
      ElMessage.success('已加载最新执行结果')
    }
  } catch (e) {
    console.warn('无历史执行结果')
  }
}

// 保存：每次执行完都会覆盖之前的
const saveLastExecutionResult = () => {
  const data = {
    executionResult: executionResult.value,
    executionNodeResults: executionNodeResults.value,
    time: new Date().toLocaleString()
  }
  sessionStorage.setItem(SESSION_KEY.value, JSON.stringify(data))
}

// 查看历史 = 直接加载最新一条
const showLastExecutionResult = () => {
  loadLastExecutionResult()
  if (executionResult.value || executionNodeResults.value.length > 0) {
    executionDialogVisible.value = true
  } else {
    ElMessage.info('暂无执行记录')
  }
}
// 测试输入
const testInput = ref('')

// 输入变量列表
const inputVariables = ref([
  { name: 'query', description: '用户输入的查询内容', example: '{{input.query}}' }
])

// 收集输入变量（始终可用）
const inputVarList = computed(() => {
  const startNode = nodes.value.find(n => n.type === 'START')
  if (startNode && startNode.config && startNode.config.inputVariables) {
    return startNode.config.inputVariables
  }
  return [{ name: 'query', description: '用户输入的问题', defaultValue: '' }]
})

// 提供变量列表给子组件
// 提供给子组件
provide('inputVarList', inputVarList)

// 获取节点的所有祖先节点（包括间接依赖，但不包括兄弟节点）
const getAncestors = (nodeId, visited = new Set()) => {
  const ancestors = []
  edges.value.forEach(edge => {
    if (edge.target === nodeId && !visited.has(edge.source)) {
      visited.add(edge.source)
      ancestors.push(edge.source)
      ancestors.push(...getAncestors(edge.source, visited))
    }
  })
  return [...new Set(ancestors)] // 去重
}

// 收集节点输出变量（只包含祖先节点）
const nodeOutputVars = computed(() => {
  const outputs = []
  const currentNodeId = currentNode.value?.id

  if (!currentNodeId) return outputs

  // 获取所有祖先节点
  const ancestors = getAncestors(currentNodeId)

  // 1. 添加输入变量（直接在根层级显示，不单独分类）
  const startNode = nodes.value.find(n => n.type === 'START')
  if (startNode && startNode.config?.inputVariables) {
    startNode.config.inputVariables.forEach(input => {
      outputs.push({
        name: `input.${input.name}`,
        fromNode: '开始节点',  // 🔥 改为"开始节点"
        isInputVar: true,
        description: input.description
      })
    })
  } else {
    // 默认输入变量
    outputs.push({
      name: 'input.query',
      fromNode: '开始节点',
      isInputVar: true,
      description: '用户输入的问题'
    })
  }

  // 2. 添加节点输出变量
  for (const node of nodes.value) {
    if (ancestors.includes(node.id) && node.type !== 'START') {  // 🔥 排除开始节点（已在上面处理）
      const config = node.config || {}
      if (config.outputVar && config.outputVar.trim()) {
        outputs.push({
          name: config.outputVar,
          fromNode: node.name,
          nodeId: node.id,
          nodeType: node.type,
          config: config,
          description: `${node.name} 的输出`
        })
      }
    }
  }

  console.log('可用节点输出变量（仅祖先节点）:', outputs.map(o => o.name))

  return outputs
})

provide('nodeOutputVars', nodeOutputVars)

const canvasWrapper = ref(null)
const canvasWidth = ref(2000)
const canvasHeight = ref(1500)
const zoom = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)

const isConnecting = ref(false)
const connectSourceNode = ref(null)
const connectSourceSide = ref('right')
const connectStartPoint = ref({ x: 0, y: 0 })
const tempPath = ref('')
const dragOverNodeId = ref(null)

let isDraggingNode = false
let dragNode = null
let dragNodeStartX = 0
let dragNodeStartY = 0
let dragMouseStartX = 0
let dragMouseStartY = 0

let isPanning = false
let panStartX = 0
let panStartY = 0
let panStartOffsetX = 0
let panStartOffsetY = 0

let nodeIdCounter = 0

const getNodeStyle = (node) => {
  const x = node.x * zoom.value + offsetX.value
  const y = node.y * zoom.value + offsetY.value
  return { left: x + 'px', top: y + 'px', position: 'absolute' }
}

const getEdgePath = (edge) => {
  const sourceNode = nodes.value.find(n => n.id === edge.source)
  const targetNode = nodes.value.find(n => n.id === edge.target)
  if (!sourceNode || !targetNode) return ''

  const sourceX = (sourceNode.x + 190) * zoom.value + offsetX.value
  const sourceY = (sourceNode.y + 40) * zoom.value + offsetY.value
  const targetX = (targetNode.x + 10) * zoom.value + offsetX.value
  const targetY = (targetNode.y + 40) * zoom.value + offsetY.value

  const midX = (sourceX + targetX) / 2
  return `M ${sourceX} ${sourceY} C ${midX} ${sourceY}, ${midX} ${targetY}, ${targetX} ${targetY}`
}

const startConnect = (event, node, side = 'right') => {
  event.preventDefault()
  event.stopPropagation()

  isConnecting.value = true
  connectSourceNode.value = node
  connectSourceSide.value = side

  const rect = canvasWrapper.value.getBoundingClientRect()
  const offsetXPos = side === 'right' ? 190 : 10
  const sx = node.x * zoom.value + offsetX.value + offsetXPos
  const sy = node.y * zoom.value + offsetY.value + 40
  connectStartPoint.value = { x: sx, y: sy }

  const mx = event.clientX - rect.left
  const my = event.clientY - rect.top
  updateTempPath(mx, my)

  document.body.style.cursor = 'crosshair'
  document.body.style.userSelect = 'none'
}

const updateTempPath = (mx, my) => {
  if (!isConnecting.value || !connectStartPoint.value) return
  const x = connectStartPoint.value.x
  const y = connectStartPoint.value.y
  const mid = (x + mx) / 2
  tempPath.value = `M ${x} ${y} C ${mid} ${y}, ${mid} ${my}, ${mx} ${my}`
}

const getMouseCanvasPos = (clientX, clientY) => {
  const r = canvasWrapper.value.getBoundingClientRect()
  return {
    x: clientX - r.left,
    y: clientY - r.top
  }
}

const isMouseInNodeArea = (clientX, clientY, node) => {
  const p = getMouseCanvasPos(clientX, clientY)
  const nx = node.x * zoom.value + offsetX.value
  const ny = node.y * zoom.value + offsetY.value
  const w = 200 * zoom.value
  const h = 80 * zoom.value
  return p.x >= nx && p.x <= nx + w && p.y >= ny && p.y <= ny + h
}

const onGlobalMouseMove = (e) => {
  const pos = getMouseCanvasPos(e.clientX, e.clientY)

  if (isConnecting.value) {
    updateTempPath(pos.x, pos.y)

    let foundNode = null
    for (const n of nodes.value) {
      if (n.id === connectSourceNode.value?.id) continue
      if (isMouseInNodeArea(e.clientX, e.clientY, n)) {
        foundNode = n
        break
      }
    }
    dragOverNodeId.value = foundNode?.id || null

  } else if (isDraggingNode) {
    const dx = (e.clientX - dragMouseStartX) / zoom.value
    const dy = (e.clientY - dragMouseStartY) / zoom.value
    dragNode.x = Math.max(0, dragNodeStartX + dx)
    dragNode.y = Math.max(0, dragNodeStartY + dy)
    edges.value = [...edges.value]

  } else if (isPanning) {
    offsetX.value = panStartOffsetX + (e.clientX - panStartX)
    offsetY.value = panStartOffsetY + (e.clientY - panStartY)
  }
}

const onGlobalMouseUp = (e) => {
  if (isConnecting.value) {
    let targetNode = null
    for (const n of nodes.value) {
      if (n.id === connectSourceNode.value?.id) continue
      if (isMouseInNodeArea(e.clientX, e.clientY, n)) {
        targetNode = n
        break
      }
    }

    if (targetNode && connectSourceNode.value) {
      const edgeExists = edges.value.some(edge =>
          edge.source === connectSourceNode.value.id && edge.target === targetNode.id
      )

      if (!edgeExists) {
        const newEdge = {
          id: `edge_${Date.now()}_${Math.random()}`,
          source: connectSourceNode.value.id,
          target: targetNode.id,
          sourceHandle: connectSourceSide.value,
          targetHandle: 'left'
        }
        edges.value.push(newEdge)
        ElMessage.success(`已连接 ${connectSourceNode.value.name} → ${targetNode.name}`)
      } else {
        ElMessage.warning('连线已存在')
      }
    }

    isConnecting.value = false
    connectSourceNode.value = null
    connectSourceSide.value = 'right'
    tempPath.value = ''
    dragOverNodeId.value = null
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  if (isDraggingNode) {
    isDraggingNode = false
    dragNode = null
  }

  if (isPanning) {
    isPanning = false
  }
}

const onNodeMouseDown = (e, node) => {
  if (e.target.classList.contains('anchor') || e.target.classList.contains('delete-icon')) return
  e.preventDefault()
  e.stopPropagation()
  isDraggingNode = true
  dragNode = node
  dragNodeStartX = node.x
  dragNodeStartY = node.y
  dragMouseStartX = e.clientX
  dragMouseStartY = e.clientY
}

const onCanvasMouseDown = (e) => {
  if (isConnecting.value || isDraggingNode || e.target.closest('.workflow-node')) return
  isPanning = true
  panStartX = e.clientX
  panStartY = e.clientY
  panStartOffsetX = offsetX.value
  panStartOffsetY = offsetY.value
  canvasWrapper.value.style.cursor = 'grabbing'
}

const handleWheel = (e) => {
  e.preventDefault()
  if (isConnecting.value) return
  const rate = e.deltaY > 0 ? 0.9 : 1.1
  const newZoom = Math.min(2, Math.max(0.5, zoom.value * rate))
  const r = canvasWrapper.value.getBoundingClientRect()
  const mx = e.clientX - r.left
  const my = e.clientY - r.top
  const zr = newZoom / zoom.value
  offsetX.value = mx - (mx - offsetX.value) * zr
  offsetY.value = my - (my - offsetY.value) * zr
  zoom.value = newZoom
}

const getNodeIcon = (t) => ({
  START: '▶', END: '●', LLM: '🤖', RAG: '📚',
  FUNCTION: '⚙️', AGENT: '👤', CONDITION: '🔀', CODE: '</>'
}[t] || '📄')

const getNodeHeaderClass = (t) => ({
  START: 'header-start', END: 'header-end', LLM: 'header-llm', RAG: 'header-rag',
  FUNCTION: 'header-function', AGENT: 'header-agent', CONDITION: 'header-condition', CODE: 'header-code'
}[t] || 'header-default')

const getNodePreview = (node) => {
  const c = node.config || {}
  switch (node.type) {
    case 'LLM':
      // 优先显示用户提示词，如果没有则显示系统提示词
      if (c.userPrompt) {
        return c.userPrompt?.substring(0, 50) || '未配置提示词'
      }
      if (c.systemPrompt) {
        return '[系统]' + (c.systemPrompt?.substring(0, 40) || '未配置系统提示词')
      }
      return '未配置提示词'
    case 'RAG':
      return c.query || '未配置查询'
    case 'FUNCTION':
      return c.functionName || '未选择函数'
    case 'AGENT':
      return c.agentId ? `Agent ID: ${c.agentId}` : '未选择Agent'
    case 'CONDITION':
      return c.expression || '未配置条件'
    case 'CODE':
      return c.language || 'JavaScript'
    default:
      return node.name
  }
}

const getDefaultConfig = (t) => {
  console.log('getDefaultConfig called for type:', t)

  if (t === 'START') {
    const config = {
      inputVariables: [
        { name: 'query', description: '用户输入的问题', defaultValue: '' }
      ]
    }
    console.log('返回开始节点配置:', config)
    return config
  }

  if (t === 'LLM') {
    // 修改：添加 systemPrompt 和 userPrompt 字段
    return {
      systemPrompt: '',           // 系统提示词
      userPrompt: '',             // 用户提示词
      modelConfigId: null,        // 模型配置ID
      temperature: 0.7,           // 温度参数
      outputVar: 'llm_response'   // 输出变量名
    }
  }

  if (t === 'RAG') {
    return {
      query: '{{input.query}}',
      topK: 3,
      outputVar: 'rag_documents',
      kbName: '' // <--- 加这一行
    }
  }

  if (t === 'FUNCTION') {
    return {
      functionName: '',
      parameters: {},
      outputVar: 'function_result'
    }
  }

  if (t === 'AGENT') {
    return {
      agentId: null,
      query: '{{input.query}}',
      outputVar: 'agent_result'
    }
  }

  if (t === 'CONDITION') {
    return {
      expression: '',
      outputVar: 'condition_result'
    }
  }

  if (t === 'CODE') {
    return {
      code: '// 在此编写代码',
      language: 'javascript',
      outputVar: 'code_result'
    }
  }

  if (t === 'END') {
    return {
      outputVariables: []
    }
  }

  return {}
}

const selectNode = (node) => {
  if (isConnecting.value) return
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  currentNode.value = node
  nodeConfigDrawer.value = true
}

const selectEdge = (edge) => {
  ElMessageBox.confirm('删除此连线？', '确认', { type: 'warning' })
      .then(() => {
        edges.value = edges.value.filter(x => x.id !== edge.id)
        selectedEdgeId.value = null
        ElMessage.success('已删除')
      }).catch(() => {})
}

const deleteNode = (id) => {
  ElMessageBox.confirm('删除节点？', '确认', { type: 'warning' })
      .then(() => {
        nodes.value = nodes.value.filter(x => x.id !== id)
        edges.value = edges.value.filter(x => x.source !== id && x.target !== id)
        if (selectedNodeId.value === id) {
          selectedNodeId.value = null
          nodeConfigDrawer.value = false
        }
        ElMessage.success('已删除')
      }).catch(() => {})
}

const updateNodeConfig = (cfg) => {
  if (!currentNode.value) return

  const i = nodes.value.findIndex(x => x.id === currentNode.value.id)
  if (i !== -1) {
    // 合并配置，保留已有字段
    nodes.value[i].config = { ...nodes.value[i].config, ...cfg }
    if (cfg.name) {
      nodes.value[i].name = cfg.name
    }
    currentNode.value = nodes.value[i]
  }
}

const onDragStart = (e, t) => e.dataTransfer.setData('application/json', JSON.stringify(t))

const onCanvasDrop = (e) => {
  const data = e.dataTransfer.getData('application/json')
  if (!data) return
  const t = JSON.parse(data)
  const r = canvasWrapper.value.getBoundingClientRect()
  const x = (e.clientX - r.left - offsetX.value) / zoom.value
  const y = (e.clientY - r.top - offsetY.value) / zoom.value

  const defaultConfig = getDefaultConfig(t.type)
  console.log('添加节点:', t.type, '默认配置:', defaultConfig)

  const newNode = {
    id: `node_${Date.now()}_${nodeIdCounter++}`,
    type: t.type,
    name: `${t.label}_${nodes.value.length + 1}`,
    x: Math.max(0, Math.min(x, canvasWidth.value - 200)),
    y: Math.max(0, Math.min(y, canvasHeight.value - 80)),
    config: defaultConfig
  }
  nodes.value.push(newNode)
  collectInputVariables()
  ElMessage.success(`已添加：${t.label}`)
}

const collectInputVariables = () => {
  const startNode = nodes.value.find(n => n.type === 'START')
  console.log('collectInputVariables - 开始节点:', startNode)

  if (startNode && startNode.config && startNode.config.inputVariables) {
    inputVariables.value = startNode.config.inputVariables
    console.log('从开始节点获取输入变量:', inputVariables.value)
  } else {
    inputVariables.value = [{ name: 'query', description: '用户输入的问题', defaultValue: '' }]
    console.log('使用默认输入变量:', inputVariables.value)
  }
}

// 修改保存工作流函数，支持自定义名称
const saveWorkflow = async () => {
  // 如果是新建工作流且没有名称，先弹出对话框
  const isNew = !route.params.id || route.params.id === 'new'

  if (isNew && (!workflowName.value || !workflowName.value.trim())) {
    try {
      const { value: name } = await ElMessageBox.prompt('请输入工作流名称', '保存工作流', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：智能问答工作流'
      })

      if (name && name.trim()) {
        workflowName.value = name.trim()

        // 可选：输入描述
        const { value: desc } = await ElMessageBox.prompt('请输入工作流描述（可选）', '工作流描述', {
          confirmButtonText: '确定',
          cancelButtonText: '跳过',
          inputValue: ''
        })
        workflowDescription.value = desc || ''
      } else {
        return
      }
    } catch (err) {
      return
    }
  }

  saving.value = true
  try {
    const graph = {
      nodes: nodes.value.map(n => ({
        id: n.id, type: n.type, name: n.name, config: n.config,
        position: { x: n.x, y: n.y }
      })),
      edges: edges.value.map(e => ({ id: e.id, source: e.source, target: e.target }))
    }

    const data = {
      name: workflowName.value,
      description: workflowDescription.value,
      appId: null,
      graph: graph
    }

    let res
    if (route.params.id && route.params.id !== 'new') {
      res = await updateWorkflow(route.params.id, data)
    } else {
      res = await createWorkflow(data)
    }

    if (res.code === 200) {
      ElMessage.success('保存成功')
      if (isNew && res.data?.id) {
        router.replace(`/workflow/editor/${res.data.id}`)
      }
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (err) {
    console.error('保存失败:', err)
    ElMessage.error('错误：' + err.message)
  } finally {
    saving.value = false
  }
}

const doPublishWorkflow = async () => {
  if (!route.params.id || route.params.id !== 'new') {
    ElMessage.warning('请先保存工作流')
    return
  }
  publishing.value = true
  try {
    const res = await publishWorkflow(route.params.id)
    if (res.code === 200) {
      ElMessage.success('发布成功')
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch (err) {
    ElMessage.error('错误：' + err.message)
  } finally {
    publishing.value = false
  }
}

const executeWorkflow = async () => {
  if (!route.params.id || route.params.id === 'new') {
    ElMessage.warning('请先保存工作流')
    return
  }

  const startNode = nodes.value.find(n => n.type === 'START')
  let inputVars = startNode?.config?.inputVariables || []

  if (inputVars.length === 0) {
    inputVars = [{ name: 'query', description: '用户输入的问题', defaultValue: '' }]
  }

  if (inputVars.length === 1 && inputVars[0].name === 'query') {
    try {
      const { value } = await ElMessageBox.prompt('请输入查询内容', '工作流输入', {
        confirmButtonText: '执行',
        cancelButtonText: '取消',
        inputPlaceholder: inputVars[0].description || '请输入问题',
        inputValue: inputVars[0].defaultValue || ''
      })

      if (value) {
        await doExecute({ query: value })
      } else {
        ElMessage.warning('请输入查询内容')
      }
    } catch (err) {
      console.log('用户取消执行')
    }
    return
  }
}

const doExecute = async (customInputs) => {
  executing.value = true
  try {
    const userStr = localStorage.getItem('user')
    let userId = 'anonymous'
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.id || user.userId || 'anonymous'
      } catch (e) {}
    }

    const inputs = {
      ...customInputs,
      userId: userId,
      sessionId: `exec_${Date.now()}`
    }

    console.log('========== 执行工作流 ==========')
    console.log('工作流ID:', route.params.id)
    console.log('输入参数 inputs:', inputs)

    const res = await executeWorkflowApi({
      workflowId: parseInt(route.params.id),
      sessionId: inputs.sessionId,
      inputs: inputs
    })

    if (res.code === 200 && res.data) {
      executionResult.value = JSON.stringify(res.data.output, null, 2)
      executionNodeResults.value = res.data.nodeResults || []
      executionDialogVisible.value = true
      ElMessage.success('执行完成')
      saveLastExecutionResult()
    } else {
      ElMessage.error(res.msg || '执行失败')
    }
  } catch (err) {
    console.error('执行错误:', err)
    ElMessage.error('错误：' + err.message)
  } finally {
    executing.value = false
  }
}

const runTestWithInput = async () => {
  if (!route.params.id || route.params.id === 'new') {
    ElMessage.warning('请先保存工作流')
    return
  }

  if (!testInput.value) {
    ElMessage.warning('请输入测试内容')
    return
  }

  testing.value = true
  try {
    const userStr = localStorage.getItem('user')
    let userId = 'anonymous'
    if (userStr) {
      try {
        const user = JSON.parse(userStr)
        userId = user.id || user.userId || 'anonymous'
      } catch (e) {}
    }

    const inputs = {
      query: testInput.value,
      userId: userId,
      sessionId: `test_${Date.now()}`
    }

    const res = await executeWorkflowApi({
      workflowId: parseInt(route.params.id),
      sessionId: inputs.sessionId,
      inputs: inputs
    })

    if (res.code === 200 && res.data) {
      executionResult.value = JSON.stringify(res.data.output, null, 2)
      executionNodeResults.value = res.data.nodeResults || []
      executionDialogVisible.value = true
      ElMessage.success('测试运行完成')
    } else {
      ElMessage.error(res.msg || '测试运行失败')
    }
  } catch (err) {
    ElMessage.error('错误：' + err.message)
  } finally {
    testing.value = false
  }
}

const clearCanvas = () => {
  ElMessageBox.confirm('清空画布？', '确认', { type: 'warning' })
      .then(() => {
        nodes.value = []
        edges.value = []
        ElMessage.success('已清空')
      }).catch(() => {})
}

const loadWorkflow = async () => {
  if (!route.params.id || route.params.id === 'new') {
    const startConfig = getDefaultConfig('START')
    nodes.value = [{
      id: 'start_1',
      type: 'START',
      name: '开始',
      x: 100,
      y: 300,
      config: startConfig
    }]
    workflowName.value = ''
    workflowDescription.value = ''
    collectInputVariables()
    return
  }

  try {
    const res = await getWorkflowDetail(route.params.id)
    if (res.code === 200 && res.data) {
      workflowName.value = res.data.name || ''
      workflowDescription.value = res.data.description || ''

      if (res.data.graph) {
        const g = res.data.graph
        nodes.value = (g.nodes || []).map(n => {
          let config = n.config

          // 修复开始节点配置
          if (n.type === 'START') {
            if (!config || !config.inputVariables || config.prompt !== undefined) {
              config = getDefaultConfig('START')
            }
          }

          // 修复 LLM 节点配置（兼容旧数据）
          if (n.type === 'LLM') {
            if (config) {
              // 如果旧数据有 prompt 字段，迁移到 userPrompt
              if (config.prompt && !config.userPrompt) {
                config.userPrompt = config.prompt
                delete config.prompt
              }
              // 确保 systemPrompt 和 userPrompt 字段存在
              if (config.systemPrompt === undefined) config.systemPrompt = ''
              if (config.userPrompt === undefined) config.userPrompt = ''
              if (config.modelConfigId === undefined) config.modelConfigId = null
              if (config.temperature === undefined) config.temperature = 0.7
              if (config.outputVar === undefined) config.outputVar = 'llm_response'
            } else {
              config = getDefaultConfig('LLM')
            }
          }

          return {
            id: n.id, type: n.type, name: n.name,
            x: n.position?.x || 100, y: n.position?.y || 100,
            config: config || getDefaultConfig(n.type)
          }
        })
        edges.value = g.edges || []
        collectInputVariables()
      }
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('加载工作流失败')
  }
}

const initCanvasSize = () => {
  if (canvasWrapper.value) {
    canvasWidth.value = canvasWrapper.value.clientWidth
    canvasHeight.value = canvasWrapper.value.clientHeight
  }
}

onMounted(() => {
  initCanvasSize()
  loadWorkflow()
  loadLastExecutionResult()
  window.addEventListener('resize', initCanvasSize)
  const c = canvasWrapper.value
  if (c) {
    c.addEventListener('dragover', e => e.preventDefault())
    c.addEventListener('drop', onCanvasDrop)
    c.addEventListener('mousedown', onCanvasMouseDown)
    c.addEventListener('wheel', handleWheel)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', initCanvasSize)
  const c = canvasWrapper.value
  if (c) {
    c.removeEventListener('dragover', e => e.preventDefault())
    c.removeEventListener('drop', onCanvasDrop)
    c.removeEventListener('mousedown', onCanvasMouseDown)
    c.removeEventListener('wheel', handleWheel)
  }
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})
</script>

<style scoped>
.workflow-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0a0e27;
}

/* ========== 工作流头部 ========== */
.workflow-header {
  padding: 12px 24px;
  background: linear-gradient(135deg, #1a1f3a 0%, #0f1228 100%);
  border-bottom: 1px solid #2a2f4a;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;
}

.workflow-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.workflow-name-wrapper {
  display: flex;
  align-items: center;
}

.workflow-name {
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 8px;
  transition: all 0.2s;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.05);
}

.workflow-name:hover {
  background: rgba(102, 126, 234, 0.2);
}

.workflow-name:hover .edit-icon {
  opacity: 1;
}

.edit-icon {
  opacity: 0.6;
  font-size: 14px;
  transition: opacity 0.2s;
  color: #a78bfa;
}

.workflow-id {
  font-size: 12px;
  opacity: 0.7;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  color: #94a3b8;
  background: rgba(0, 0, 0, 0.3);
  padding: 4px 10px;
  border-radius: 6px;
}

.workflow-actions {
  display: flex;
  gap: 8px;
}

.workflow-actions .el-button {
  background: rgba(102, 126, 234, 0.15) !important;
  border: 1px solid rgba(102, 126, 234, 0.3) !important;
  color: #a78bfa !important;
}

.workflow-actions .el-button:hover {
  background: rgba(102, 126, 234, 0.3) !important;
  transform: translateY(-1px);
}

/* ========== 编辑工具栏 ========== */
.editor-toolbar {
  padding: 12px 20px;
  border-bottom: 1px solid #2a2f4a;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #0f1228;
  flex-shrink: 0;
}

.editor-toolbar .el-button-group .el-button {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  color: #cbd5e6 !important;
}

.editor-toolbar .el-button-group .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  color: #ffffff !important;
}

.editor-toolbar .el-button-group .el-button--success {
  background: linear-gradient(135deg, #10b981, #059669) !important;
  border: none !important;
  color: #ffffff !important;
}

.editor-toolbar .el-button-group .el-button--warning {
  background: linear-gradient(135deg, #f59e0b, #d97706) !important;
  border: none !important;
  color: #ffffff !important;
}

.editor-toolbar .el-button-group .el-button:hover {
  transform: translateY(-1px);
}

/* 节点面板 */
.node-palette {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 10px;
  cursor: move;
  transition: all 0.2s;
  color: #cbd5e6;
  font-size: 13px;
}

.palette-item:hover {
  background: #22284a;
  border-color: #667eea;
  transform: translateY(-2px);
}

.palette-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

/* ========== 测试面板 ========== */
.test-panel {
  padding: 8px 20px;
  background: #0f1228;
  border-bottom: 1px solid #2a2f4a;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.test-label {
  font-size: 13px;
  color: #94a3b8;
  font-weight: 500;
}

.test-panel .el-input :deep(.el-input__wrapper) {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 8px;
}

.test-panel .el-input :deep(.el-input__inner) {
  color: #ffffff;
}

.test-panel .el-button--primary {
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
}

/* ========== 画布区域 ========== */
.canvas-wrapper {
  flex: 1;
  position: relative;
  background: #0a0e27;
  overflow: auto;
  cursor: grab;
}

.canvas-wrapper:active {
  cursor: grabbing;
}

/* 节点样式 */
.workflow-node {
  position: absolute;
  width: 220px;
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  cursor: move;
  z-index: 10;
  user-select: none;
  transition: box-shadow 0.2s, transform 0.1s;
}

.workflow-node:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.3);
}

.workflow-node.selected {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.4);
}

.workflow-node.drag-over {
  border-color: #667eea;
  box-shadow: 0 0 0 2px #667eea, 0 0 0 4px rgba(102, 126, 234, 0.2);
  transform: scale(1.01);
}

.node-header {
  padding: 10px 14px;
  border-radius: 11px 11px 0 0;
  display: flex;
  align-items: center;
  gap: 8px;
  color: white;
}

.node-title {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
}

.delete-icon {
  cursor: pointer;
  opacity: 0.6;
  transition: all 0.2s;
  color: #ffffff;
}

.delete-icon:hover {
  opacity: 1;
  color: #f87171;
  transform: scale(1.1);
}

/* 节点头部颜色 */
.header-start { background: linear-gradient(135deg, #10b981, #059669); }
.header-end { background: linear-gradient(135deg, #ef4444, #dc2626); }
.header-llm { background: linear-gradient(135deg, #667eea, #764ba2); }
.header-rag { background: linear-gradient(135deg, #f59e0b, #d97706); }
.header-function { background: linear-gradient(135deg, #6b7280, #4b5563); }
.header-agent { background: linear-gradient(135deg, #3b82f6, #2563eb); }
.header-condition { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
.header-code { background: linear-gradient(135deg, #ec4899, #be185d); }
.header-default { background: linear-gradient(135deg, #64748b, #475569); }

.node-content {
  padding: 10px 14px;
  font-size: 12px;
  color: #cbd5e6;
}

.node-preview {
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #94a3b8;
}

/* 连接锚点 */
.anchor {
  position: absolute;
  width: 14px;
  height: 14px;
  background: #667eea;
  border: 2px solid #1a1f3a;
  border-radius: 50%;
  cursor: crosshair;
  z-index: 20;
  transition: all 0.2s;
  box-shadow: 0 0 4px rgba(0, 0, 0, 0.3);
}

.anchor:hover {
  transform: scale(1.3);
  background: #a78bfa;
  box-shadow: 0 0 6px rgba(102, 126, 234, 0.8);
}

.anchor-right {
  right: -7px;
  top: 50%;
  transform: translateY(-50%);
}

.anchor-left {
  left: -7px;
  top: 50%;
  transform: translateY(-50%);
}

/* SVG 连线层 */
.edges-canvas {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 5;
}

.edge {
  pointer-events: stroke;
  cursor: pointer;
  transition: stroke 0.2s, stroke-width 0.2s;
}

.edge:hover {
  stroke: #a78bfa !important;
  stroke-width: 3;
}

.edge-selected {
  stroke: #667eea !important;
  stroke-width: 3;
  filter: drop-shadow(0 0 4px rgba(102, 126, 234, 0.5));
}

/* ========== 执行结果对话框 ========== */
.execution-output {
  background: #0f1228;
  color: #cbd5e6;
  padding: 16px;
  border-radius: 10px;
  max-height: 400px;
  overflow: auto;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  border: 1px solid #2a2f4a;
}

/* ========== 变量面板 ========== */
.variable-panel {
  padding: 20px;
}

.variable-usage {
  margin-top: 20px;
}

.usage-tips {
  font-size: 13px;
  color: #cbd5e6;
}

.usage-tips code {
  background: #0f1228;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: #a78bfa;
  border: 1px solid #2a2f4a;
}

.usage-tips ul {
  margin: 8px 0;
  padding-left: 20px;
}

.usage-tips li {
  margin: 4px 0;
  color: #94a3b8;
}

.node-vars-list {
  max-height: 400px;
  overflow-y: auto;
}

.node-var-item {
  padding: 12px;
  border-bottom: 1px solid #2a2f4a;
}

.node-name {
  font-weight: 600;
  margin-bottom: 8px;
  color: #ffffff;
}

.node-outputs {
  padding-left: 12px;
}

.var-desc {
  font-size: 12px;
  color: #64748b;
  margin-left: 8px;
}

/* ========== 节点详情样式 ========== */
.node-header-detail {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-detail {
  font-size: 12px;
}

.detail-section {
  margin-bottom: 12px;
}

.detail-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #a78bfa;
}

.detail-section pre {
  background: #0f1228;
  padding: 10px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0;
  color: #cbd5e6;
  border: 1px solid #2a2f4a;
  font-size: 11px;
}

.detail-section.error .detail-title {
  color: #f87171;
}

/* ========== Element Plus 组件覆盖 ========== */
:deep(.el-drawer__header) {
  background: #0f1228;
  border-bottom: 1px solid #2a2f4a;
  color: #ffffff;
  padding: 16px 20px;
  margin: 0;
}

:deep(.el-drawer__body) {
  background: #0a0e27;
  padding: 0;
}

:deep(.el-dialog) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 16px !important;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-dialog__title) {
  color: #ffffff !important;
  font-weight: 600;
}

:deep(.el-dialog__body) {
  padding: 20px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid #2a2f4a;
  padding: 16px 20px;
}

:deep(.el-tabs__header) {
  background: transparent;
  border-bottom: 1px solid #2a2f4a;
}

:deep(.el-tabs__item) {
  color: #94a3b8;
}

:deep(.el-tabs__item.is-active) {
  color: #a78bfa;
}

:deep(.el-timeline-item__timestamp) {
  color: #64748b;
}

:deep(.el-timeline-item__content) {
  color: #cbd5e6;
}

:deep(.el-card) {
  background: #1a1f3a !important;
  border: 1px solid #2a2f4a !important;
}

:deep(.el-card__header) {
  border-bottom: 1px solid #2a2f4a;
  color: #ffffff;
}

/* ========== 响应式 ========== */
@media screen and (max-width: 768px) {
  .editor-toolbar {
    flex-direction: column;
    gap: 12px;
  }

  .node-palette {
    flex-wrap: wrap;
    justify-content: center;
  }

  .workflow-header {
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
  }

  .test-panel {
    flex-wrap: wrap;
  }

  .workflow-node {
    width: 180px;
  }
}

/* ========== 执行结果对话框优化 ========== */
.execution-dialog :deep(.el-dialog__body) {
  padding: 20px;
  max-height: 70vh;
  overflow-y: auto;
}

.final-output {
  background: #0f1228;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  padding: 16px;
}

.execution-output {
  background: transparent;
  color: #cbd5e6;
  padding: 0;
  margin: 0;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 节点详情列表 */
.node-details-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.node-detail-card {
  background: #1a1f3a;
  border: 1px solid #2a2f4a;
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.2s;
}

.node-detail-card.node-success {
  border-left: 4px solid #10b981;
}

.node-detail-card.node-error {
  border-left: 4px solid #ef4444;
}

.node-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #0f1228;
  border-bottom: 1px solid #2a2f4a;
}

.node-title-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-icon {
  font-size: 18px;
}

.node-name {
  font-weight: 600;
  font-size: 14px;
  color: #ffffff;
}

.node-meta {
  display: flex;
  gap: 12px;
}

.node-time {
  font-size: 12px;
  color: #64748b;
}

/* 节点详情区域 */
.node-detail-section {
  padding: 12px 16px;
  border-bottom: 1px solid #2a2f4a;
}

.node-detail-section:last-child {
  border-bottom: none;
}

.node-detail-section .section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 13px;
  color: #a78bfa;
}

.node-detail-section .section-header .el-icon {
  font-size: 16px;
}

.node-detail-section .section-content {
  background: #0f1228;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
}

.node-detail-section .section-content pre {
  margin: 0;
  font-family: 'SF Mono', Monaco, 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #cbd5e6;
  white-space: pre-wrap;
  word-break: break-word;
}

.node-detail-section.error .section-header {
  color: #f87171;
}

.node-detail-section.error .section-content {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.node-detail-section.error pre {
  color: #f87171;
}

/* 滚动条优化 */
.node-detail-section .section-content::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

.node-detail-section .section-content::-webkit-scrollbar-track {
  background: #0f1228;
}

.node-detail-section .section-content::-webkit-scrollbar-thumb {
  background: #2a2f4a;
  border-radius: 2px;
}

.history-panel {
  padding: 16px;
}
.history-item {
  padding: 12px;
  border-bottom: 1px solid #2a2f4a;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.history-header {
  display: flex;
  justify-content: space-between;
  width: 100%;
  color: #fff;
}
</style>