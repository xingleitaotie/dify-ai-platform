import { defineStore } from 'pinia'

export const useMenuStore = defineStore('menu', {
    state: () => ({
        menus: [],
        permissions: [],
        currentMenu: null
    }),

    getters: {
        sidebarMenus: (state) => {
            const userRole = localStorage.getItem('userRole') || 'admin'
            return state.menus.filter(menu => {
                if (menu.type !== 0 || menu.status !== 1) {
                    return false
                }
                if (menu.requiredRole && menu.requiredRole !== userRole) {
                    return false
                }
                return true
            })
        },

        hasPermission: (state) => (permission) => {
            return state.permissions.includes(permission)
        }
    },

    actions: {
        async loadMenus() {
            this.menus = [
                {
                    id: 1,
                    parentId: 0,
                    name: 'dashboard',
                    title: '首页',
                    path: '/dashboard',
                    icon: 'HomeFilled',
                    type: 0,
                    sort: 1,
                    status: 1,
                    children: []
                },
                {
                    id: 2,
                    parentId: 0,
                    name: 'chat',
                    title: '对话',
                    path: '/chat',
                    icon: 'ChatDotRound',
                    type: 0,
                    sort: 2,
                    status: 1,
                    children: []
                },
                {
                    id: 3,
                    parentId: 0,
                    name: 'knowledge-base',
                    title: '知识库',
                    path: '/knowledge-base',
                    icon: 'Document',
                    type: 0,
                    sort: 3,
                    status: 1,
                    children: []
                },
                {
                    id: 4,
                    parentId: 0,
                    name: 'agent',
                    title: 'Agent',
                    path: '/agent',
                    icon: 'Cpu',
                    type: 0,
                    sort: 4,
                    status: 1,
                    children: []
                },
                {
                    id: 5,
                    parentId: 0,
                    name: 'workflow',
                    title: '工作流',
                    path: '/workflow/list',
                    icon: 'Share',
                    type: 0,
                    sort: 5,
                    status: 1,
                    children: []
                },
                {
                    id: 6,
                    parentId: 0,
                    name: 'prompt',
                    title: '提示词',
                    path: '/prompt',
                    icon: 'Edit',
                    type: 0,
                    sort: 6,
                    status: 1,
                    children: []
                },
                {
                    id: 7,
                    parentId: 0,
                    name: 'menu-manage',
                    title: '菜单管理',
                    path: '/system/menu',
                    icon: 'Menu',
                    type: 0,
                    sort: 7,
                    status: 1,
                    children: [],
                    requiredRole: 'admin'
                },
                {
                    id: 8,
                    parentId: 0,
                    name: 'button-manage',
                    title: '按钮管理',
                    path: '/system/button',
                    icon: 'Grid',
                    type: 0,
                    sort: 8,
                    status: 1,
                    children: [],
                    requiredRole: 'admin'
                },
                {
                    id: 9,
                    parentId: 0,
                    name: 'settings',
                    title: '系统设置',
                    path: '/settings',
                    icon: 'Tools',
                    type: 0,
                    sort: 9,
                    status: 1,
                    children: [],
                    requiredRole: 'admin'
                }
            ]
        },

        async loadPermissions() {
            this.permissions = [
                'menu:view',
                'menu:add',
                'menu:edit',
                'menu:delete',
                'button:view',
                'button:add',
                'button:edit',
                'button:delete',
                'workflow:execute',
                'rag:upload',
                'rag:search'
            ]
        },

        setCurrentMenu(menu) {
            this.currentMenu = menu
        },

        async addMenu(menu) {
            const maxId = Math.max(...this.menus.map(m => m.id))
            menu.id = maxId + 1
            menu.children = menu.children || []
            this.menus.push(menu)
            return menu
        },

        async updateMenu(id, menu) {
            const index = this.menus.findIndex(m => m.id === id)
            if (index !== -1) {
                this.menus[index] = { ...this.menus[index], ...menu }
            }
        },

        async deleteMenu(id) {
            this.menus = this.menus.filter(m => m.id !== id)
        },

        async addButton(button) {
            return button
        },

        async updateButton(id, button) {
            return button
        },

        async deleteButton(id) {
            return true
        }
    }
})
