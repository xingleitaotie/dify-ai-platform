import { defineStore } from 'pinia'
import { userApi } from '@/api/user'
import request from '@/api/request'

export const useUserStore = defineStore('user', {
    state: () => ({
        token: localStorage.getItem('token') || '',
        user: JSON.parse(localStorage.getItem('user') || 'null'),
        apps: []
    }),

    getters: {
        isLoggedIn: (state) => !!state.token,
        username: (state) => state.user?.username || ''
    },

    actions: {
        async login(loginData) {
            const res = await userApi.login(loginData)
            if (res.code === 200) {
                this.token = res.data
                this.user = { username: loginData.username }
                localStorage.setItem('token', res.data)
                localStorage.setItem('user', JSON.stringify(this.user))
                return true
            }
            return false
        },

        async register(registerData) {
            const res = await userApi.register(registerData)
            return res.code === 200
        },

        logout() {
            this.token = ''
            this.user = null
            localStorage.removeItem('token')
            localStorage.removeItem('user')
        },

        // 新增：验证 token 有效性
        async verifyToken() {
            if (!this.token) return false
            try {
                // 调用一个需要认证的接口验证 token（比如获取用户信息）
                const res = await request.get('/user/info')
                if (res.code === 200) {
                    this.user = res.data
                    localStorage.setItem('user', JSON.stringify(this.user))
                    return true
                }
                return false
            } catch (error) {
                console.error('Token验证失败:', error)
                return false
            }
        },

        // 新增：初始化认证状态（刷新页面时调用）
        initAuth() {
            const token = localStorage.getItem('token')
            const user = localStorage.getItem('user')
            if (token) {
                this.token = token
                if (user) {
                    try {
                        this.user = JSON.parse(user)
                    } catch (e) {
                        this.user = null
                    }
                }
            }
        },

        async loadApps() {
            const res = await userApi.getAppList()
            if (res.code === 200) {
                this.apps = res.data
            }
            return this.apps
        },

        async createApp(appName) {
            const res = await userApi.createApp({ appName })
            if (res.code === 200) {
                await this.loadApps()
            }
            return res.data
        },

        async deleteApp(id) {
            const res = await userApi.deleteApp(id)
            if (res.code === 200) {
                await this.loadApps()
            }
            return res.data
        }
    }
})