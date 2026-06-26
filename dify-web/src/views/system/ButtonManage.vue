<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-title">
        <el-icon :size="24"><Grid /></el-icon>
        <h2>按钮管理</h2>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="handleAdd" v-hasPermi="['button:add']">
          <el-icon><Plus /></el-icon>
          新增按钮
        </el-button>
      </div>
    </div>

    <div class="glass-card">
      <div class="card-body">
        <el-table :data="buttonList" border="false">
          <el-table-column prop="name" label="按钮标识" min-width="150" />
          <el-table-column prop="title" label="按钮名称" min-width="120" />
          <el-table-column prop="menuName" label="所属菜单" min-width="120" />
          <el-table-column prop="permission" label="权限标识" min-width="150" />
          <el-table-column prop="type" label="按钮类型" min-width="100">
            <template #default="scope">
              <el-tag :type="getButtonTypeTag(scope.row.type)" size="small">
                {{ getButtonTypeLabel(scope.row.type) }}
              </el-tag>
            </template>
          </el-table-column>
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
          <el-table-column label="操作" min-width="180" fixed="right">
            <template #default="scope">
              <el-button type="primary" link size="small" @click="handleEdit(scope.row)" v-hasPermi="['button:edit']">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" link size="small" @click="handleDelete(scope.row)" v-hasPermi="['button:delete']">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑按钮' : '新增按钮'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属菜单" required>
          <el-select v-model="form.menuId" placeholder="请选择所属菜单">
            <el-option
                v-for="menu in menuOptions"
                :key="menu.id"
                :value="menu.id"
                :label="menu.title"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="按钮名称" required>
          <el-input v-model="form.title" placeholder="请输入按钮名称" />
        </el-form-item>
        <el-form-item label="按钮标识" required>
          <el-input v-model="form.name" placeholder="请输入按钮标识" />
        </el-form-item>
        <el-form-item label="权限标识" required>
          <el-input v-model="form.permission" placeholder="请输入权限标识，如: menu:add" />
        </el-form-item>
        <el-form-item label="按钮类型">
          <el-select v-model="form.type" placeholder="请选择按钮类型">
            <el-option :value="0" label="主要按钮" />
            <el-option :value="1" label="成功按钮" />
            <el-option :value="2" label="警告按钮" />
            <el-option :value="3" label="危险按钮" />
            <el-option :value="4" label="默认按钮" />
          </el-select>
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
import { Grid, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { useMenuStore } from '@/stores/menu'

const menuStore = useMenuStore()

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({
  id: null,
  menuId: null,
  name: '',
  title: '',
  permission: '',
  type: 0,
  status: 1
})

const buttonList = ref([
  { id: 1, menuId: 1, menuName: '首页', name: 'dashboard_view', title: '查看首页', permission: 'dashboard:view', type: 0, status: 1 },
  { id: 2, menuId: 3, menuName: '知识库', name: 'rag_upload', title: '上传文档', permission: 'rag:upload', type: 1, status: 1 },
  { id: 3, menuId: 3, menuName: '知识库', name: 'rag_search', title: '搜索文档', permission: 'rag:search', type: 0, status: 1 },
  { id: 4, menuId: 5, menuName: '工作流', name: 'workflow_execute', title: '执行工作流', permission: 'workflow:execute', type: 1, status: 1 },
  { id: 5, menuId: 7, menuName: '系统管理', name: 'menu_add', title: '新增菜单', permission: 'menu:add', type: 1, status: 1 },
  { id: 6, menuId: 7, menuName: '系统管理', name: 'menu_edit', title: '编辑菜单', permission: 'menu:edit', type: 0, status: 1 },
  { id: 7, menuId: 7, menuName: '系统管理', name: 'menu_delete', title: '删除菜单', permission: 'menu:delete', type: 3, status: 1 },
  { id: 8, menuId: 7, menuName: '系统管理', name: 'button_add', title: '新增按钮', permission: 'button:add', type: 1, status: 1 },
  { id: 9, menuId: 7, menuName: '系统管理', name: 'button_edit', title: '编辑按钮', permission: 'button:edit', type: 0, status: 1 },
  { id: 10, menuId: 7, menuName: '系统管理', name: 'button_delete', title: '删除按钮', permission: 'button:delete', type: 3, status: 1 }
])

const menuOptions = computed(() => {
  const options = []
  const menus = menuStore.menus
  menus.forEach(menu => {
    options.push({ id: menu.id, title: menu.title })
    if (menu.children) {
      menu.children.forEach(child => {
        options.push({ id: child.id, title: `${menu.title} - ${child.title}` })
      })
    }
  })
  return options
})

const getButtonTypeLabel = (type) => {
  const labels = ['主要按钮', '成功按钮', '警告按钮', '危险按钮', '默认按钮']
  return labels[type] || '默认按钮'
}

const getButtonTypeTag = (type) => {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  return types[type] || 'info'
}

const handleAdd = () => {
  isEdit.value = false
  form.value = {
    id: null,
    menuId: null,
    name: '',
    title: '',
    permission: '',
    type: 0,
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
    await ElMessageBox.confirm('确定要删除该按钮吗？', '提示', {
      type: 'warning'
    })
    buttonList.value = buttonList.value.filter(b => b.id !== row.id)
    ElMessage.success('删除成功')
  } catch {}
}

const handleStatusChange = (row) => {
  ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
}

const handleSubmit = () => {
  if (!form.value.title || !form.value.name || !form.value.permission) {
    ElMessage.error('请填写必填项')
    return
  }

  if (isEdit.value) {
    const index = buttonList.value.findIndex(b => b.id === form.value.id)
    if (index !== -1) {
      buttonList.value[index] = { ...buttonList.value[index], ...form.value }
    }
    ElMessage.success('编辑成功')
  } else {
    const maxId = Math.max(...buttonList.value.map(b => b.id))
    form.value.id = maxId + 1
    const menu = menuOptions.value.find(m => m.id === form.value.menuId)
    form.value.menuName = menu ? menu.title : ''
    buttonList.value.push(form.value)
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
</style>
