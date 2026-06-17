<template>
  <div class="workflow-editor">
    <!-- 头部 -->
    <div class="workflow-header">
      <div class="workflow-info">
        <div class="workflow-name-wrapper">
          <span class="workflow-name">{{ workflowName || '未命名工作流' }}</span>
          <span v-if="workflowDescription" class="workflow-desc">- {{ workflowDescription }}</span>
        </div>
      </div>
      <div class="workflow-actions">
        <el-tooltip content="工作流信息" placement="bottom">
          <el-button size="small" @click="showWorkflowInfo" plain circle>
            <el-icon>
              <InfoFilled/>
            </el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-actions">
        <el-button-group>
          <el-button type="primary" @click="saveWorkflow" :loading="saving">
            <el-icon><DocumentAdd/></el-icon> 保存
          </el-button>
          <el-button type="success" @click="doPublishWorkflow" :loading="publishing">
            <el-icon><Upload/></el-icon> 发布
          </el-button>
          <el-button type="warning" @click="executeWorkflow" :loading="executing">
            <el-icon><VideoPlay/></el-icon> 执行
          </el-button>
          <el-button @click="variableDrawerVisible = true">
            <el-icon><List/></el-icon> 变量
          </el-button>
          <el-button @click="showLastExecutionResult">
            <el-icon><Clock/></el-icon> 历史
          </el-button>
        </el-button-group>
      </div>
    </div>

    <!-- Vue Flow 画布区域 -->
    <div class="flow-container-row">
      <!-- 左侧节点侧边栏 -->
      <div class="sidebar-node-list">
        <div class="sidebar-title">节点组件</div>
        <div class="sidebar-group" v-for="group in nodeTypeGroups" :key="group.label">
          <div class="sidebar-group-title">{{ group.label }}</div>
          <div class="sidebar-node-items">
            <div
                v-for="item in group.list"
                :key="item.type"
                class="sidebar-node-item"
                draggable="true"
                @dragstart="handleDragStart(item)"
            >
              <span class="menu-icon" :style="{ background: item.color }">{{ item.icon }}</span>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </div>
      </div>
      <!-- Vue Flow 画布 -->
      <div class="flow-wrapper">
        <VueFlow
            v-model:nodes="nodes"
            v-model:edges="edges"
            :node-types="nodeTypes"
            :edge-types="edgeTypes"
            :default-viewport="{ zoom: 1, x: 0, y: 0 }"
            :min-zoom="0.2"
            :max-zoom="4"
            :snap-to-grid="true"
            :snap-grid="[20,20]"
            :is-valid-connection="isValidConnection"
            :edges-updatable="true"
            @node-click="onNodeClick"
            @drop="onCanvasDrop"
            @dragover="onDragOver"
            @pane-click="deselectAll"
            @connect="onConnect"
            @pane-mouse-move="onPaneMouseMove"
            @pane-ready="onPaneReady"
            @edge-update-end="handleEdgeUpdateEnd"
            @contextmenu="onCanvasRightClick"
        >
          <Background :pattern-color="'#2a2f4a'" :gap="20"/>
          <Controls/>
        </VueFlow>

        <!-- 弹出节点菜单 -->
        <div
            v-if="showNodeMenu"
            class="node-add-menu"
            :style="{ left: menuPosition.x + 'px', top: menuPosition.y + 'px' }"
            @click.stop
            @mousedown.stop
        >
          <div class="menu-group" v-for="group in nodeTypeGroups" :key="group.label">
            <div class="group-title">{{ group.label }}</div>
            <div class="group-items">
              <div
                  v-for="item in group.list"
                  :key="item.type"
                  class="menu-item"
                  @click="selectNodeType(item.type)"
              >
                <span class="menu-icon" :style="{ background: item.color }">{{ item.icon }}</span>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 节点配置抽屉 -->
    <el-drawer v-model="nodeConfigDrawer" :title="'配置节点: ' + (currentNode ? currentNode.name : '')" size="40%">
      <NodeConfigPanel
          v-if="currentNode"
          :node="currentNode"
          :nodes="nodes"
          :edges="edges"
          @update="updateNodeConfig"
          @close="nodeConfigDrawer = false"
      />
    </el-drawer>

    <!-- 变量面板抽屉 -->
    <el-drawer v-model="variableDrawerVisible" title="工作流变量" size="30%" direction="rtl">
      <div class="variable-panel">
        <el-tabs>
          <el-tab-pane label="输入变量">
            <el-alert type="info" :closable="false">
              <template #title>默认输入变量：query</template>
              测试运行时，输入的文本会自动赋值给 <code>input.query</code>
            </el-alert>
            <el-table :data="inputVarList" size="small" border>
              <el-table-column prop="name" label="变量名"/>
              <el-table-column prop="description" label="说明"/>
              <el-table-column prop="example" label="使用方式"/>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="节点输出变量">
            <div class="node-vars-list">
              <div v-for="node in nodes" :key="node.id" class="node-var-item">
                <div class="node-name">{{ node.data.name }} ({{ node.type }})</div>
                <div class="node-outputs">
                  <el-tag v-if="node.data.config?.outputVar" size="small" type="success">
                    var.{{ node.data.config.outputVar }}
                  </el-tag>
                  <el-tag v-else size="small" type="info">未设置输出变量</el-tag>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
        <div class="variable-usage">
          <el-divider>变量使用说明</el-divider>
          <div class="usage-tips">
            <p><strong>输入变量：</strong></p>
            <ul>
              <li><code v-pre>{{input.query}}</code> - 用户在测试面板输入的内容</li>
            </ul>
            <p><strong>节点输出变量：</strong></p>
            <ul>
              <li>其他节点可通过 <code v-pre>{{var.变量名}}</code> 引用</li>
            </ul>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 执行结果对话框 -->
    <el-dialog v-model="executionDialogVisible" title="工作流执行结果" width="70%" class="execution-dialog">
      <el-tabs>
        <el-tab-pane label="最终输出">
          <div class="final-output">
            <pre class="execution-output">{{ executionResult }}</pre>
          </div>
        </el-tab-pane>
        <el-tab-pane label="节点执行详情">
          <div class="node-details-list">
            <div v-for="node in executionNodeResults" :key="node.nodeId" class="node-detail-card"
                 :class="{ 'node-success': node.status === 'SUCCESS', 'node-error': node.status !== 'SUCCESS' }">
              <div class="node-detail-header">
                <div class="node-title-info">
                  <span class="node-icon">{{ getNodeIconByType(node.nodeType) }}</span>
                  <span class="node-name">{{ node.nodeName }}</span>
                  <el-tag :type="node.status === 'SUCCESS' ? 'success' : 'danger'" size="small" effect="dark">
                    {{ node.status === 'SUCCESS' ? '成功' : '失败' }}
                  </el-tag>
                </div>
                <span class="node-time">⏱️ 耗时: {{ node.costTime }}ms</span>
              </div>
              <div v-if="node.input && node.nodeType !== 'END'" class="node-detail-section">
                <div class="section-header">
                  <el-icon><Upload/></el-icon>
                  <span>输入</span>
                </div>
                <div class="section-content">
                  <pre>{{ formatNodeInput(node.input, node.nodeType) }}</pre>
                </div>
              </div>
              <div v-if="node.output && node.nodeType !== 'START'" class="node-detail-section">
                <div class="section-header">
                  <el-icon><Download/></el-icon>
                  <span>输出</span>
                </div>
                <div class="section-content">
                  <pre>{{ formatNodeOutput(node.output, node.nodeType) }}</pre>
                </div>
              </div>
              <div v-if="node.errorMsg" class="node-detail-section error">
                <div class="section-header">
                  <el-icon><CircleClose/></el-icon>
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

    <!-- 保存对话框 -->
    <el-dialog
        v-model="showSaveDialog"
        title="保存工作流"
        width="450px"
        class="save-dialog"
        :modal="true"
        :close-on-click-modal="false"
        center
    >
      <el-form :model="saveForm" :rules="saveRules" ref="saveFormRef" label-width="100px">
        <el-form-item label="工作流名称" prop="name">
          <el-input v-model="saveForm.name" placeholder="请输入工作流名称" autofocus/>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="saveForm.description" type="textarea" :rows="3" placeholder="请输入工作流描述（可选）"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 信息查看对话框 -->
    <el-dialog v-model="infoDialogVisible" title="工作流信息" width="400px" class="info-dialog">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="工作流名称">{{ workflowName || '未命名' }}</el-descriptions-item>
        <el-descriptions-item label="工作流描述">{{ workflowDescription || '无' }}</el-descriptions-item>
        <el-descriptions-item v-if="route.params.id && route.params.id !== 'new'" label="工作流ID">
          {{ route.params.id }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="infoDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, provide, computed, watch, markRaw, unref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentAdd, Upload, VideoPlay, List, InfoFilled, Clock, Download, CircleClose } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'

import { workflowApi } from '@/api'
import NodeConfigPanel from '../../components/NodeConfigPanel/index.vue'
import CustomNode from './CustomNode.vue'
import AddButtonEdge from './AddButtonEdge.vue'

const route = useRoute()
const router = useRouter()

const {
  addNodes,
  removeNodes,
  removeEdges,
  addEdges,
  screenToFlowCoordinate,
  fitView,
  setViewport,
  viewport,
} = useVueFlow()

// -------------------- 节点图标映射（与侧边栏保持一致） --------------------
const getNodeIconByType = (type) => {
  const map = {
    START: '▶️',
    END: '●',
    LLM: '🤖',
    RAG: '📚',
    FUNCTION: '⚙️',
    AGENT: '👤',
    CONDITION: '🔀',
    CODE: '</>',
  }
  return map[type] || '📦'
}

// -------------------- 格式化节点输入（用于详情展示） --------------------
const formatNodeInput = (input, nodeType) => {
  if (!input) return '无输入'
  // 如果是字符串，直接展示
  if (typeof input === 'string') return input
  // 如果是对象，转 JSON 格式化
  try {
    return JSON.stringify(input, null, 2)
  } catch {
    return String(input)
  }
}

// -------------------- 格式化节点输出（用于详情展示） --------------------
const formatNodeOutput = (output, nodeType) => {
  if (!output) return '无输出'
  if (typeof output === 'string') return output
  try {
    return JSON.stringify(output, null, 2)
  } catch {
    return String(output)
  }
}

// -------------------- 鼠标位置记录（用于菜单定位） --------------------
const lastMouseX = ref(0)
const lastMouseY = ref(0)
const onPaneMouseMove = (event) => {
  const bounds = document.querySelector('.flow-wrapper')?.getBoundingClientRect()
  if (bounds) {
    lastMouseX.value = event.clientX - bounds.left
    lastMouseY.value = event.clientY - bounds.top
  }
}

// -------------------- 拖拽连线端点松开 --------------------
const handleEdgeUpdateEnd = async ({ edge, targetNode }) => {
  if (!targetNode) {
    await ElMessageBox.confirm('确定断开当前连线吗？', '操作确认', {
      type: 'warning',
      confirmButtonText: '断开',
      cancelButtonText: '取消',
    })
        .then(() => {
          removeEdges([edge.id])
          ElMessage.info('连线已断开')
        })
        .catch(() => {})
  }
}

// -------------------- 拖拽添加节点 --------------------
const handleDragStart = (item) => {
  const data = JSON.stringify({
    type: item.type,
    label: item.label,
  })
  event.dataTransfer.setData('application/json', data)
  event.dataTransfer.effectAllowed = 'move'
}

// -------------------- 坐标转换工具 --------------------
/** flow逻辑坐标 → flow-wrapper容器内相对坐标（紧贴加号用） */
const flowToWrapperRelative = (flowPoint) => {
  const vp = unref(viewport)
  const zoom = vp?.zoom || 1
  const panX = vp?.x || 0
  const panY = vp?.y || 0
  return {
    x: (flowPoint.x - panX) * zoom,
    y: (flowPoint.y - panY) * zoom,
  }
}

/** 容器内边界修正，保证菜单不超出 */
const fixWrapperBoundary = (pos, menuW = 180, menuH = 360) => {
  const wrapperDom = document.querySelector('.flow-wrapper')
  if (!wrapperDom) return pos
  const rect = wrapperDom.getBoundingClientRect()
  const padding = 8
  let x = pos.x + 16
  let y = pos.y - 20
  if (x + menuW > rect.width - padding) {
    x = pos.x - menuW - 16
  }
  if (y + menuH > rect.height - padding) {
    y = pos.y - menuH - 16
  }
  x = Math.max(padding, x)
  y = Math.max(padding, y)
  return { x, y }
}

// -------------------- 工作流基本信息 --------------------
const workflowName = ref('')
const workflowDescription = ref('')
const infoDialogVisible = ref(false)

// -------------------- 保存相关 --------------------
const showSaveDialog = ref(false)
const saveFormRef = ref(null)
const saving = ref(false)
const saveForm = ref({ name: '', description: '' })
const saveRules = {
  name: [
    { required: true, message: '请输入工作流名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度在 1 到 100 个字符', trigger: 'blur' },
  ],
}

// -------------------- Vue Flow 数据 --------------------
const nodes = ref([])
const edges = ref([])
const selectedNodeId = ref(null)
const publishing = ref(false)
const executing = ref(false)
const nodeConfigDrawer = ref(false)
const currentNode = ref(null)
const executionDialogVisible = ref(false)
const executionResult = ref('')
const executionNodeResults = ref([])
const variableDrawerVisible = ref(false)

let nodeIdCounter = 0

// -------------------- 节点类型映射 --------------------
const nodeTypes = {
  START: markRaw(CustomNode),
  END: markRaw(CustomNode),
  LLM: markRaw(CustomNode),
  RAG: markRaw(CustomNode),
  FUNCTION: markRaw(CustomNode),
  AGENT: markRaw(CustomNode),
  CONDITION: markRaw(CustomNode),
  CODE: markRaw(CustomNode),
}

// -------------------- 菜单相关 --------------------
const showNodeMenu = ref(false)
const menuPosition = ref({ x: 0, y: 0 })
const activeEdgeId = ref('')
const isInsertOnEdge = ref(false)
const edgeOriginSource = ref('')
const edgeOriginTarget = ref('')
const insertFlowPosition = ref({ x: 0, y: 0 })
const menuLock = ref(false)

const nodeTypeGroups = [
  {
    label: '基础输出',
    list: [{ type: 'END', label: '结果输出节点', icon: '●', color: '#f56c6c' }],
  },
  {
    label: '大模型能力',
    list: [
      { type: 'LLM', label: '大模型节点', icon: '🤖', color: '#409eff' },
      { type: 'AGENT', label: 'Agent节点', icon: '👤', color: '#67c23a' },
    ],
  },
  {
    label: '知识库工具',
    list: [
      { type: 'RAG', label: '知识库节点', icon: '📚', color: '#e6a23c' },
      { type: 'FUNCTION', label: '函数节点', icon: '⚙️', color: '#909399' },
    ],
  },
  {
    label: '逻辑控制',
    list: [
      { type: 'CONDITION', label: '条件分支节点', icon: '🔀', color: '#f56c6c' },
      { type: 'CODE', label: '代码执行节点', icon: '</>', color: '#909399' },
    ],
  },
]

// -------------------- 画布右键菜单 --------------------
const onCanvasRightClick = (e) => {
  e.preventDefault()
  isInsertOnEdge.value = false
  activeEdgeId.value = ''
  edgeOriginSource.value = ''
  edgeOriginTarget.value = ''
  const flowPos = screenToFlowCoordinate({ x: e.clientX, y: e.clientY })
  insertFlowPosition.value = flowPos

  const wrapperDom = document.querySelector('.flow-wrapper')
  const rect = wrapperDom.getBoundingClientRect()
  const mouseWrapperPos = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top,
  }
  const safePos = fixWrapperBoundary(mouseWrapperPos)
  menuPosition.value = safePos
  showNodeMenu.value = true
  menuLock.value = true
  setTimeout(() => (menuLock.value = false), 300)
}

// -------------------- 选择节点类型（添加或插入） --------------------
const selectNodeType = (type) => {
  const targetGroup = nodeTypeGroups.flatMap((g) => g.list).find((t) => t.type === type)
  if (!targetGroup) return

  if (type === 'START' && nodes.value.some((n) => n.type === 'START')) {
    ElMessage.warning('开始节点已存在，不能重复添加')
    showNodeMenu.value = false
    isInsertOnEdge.value = false
    return
  }

  // 连线中间插入
  if (isInsertOnEdge.value && edgeOriginSource.value && edgeOriginTarget.value) {
    const newNode = {
      id: `node_${Date.now()}_${nodeIdCounter++}`,
      type: type,
      position: insertFlowPosition.value,
      data: {
        name: type === 'END' ? `结果输出_${nodes.value.length + 1}` : `${targetGroup.label}_${nodes.value.length + 1}`,
        config: getDefaultConfig(type),
      },
    }
    removeEdges([activeEdgeId.value])
    addNodes([newNode])
    addEdges([
      { id: `edge_${edgeOriginSource.value}_${newNode.id}`, source: edgeOriginSource.value, target: newNode.id },
      { id: `edge_${newNode.id}_${edgeOriginTarget.value}`, source: newNode.id, target: edgeOriginTarget.value },
    ])
    ElMessage.success(`已插入 ${targetGroup.label}`)
  } else {
    // 空白处添加
    const newNode = {
      id: `node_${Date.now()}_${nodeIdCounter++}`,
      type: type,
      position: insertFlowPosition.value,
      data: {
        name: type === 'END' ? `结果输出_${nodes.value.length + 1}` : `${targetGroup.label}_${nodes.value.length + 1}`,
        config: getDefaultConfig(type),
      },
    }
    addNodes([newNode])
    ElMessage.success(`已添加 ${targetGroup.label}`)
  }

  showNodeMenu.value = false
  isInsertOnEdge.value = false
  activeEdgeId.value = ''
  edgeOriginSource.value = ''
  edgeOriginTarget.value = ''
  insertFlowPosition.value = { x: 0, y: 0 }
}

// -------------------- 取消选择 & 关闭菜单 --------------------
const deselectAll = () => {
  selectedNodeId.value = null
  if (!menuLock.value) {
    showNodeMenu.value = false
    isInsertOnEdge.value = false
    activeEdgeId.value = ''
  }
}

// -------------------- 连线校验 --------------------
const isValidConnection = (connection) => {
  if (connection.source === connection.target) return false
  return !edges.value.some((e) => e.source === connection.source && e.target === connection.target)
}

const onConnect = (connection) => {
  if (!connection.source || !connection.target) {
    ElMessage.warning('连接失败：缺少源节点或目标节点')
    return
  }
  addEdges([{ id: `edge_${Date.now()}_${Math.random()}`, source: connection.source, target: connection.target }])
  ElMessage.success('连线创建成功')
}

// -------------------- 节点默认配置 --------------------
const getDefaultConfig = (type) => {
  if (type === 'START') return { inputVariables: [{ name: 'query', description: '用户输入的问题', defaultValue: '' }] }
  if (type === 'LLM')
    return {
      systemPrompt: '',
      userPrompt: '',
      modelConfigId: null,
      temperature: 0.7,
      outputVar: 'llm_response',
    }
  if (type === 'RAG') return { query: '{{input.query}}', topK: 3, outputVar: 'rag_documents', kbName: '' }
  if (type === 'FUNCTION') return { functionName: '', parameters: {}, outputVar: 'function_result' }
  if (type === 'AGENT') return { agentId: null, query: '{{input.query}}', outputVar: 'agent_result' }
  if (type === 'CONDITION') return { expression: '', outputVar: 'condition_result' }
  if (type === 'CODE') return { code: '// 在此编写代码', language: 'javascript', outputVar: 'code_result' }
  if (type === 'END') return { outputVariables: [] }
  return {}
}

// -------------------- 变量相关 --------------------
// 输入变量列表（从 START 节点读取，无则返回默认）
const inputVarList = computed(() => {
  const startNode = nodes.value.find((n) => n.type === 'START')
  if (!startNode || !startNode.data.config?.inputVariables) {
    return [{ name: 'query', description: '用户输入的问题', example: '{{input.query}}', defaultValue: '' }]
  }
  return startNode.data.config.inputVariables.map((v) => ({
    ...v,
    example: `{{input.${v.name}}}`,
  }))
})

// 节点输出变量列表（计算属性）
const nodeOutputVars = computed(() => {
  return nodes.value.map((node) => ({
    nodeId: node.id,
    nodeName: node.data.name,
    nodeType: node.type,
    outputVar: node.data.config?.outputVar || null,
  }))
})

provide('inputVarList', inputVarList)
provide('nodeOutputVars', nodeOutputVars)
provide('edges', edges) // 注意：子组件可能修改，但暂时保留
provide('nodes', nodes)

// -------------------- 注入连线插入方法（供 AddButtonEdge 调用） --------------------
provide('onAddNodeOnEdge', async (params) => {
  const { sourceId, targetId, centerPoint } = params
  const edge = edges.value.find((e) => e.source === sourceId && e.target === targetId)
  if (!edge) return

  activeEdgeId.value = edge.id
  edgeOriginSource.value = sourceId
  edgeOriginTarget.value = targetId
  isInsertOnEdge.value = true
  insertFlowPosition.value = centerPoint

  await nextTick()
  const wrapperPos = flowToWrapperRelative(centerPoint)
  const safePos = fixWrapperBoundary(wrapperPos)
  menuPosition.value = safePos
  menuLock.value = true
  showNodeMenu.value = true
  setTimeout(() => (menuLock.value = false), 300)
})

// -------------------- 删除节点 --------------------
provide('deleteNode', (nodeId) => {
  const targetNode = nodes.value.find((n) => n.id === nodeId)
  if (targetNode && targetNode.type === 'START') {
    ElMessage.warning('开始节点不允许删除')
    return
  }
  ElMessageBox.confirm('确定删除该节点吗？', '提示', { type: 'warning' })
      .then(() => {
        removeNodes([nodeId])
        edges.value = edges.value.filter((e) => e.source !== nodeId && e.target !== nodeId)
        if (selectedNodeId.value === nodeId) {
          selectedNodeId.value = null
          nodeConfigDrawer.value = false
        }
        ElMessage.success('节点已删除')
      })
      .catch(() => {})
})

// -------------------- 拖拽添加节点到画布 --------------------
const onDragOver = (event) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
}

const onCanvasDrop = (event) => {
  const rawData = event.dataTransfer.getData('application/json')
  if (!rawData) return
  const nodeType = JSON.parse(rawData)

  if (nodeType.type === 'START' && nodes.value.some((n) => n.type === 'START')) {
    ElMessage.warning('画布中仅允许存在一个开始节点')
    return
  }
  const position = screenToFlowCoordinate({ x: event.clientX, y: event.clientY })
  const newNode = {
    id: `node_${Date.now()}_${nodeIdCounter++}`,
    type: nodeType.type,
    position,
    data: {
      name: nodeType.type === 'END' ? `结果输出_${nodes.value.length + 1}` : `${nodeType.label}_${nodes.value.length + 1}`,
      config: getDefaultConfig(nodeType.type),
    },
  }
  addNodes([newNode])
  ElMessage.success(`已添加：${nodeType.label}`)
}

// -------------------- Vue Flow 事件 --------------------
const onPaneReady = () => {
  fitView({ padding: 0.2, maxZoom: 1.5 })
}

const onNodeClick = ({ node }) => {
  selectedNodeId.value = node.id
  currentNode.value = { id: node.id, type: node.type, name: node.data.name, config: node.data.config }
  nodeConfigDrawer.value = true
}

const updateNodeConfig = (config) => {
  if (!currentNode.value) return
  const idx = nodes.value.findIndex(n => n.id === currentNode.value.id)
  if (idx !== -1) {
    const node = nodes.value[idx]
    node.data = {
      ...node.data,
      config: { ...node.data.config, ...config },
    }
    if (config.name) node.data.name = config.name
    // 同步 currentNode 以供配置面板和变量计算
    currentNode.value.config = node.data.config
    currentNode.value.name = node.data.name
    // 如果修改了输出变量相关配置，立即更新变量列表
    if (config.outputVar !== undefined || config.outputType !== undefined) {
      updateNodeOutputVars()
    }
  }
}

// -------------------- 边类型 --------------------
const edgeTypes = { default: markRaw(AddButtonEdge) }

// -------------------- 保存工作流 --------------------
const saveWorkflow = async () => {
  const isNew = !route.params.id || route.params.id === 'new'

  if (isNew && (!workflowName.value || !workflowName.value.trim())) {
    saveForm.value.name = ''
    saveForm.value.description = ''
    showSaveDialog.value = true
    await nextTick()
    saveFormRef.value?.clearValidate()
    return
  }

  saving.value = true
  try {
    const pureNodes = nodes.value.map((node) => ({
      id: node.id,
      type: node.type,
      name: node.data.name,
      config: node.data.config || {},
      position: {
        x: node.position.x,
        y: node.position.y,
      },
    }))

    const pureEdges = edges.value
        .filter((edge) => edge.id && edge.source && edge.target)
        .map((edge) => {
          const base = {
            id: edge.id,
            source: edge.source,
            target: edge.target,
          }
          if (edge.sourceHandle) base.sourceHandle = edge.sourceHandle
          if (edge.targetHandle) base.targetHandle = edge.targetHandle
          if (edge.condition) base.condition = edge.condition
          return base
        })

    const graph = { nodes: pureNodes, edges: pureEdges }
    const submitData = {
      name: workflowName.value,
      description: workflowDescription.value,
      appId: null,
      graph: graph,
    }
    console.log('提交后端完整graph', JSON.parse(JSON.stringify(graph)))

    let res
    if (route.params.id && route.params.id !== 'new') {
      res = await workflowApi.update(route.params.id, submitData)
    } else {
      res = await workflowApi.create(submitData)
    }

    if (res.code === 200) {
      workflowName.value = submitData.name
      workflowDescription.value = submitData.description
      ElMessage.success('保存成功')

      if (isNew && res.data?.id) {
        router.replace(`/workflow/editor/${res.data.id}`)
      }
      // 不再调用 loadWorkflow()
    }
  } catch (err) {
    console.error('保存异常', err)
    ElMessage.error('保存失败：' + err.message)
  } finally {
    saving.value = false
  }
}

// -------------------- 弹窗保存确认 --------------------
const confirmSave = async () => {
  await saveFormRef.value.validate((valid) => {
    if (!valid) return
    workflowName.value = saveForm.value.name
    workflowDescription.value = saveForm.value.description
    showSaveDialog.value = false
    saveWorkflow()
  })
}

// -------------------- 发布 / 执行 / 历史 （占位） --------------------
const doPublishWorkflow = async () => {
  if (!route.params.id || route.params.id === 'new') {
    ElMessage.warning('请先保存工作流再发布')
    return
  }
  publishing.value = true
  try {
    const res = await workflowApi.publish(route.params.id)
    if (res.code === 200) {
      ElMessage.success('发布成功，当前版本已上线')
    } else {
      ElMessage.error(res.msg || '发布失败')
    }
  } catch (err) {
    ElMessage.error('发布异常：' + err.message)
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
  let inputVars = startNode?.data?.config?.inputVariables || []
  if (inputVars.length === 0) {
    inputVars = [{ name: 'query', description: '用户输入的问题', defaultValue: '' }]
  }

  // 简单方案：只取第一个变量（假设为 query）
  const firstVar = inputVars[0]
  try {
    const { value } = await ElMessageBox.prompt(
        firstVar.description || '请输入内容',
        '工作流输入',
        {
          confirmButtonText: '执行',
          cancelButtonText: '取消',
          inputPlaceholder: firstVar.description || '请输入...',
          inputValue: firstVar.defaultValue || ''
        }
    )
    if (value !== null && value !== undefined) {
      const inputs = {}
      inputs[firstVar.name] = value
      // 如果有其他变量，可再扩展输入
      await doExecute(inputs)
    } else {
      ElMessage.warning('请输入内容')
    }
  } catch (err) {
    console.log('用户取消执行')
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

    console.log('执行参数：', { workflowId: route.params.id, inputs })
    const res = await workflowApi.execute({
      workflowId: parseInt(route.params.id),
      sessionId: inputs.sessionId,
      inputs: inputs
    })
    console.log('执行响应：', res)
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

const showWorkflowInfo = () => {
  infoDialogVisible.value = true
}

// -------------------- 加载工作流 --------------------
const loadWorkflow = async () => {
  const workflowId = route.params.id
  if (!workflowId || workflowId === 'new') {
    nodes.value = []
    edges.value = []
    await nextTick()
    addNodes([
      {
        id: 'start_1',
        type: 'START',
        position: { x: 200, y: 200 },
        data: { name: '开始', config: getDefaultConfig('START') },
      },
      {
        id: 'end_1',
        type: 'END',
        position: { x: 600, y: 200 },
        data: { name: '结果输出', config: getDefaultConfig('END') },
      },
    ])
    workflowName.value = ''
    workflowDescription.value = ''
    await nextTick()
    setViewport({ zoom: 1, x: 0, y: 0 })
    return
  }

  try {
    const res = await workflowApi.getDetail(workflowId)
    if (res.code === 200 && res.data) {
      workflowName.value = res.data.name || ''
      workflowDescription.value = res.data.description || ''
      const graph = res.data.graph
      if (graph) {
        console.log('后端返回graph数据', graph)

        nodes.value = []
        edges.value = []
        await nextTick()

        const mappedNodes = (graph.nodes || []).map((item) => ({
          id: item.id,
          type: item.type,
          position: { x: item.position?.x ?? 0, y: item.position?.y ?? 0 },
          data: { name: item.name, config: item.config || {} },
        }))
        addNodes(mappedNodes)

        const mappedEdges = (graph.edges || [])
            .filter((e) => e.id && e.source && e.target)
            .map((item) => ({
              id: item.id,
              source: item.source,
              target: item.target,
              sourceHandle: item.sourceHandle || null,
              targetHandle: item.targetHandle || null,
              condition: item.condition || null,
            }))
        addEdges(mappedEdges)
        console.log('加载后的边数量:', mappedEdges.length)

        await nextTick()
        fitView({ padding: 0.2, maxZoom: 1.5, duration: 300 })
      }
    }
  } catch (err) {
    ElMessage.error('加载失败')
    console.error(err)
  }
}

// -------------------- 生命周期 --------------------
onMounted(() => {
  loadWorkflow()
})
</script>

<style scoped lang="scss">
// 全局变量统一管理，方便后期换主题
$bg-primary: #0a0e27;
$bg-secondary: #0f1228;
$bg-card: #1a1f3a;
$bg-hover: #2a2f4a;
$border-color: #303759;
$text-primary: #ffffff;
$text-normal: #cbd5e6;
$text-muted: #94a3b8;
$text-tip: #7c87a3;
$brand-primary: #667eea;
$brand-gradient: linear-gradient(135deg, #667eea, #764ba2);
$success-gradient: linear-gradient(135deg, #10b981, #059669);
$warn-gradient: linear-gradient(135deg, #f59e0b, #d97706);
$danger-gradient: linear-gradient(135deg, #ef4444, #dc2626);
$radius-sm: 6px;
$radius-md: 8px;
$radius-lg: 12px;
$radius-xl: 20px;
$shadow-base: 0 4px 12px rgba(0, 0, 0, 0.35);
$shadow-heavy: 0 8px 28px rgba(0, 0, 0, 0.55);

/* ========== 页面根容器 ========== */
.workflow-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: $bg-primary;
  color: $text-normal;
  font-size: 14px;
  overflow: hidden;
}

/* 画布主体行 */
.flow-container-row {
  display: flex;
  flex: 1;
  min-height: 0;
  width: 100%;
}

/* VueFlow 画布容器 */
.flow-wrapper {
  position: relative;
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 450px;
  overflow: hidden;

  :deep(.vue-flow) {
    width: 100% !important;
    height: 100% !important;
  }

  // 缩放控制器美化
  :deep(.vue-flow__controls) {
    background: $bg-card !important;
    border: 1px solid $border-color !important;
    border-radius: $radius-md !important;
    box-shadow: $shadow-base !important;

    button {
      background: transparent !important;
      border: none !important;
      color: $text-normal !important;

      &:hover {
        background: $bg-hover !important;
      }
    }
  }

  // 背景网格美化
  :deep(.vue-flow__background) {
    opacity: 0.6;
  }
}

/* ========== 顶部头部栏 ========== */
.workflow-header {
  padding: 14px 28px;
  background: linear-gradient(135deg, #161b36 0%, $bg-secondary 100%);
  border-bottom: 1px solid $border-color;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .workflow-name-wrapper {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .workflow-name {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    background: rgba(255, 255, 255, 0.06);
    padding: 8px 14px;
    border-radius: $radius-md;
    letter-spacing: 0.5px;
    backdrop-filter: blur(4px);
    transition: background 0.24s ease;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
    }
  }

  .workflow-desc {
    font-size: 13px;
    color: $text-muted;
    line-height: 1.4;
  }

  .workflow-actions {
    .el-button {
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid $border-color;
      color: $text-normal;
      transition: all 0.2s ease;

      &:hover {
        background: $bg-hover;
        border-color: $brand-primary;
      }
    }
  }
}

/* ========== 工具栏 ========== */
.editor-toolbar {
  flex-shrink: 0;
  background: $bg-secondary;
  border-bottom: 1px solid $border-color;

  .toolbar-actions {
    padding: 12px 28px;
    display: flex;
    align-items: center;
    gap: 16px;

    .el-button-group {
      display: flex;
      gap: 2px;

      .el-button {
        background: $bg-card !important;
        border: 1px solid $border-color !important;
        color: $text-normal !important;
        padding: 9px 18px;
        font-size: 13.5px;
        transition: all 0.2s ease;
        height: 36px;

        &:first-child {
          border-radius: $radius-sm 0 0 $radius-sm;
        }
        &:last-child {
          border-radius: 0 $radius-sm $radius-sm 0;
        }

        &--primary {
          background: $brand-gradient !important;
          border: none !important;
          color: #fff !important;
          box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);

          &:hover {
            filter: brightness(1.1);
          }
        }

        &--success {
          background: $success-gradient !important;
          border: none !important;
          color: #fff !important;
          box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);

          &:hover {
            filter: brightness(1.1);
          }
        }

        &--warning {
          background: $warn-gradient !important;
          border: none !important;
          color: #fff !important;
          box-shadow: 0 2px 8px rgba(245, 158, 11, 0.3);

          &:hover {
            filter: brightness(1.1);
          }
        }

        &:not(.el-button--primary):not(.el-button--success):not(.el-button--warning):hover {
          background: $bg-hover !important;
          border-color: $brand-primary !important;
        }
      }
    }
  }
}

/* ========== 左侧节点侧边栏 ========== */
.sidebar-node-list {
  width: 210px;
  background: $bg-card;
  border-right: 1px solid $border-color;
  padding: 20px 14px;
  flex-shrink: 0;
  overflow-y: auto;

  // 美化滚动条
  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background: $border-color;
    border-radius: 10px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }

  .sidebar-title {
    font-size: 16px;
    font-weight: 600;
    color: $text-primary;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 6px;

    &::before {
      content: '';
      width: 4px;
      height: 16px;
      background: $brand-primary;
      border-radius: 2px;
    }
  }

  .sidebar-group-title {
    font-size: 12px;
    color: $text-tip;
    margin: 14px 0 8px;
    letter-spacing: 0.4px;
  }

  .sidebar-node-items {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .sidebar-node-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    border-radius: $radius-sm;
    color: $text-normal;
    cursor: grab;
    border: 1px solid transparent;
    transition: all 0.2s ease;

    &:active {
      cursor: grabbing;
      scale: 0.98;
    }

    &:hover {
      background: $bg-hover;
      border-color: $brand-primary;
    }
  }

  .menu-icon {
    width: 26px;
    height: 26px;
    border-radius: $radius-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: white;
    flex-shrink: 0;
  }
}

/* ========== 画布右键添加节点菜单 ========== */
.node-add-menu {
  position: absolute !important;
  z-index: 99999 !important;
  background: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-md;
  padding: 10px;
  min-width: 200px;
  max-width: 240px;
  box-shadow: $shadow-heavy;
  backdrop-filter: blur(6px);

  .menu-group {
    margin-bottom: 10px;

    &:last-child {
      margin-bottom: 0;
    }
  }

  .group-title {
    font-size: 12px;
    color: $text-tip;
    padding: 4px 8px 6px;
    letter-spacing: 0.3px;
  }

  .group-items {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .menu-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 10px;
    border-radius: $radius-sm;
    cursor: pointer;
    color: $text-normal;
    transition: background 0.2s ease;

    &:hover {
      background: $bg-hover;
    }
  }

  .menu-icon {
    width: 26px;
    height: 26px;
    border-radius: $radius-sm;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: white;
    flex-shrink: 0;
  }
}

/* ========== 变量抽屉面板 ========== */
.variable-panel {
  color: $text-normal;
  padding-right: 4px;

  // 表格美化
  :deep(.el-table) {
    background: $bg-secondary;
    border: 1px solid $border-color;
    border-radius: $radius-sm;

    .el-table__header {
      background: $bg-card;

      th {
        background: $bg-card !important;
        color: $text-primary !important;
        border-color: $border-color !important;
      }
    }

    .el-table__row {
      td {
        background: $bg-secondary !important;
        border-color: $border-color !important;
        color: $text-normal;
      }
    }
  }

  .node-vars-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
    margin-top: 12px;
  }

  .node-var-item {
    background: $bg-card;
    border: 1px solid $border-color;
    border-radius: $radius-md;
    padding: 12px;
    transition: border-color 0.2s ease;

    &:hover {
      border-color: $brand-primary;
    }

    .node-name {
      font-weight: 500;
      color: $text-primary;
      margin-bottom: 6px;
    }
  }

  .usage-tips {
    font-size: 13px;
    line-height: 1.6;

    code {
      background: $bg-card;
      padding: 3px 8px;
      border-radius: $radius-sm;
      color: $brand-primary;
      font-family: 'Consolas', monospace;
    }
  }
}

/* ========== 执行结果弹窗 ========== */
.execution-output {
  background: $bg-secondary;
  padding: 18px;
  border-radius: $radius-md;
  color: #e2e8f0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  border: 1px solid $border-color;
  font-family: 'Consolas', monospace;
  line-height: 1.5;

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: $border-color;
    border-radius: 10px;
  }
}

.node-details-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.node-detail-card {
  background: $bg-card;
  border: 1px solid $border-color;
  border-radius: $radius-lg;
  padding: 16px;
  transition: all 0.2s ease;

  &.node-success {
    border-left: 4px solid #10b981;
  }
  &.node-error {
    border-left: 4px solid #ef4444;
  }

  &:hover {
    box-shadow: $shadow-base;
  }
}

.node-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .node-title-info {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    color: $text-primary;
  }

  .node-time {
    font-size: 12px;
    color: $text-muted;
    padding: 3px 8px;
    background: rgba(255,255,255,0.05);
    border-radius: 12px;
  }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  color: $brand-primary;
  margin-bottom: 8px;
  font-weight: 500;
}

.section-content pre {
  background: $bg-secondary;
  padding: 12px;
  border-radius: $radius-sm;
  color: #e2e8f0;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  border: 1px solid $border-color;
  font-family: 'Consolas', monospace;
  line-height: 1.4;

  &::-webkit-scrollbar {
    width: 5px;
  }
}

.section-content.error-content {
  border-color: rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}

/* ========== 弹窗抽屉统一暗色覆盖 ========== */
.save-dialog, .info-dialog {
  :deep(.el-dialog) {
    background: $bg-card !important;
    border: 1px solid $border-color !important;
    border-radius: $radius-xl;
    box-shadow: $shadow-heavy;
  }

  :deep(.el-dialog__header) {
    background: $bg-secondary;
    border-bottom: 1px solid $border-color;
    border-radius: $radius-xl $radius-xl 0 0;
    padding: 16px 24px;
  }

  :deep(.el-dialog__title) {
    color: $text-primary;
    font-weight: 600;
    font-size: 16px;
  }

  :deep(.el-dialog__body) {
    background: $bg-card;
    padding: 24px;
  }

  :deep(.el-dialog__footer) {
    background: $bg-secondary;
    border-top: 1px solid $border-color;
    border-radius: 0 0 $radius-xl $radius-xl;
    padding: 14px 24px;
  }
}

// 保存弹窗表单
.save-dialog {
  :deep(.el-form-item__label) {
    color: $text-normal !important;
  }

  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    background: $bg-secondary !important;
    border: 1px solid $border-color !important;
    box-shadow: none !important;
    border-radius: $radius-sm;
  }

  :deep(.el-input__inner),
  :deep(.el-textarea__inner) {
    color: $text-primary !important;
  }
}

// 工作流信息弹窗详情
.info-dialog {
  :deep(.el-descriptions) {
    background: $bg-card;
    border: none !important;
  }

  :deep(.el-descriptions__table) {
    border-collapse: collapse;
    width: 100%;
  }

  :deep(.el-descriptions__cell) {
    border: 1px solid $border-color !important;
    padding: 12px 16px;
  }

  :deep(.el-descriptions__label) {
    background: $bg-secondary !important;
    color: $text-muted !important;
    font-weight: 500;
    width: 110px;
  }

  :deep(.el-descriptions__content) {
    background: $bg-card !important;
    color: $text-normal !important;
  }
}

// 全局弹窗按钮统一美化
:deep(.el-dialog__footer .el-button) {
  height: 36px;
  padding: 8px 18px;
  border-radius: $radius-sm;

  &:not(.el-button--primary) {
    background: $bg-card !important;
    border: 1px solid $border-color !important;
    color: $text-normal !important;

    &:hover {
      background: $bg-hover !important;
      border-color: $brand-primary !important;
    }
  }
}

/* ========== VueFlow 连线样式优化 ========== */
:deep(.vue-flow__edge) {
  position: relative !important;

  .vue-flow__edge-path {
    stroke-width: 2.2px;
    stroke: #555b88;
    transition: all 0.2s ease;
  }

  &:hover .vue-flow__edge-path {
    stroke: $brand-primary !important;
    stroke-width: 3px !important;
    filter: drop-shadow(0 0 4px rgba(102, 126, 234, 0.4));
  }
}

.edge-add-circle {
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.24s ease;
}

.vue-flow__edge:hover .edge-add-circle {
  opacity: 1;
}

/* ========== Element 组件通用暗色覆盖 ========== */
:deep(.el-drawer) {
  background: $bg-card !important;

  .el-drawer__header {
    background: $bg-secondary;
    border-bottom: 1px solid $border-color;

    .el-drawer__title {
      color: $text-primary;
    }
  }

  .el-drawer__body {
    background: $bg-card;
  }
}

:deep(.el-tabs__header) {
  background: transparent;
  margin-bottom: 12px;
}
:deep(.el-tabs__nav-wrap::after) {
  background: $border-color;
}
:deep(.el-tabs__item) {
  color: $text-muted;

  &.is-active {
    color: $brand-primary;
  }
}
:deep(.el-alert) {
  background: rgba(102, 126, 234, 0.08);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: $radius-sm;
}
</style>

<style>
/* 全局强制覆盖弹窗描述列表样式，优先级高于局部 */
.workflow-editor .info-dialog .el-descriptions {
  background: #1a1f3a !important;
  border: 1px solid #303759 !important;
  border-radius: 8px;
}

.workflow-editor .info-dialog .el-descriptions__table {
  border-collapse: collapse !important;
}

.workflow-editor .info-dialog .el-descriptions__cell {
  border: 1px solid #303759 !important;
  padding: 12px 16px !important;
  background: #1a1f3a !important;
}

.workflow-editor .info-dialog .el-descriptions__label {
  background: #0f1228 !important;
  color: #94a3b8 !important;
  font-weight: 500 !important;
  width: 110px !important;
}

.workflow-editor .info-dialog .el-descriptions__content {
  background: #1a1f3a !important;
  color: #cbd5e6 !important;
}

.workflow-editor .info-dialog .el-descriptions__cell.is-bordered-label,
.workflow-editor .info-dialog .el-descriptions__cell.is-bordered-content {
  border-color: #303759 !important;
}

.workflow-editor .info-dialog .el-dialog {
  background: #1a1f3a !important;
  border: 1px solid #303759 !important;
}

/* 全局美化滚动条（页面内所有滚动区域统一） */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
::-webkit-scrollbar-thumb {
  background: #303759;
  border-radius: 10px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
</style>