<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <el-icon :size="24"><Menu /></el-icon>
        <h2>菜单管理</h2>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd" v-hasPermi="['menu:add']">
          <el-icon><Plus /></el-icon>
          新增菜单
        </el-button>
      </div>
    </div>

    <div class="glass-card">
      <div class="card-body">
        <el-table :data="menuList" border="false" default-expand-all>
          <el-table-column prop="title" label="菜单名称" min-width="180">
            <template #default="scope">
              <div class="menu-name">
                <el-icon v-if="scope.row.icon" :size="18"><component :is="getIcon(scope.row.icon)" /></el-icon>
                <span>{{ scope.row.title }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="菜单标识" min-width="120" />
          <el-table-column prop="path" label="路由路径" min-width="150" />
          <el-table-column prop="type" label="菜单类型" min-width="80">
            <template #default="scope">
              <el-tag :type="scope.row.type === 0 ? 'primary' : 'success'" size="small">
                {{ scope.row.type === 0 ? '目录' : '菜单' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" min-width="60" />
          <el-table-column prop="status" label="状态" min-width="80">
            <template #default="scope">
              <el-switch
                  v-model="scope.row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="handleStatusChange(scope.row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="200" fixed="right">
            <template #default="scope">
              <el-button type="primary" link size="small" @click="handleEdit(scope.row)" v-hasPermi="['menu:edit']">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="success" link size="small" @click="handleAddChild(scope.row)" v-hasPermi="['menu:add']">
                <el-icon><Plus /></el-icon>
                添加子菜单
              </el-button>
              <el-button type="danger" link size="small" @click="handleDelete(scope.row)" v-hasPermi="['menu:delete']">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" placeholder="请选择上级菜单">
            <el-option :value="0" label="无（顶级菜单）" />
            <el-option
                v-for="menu in topMenus"
                :key="menu.id"
                :value="menu.id"
                :label="menu.title"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.title" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单标识" required>
          <el-input v-model="form.name" placeholder="请输入菜单标识" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item label="菜单图标">
          <el-select v-model="form.icon" placeholder="请选择图标">
            <el-option v-for="(icon, key) in iconOptions" :key="key" :value="key" :label="icon" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Menu,
  Plus,
  Edit,
  Delete,
  HomeFilled,
  ChatDotRound,
  Document,
  Cpu,
  Setting,
  Share,
  Tools,
  Grid
} from '@element-plus/icons-vue'
import { useMenuStore } from '@/stores/menu'

const menuStore = useMenuStore()

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  parentId: 0,
  name: '',
  title: '',
  path: '',
  icon: '',
  type: 0,
  sort: 0,
  status: 1
})

const iconOptions = {
  HomeFilled: '首页',
  ChatDotRound: '对话',
  Document: '文档',
  Cpu: 'CPU',
  Setting: '设置',
  Share: '分享',
  Edit: '编辑',
  Tools: '工具',
  Menu: '菜单',
  Grid: '按钮'
}

const iconMap = {
  HomeFilled,
  ChatDotRound,
  Document,
  Cpu,
  Setting,
  Share,
  Edit,
  Tools,
  Menu,
  Grid
}

const menuList = computed(() => menuStore.menus)

const topMenus = computed(() => menuStore.menus.filter(m => m.parentId === 0))

const getIcon = (iconName) => {
  return iconMap[iconName] || HomeFilled
}

const handleAdd = () => {
  isEdit.value = false
  form.value = {
    id: null,
    parentId: 0,
    name: '',
    title: '',
    path: '',
    icon: '',
    type: 0,
    sort: 0,
    status: 1
  }
  dialogVisible.value = true
}

const handleAddChild = (parent) => {
  isEdit.value = false
  form.value = {
    id: null,
    parentId: parent.id,
    name: '',
    title: '',
    path: '',
    icon: '',
    type: 1,
    sort: 0,
    status: 1
  }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该菜单吗？', '提示', {
      type: 'warning'
    })
    await menuStore.deleteMenu(row.id)
    ElMessage.success('删除成功')
  } catch {}
}

const handleStatusChange = async (row) => {
  await menuStore.updateMenu(row.id, { status: row.status })
  ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.name) {
    ElMessage.error('请填写必填项')
    return
  }

  if (isEdit.value) {
    await menuStore.updateMenu(form.value.id, form.value)
    ElMessage.success('编辑成功')
  } else {
    await menuStore.addMenu(form.value)
    ElMessage.success('新增成功')
  }

  dialogVisible.value = false
}
</script>

<style scoped>
.page-container {
  padding: 24px;
  min-height: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 22px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.card-body {
  padding: 24px;
}

.menu-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-name span {
  color: var(--text-primary);
}
</style>
