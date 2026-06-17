import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/views/MainLayout.vue'
import Login from '@/views/Login.vue'
import { useUserStore } from '@/stores/user'

// 工作流组件直接导入（已经在之前导入，保持不变）
import WorkflowList from '@/views/workflow/WorkflowList.vue'
import WorkflowEditor from '@/views/workflow/WorkflowEditor/index.vue'

const routes = [
    {
        path: '/login',
        name: 'Login',
        component: Login,
        meta: { requiresAuth: false, title: '登录' }
    },
    {
        path: '/',
        component: MainLayout,
        meta: { requiresAuth: true },
        redirect: '/dashboard',
        children: [
            {
                path: 'dashboard',
                name: 'Dashboard',
                component: () => import('@/views/Dashboard.vue'),
                meta: { title: '首页', icon: 'HomeFilled', requiresAuth: true }
            },
            {
                path: 'chat',
                name: 'Chat',
                component: () => import('@/views/Chat.vue'),
                meta: { title: '对话', icon: 'ChatDotRound', requiresAuth: true }
            },
            {
                path: 'knowledge-base',
                name: 'KnowledgeBase',
                component: () => import('@/views/KnowledgeBase.vue'),
                meta: { title: '知识库', icon: 'Document', requiresAuth: true }
            },
            {
                path: 'agent',
                name: 'Agent',
                component: () => import('@/views/Agent.vue'),
                meta: { title: 'Agent', icon: 'Cpu', requiresAuth: true }
            },
            {
                path: 'workflow/list',
                name: 'WorkflowList',
                component: WorkflowList,
                meta: { title: '工作流', icon: 'Share', requiresAuth: true }
            },
            {
                path: 'workflow/editor/:id?',   // 改为相对路径
                name: 'WorkflowEditor',
                component: WorkflowEditor,
                meta: { title: '工作流编辑器', requiresAuth: true }   // 显式添加 requiresAuth
            },
            {
                path: 'prompt',
                name: 'Prompt',
                component: () => import('@/views/components/PromptTemplate.vue'),
                meta: { title: '提示词', icon: 'Edit', requiresAuth: true }
            },
            {
                path: 'settings',
                name: 'Settings',
                component: () => import('@/views/Settings.vue'),
                meta: { title: '设置', icon: 'Setting', requiresAuth: true }
            }
        ]
    },
    // 404 重定向
    {
        path: '/:pathMatch(.*)*',
        redirect: '/dashboard'
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 需要跳过验证的白名单路径
const whiteList = ['/login']

// 路由守卫
router.beforeEach(async (to, from, next) => {
    const token = localStorage.getItem('token')
    const userStore = useUserStore()

    console.log('路由守卫:', to.path, 'token:', !!token)

    // 白名单路径直接放行
    if (whiteList.includes(to.path)) {
        next()
        return
    }

    // 如果有 token
    if (token) {
        // 如果 store 中没有用户信息，先初始化
        if (!userStore.user) {
            userStore.initAuth()
        }

        // 可选：异步验证 token 有效性（可以注释掉以提高性能）
        // 如果需要严格的 token 验证，取消下面的注释
        /*
        try {
            const isValid = await userStore.verifyToken()
            if (!isValid) {
                userStore.logout()
                ElMessage.error('登录已过期，请重新登录')
                next('/login')
                return
            }
        } catch (error) {
            userStore.logout()
            next('/login')
            return
        }
        */

        next()
    } else {
        // 没有 token，需要认证的页面跳转到登录页
        if (to.meta.requiresAuth !== false) {
            next('/login')
        } else {
            next()
        }
    }
})

// 路由后置守卫：设置页面标题
router.afterEach((to) => {
    if (to.meta?.title) {
        document.title = `${to.meta.title} - Dify AI`
    }
})

export default router