import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// API 基础路径
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

console.log('[API] 环境:', import.meta.env.MODE)
console.log('[API] API 基础路径:', API_BASE_URL)

const request = axios.create({
    baseURL: API_BASE_URL,
    timeout: 600000
})

// 请求拦截器
request.interceptors.request.use(
    config => {
        const token = localStorage.getItem('token')
        if (token) {
            config.headers['token'] = token
            config.headers['Authorization'] = `Bearer ${token}`
        }
        console.log(`[API Request] ${config.method.toUpperCase()} ${config.url}`)
        return config
    },
    error => {
        console.error('[API Request Error]', error)
        return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    response => {
        const res = response.data
        console.log(`[API Response] ${response.config.url}`, res)

        if (response.status === 504) {
            ElMessage.error('请求超时，请稍后重试')
            return Promise.reject(new Error('请求超时'))
        }

        if (res.code !== undefined && res.code !== 200) {
            // 401 未授权或 token 过期
            if (res.code === 401 || res.code === 403) {
                ElMessage.error(res.msg || '登录已过期，请重新登录')
                // 清除本地存储
                localStorage.removeItem('token')
                localStorage.removeItem('user')
                // 跳转到登录页
                const currentPath = router.currentRoute.value.path
                if (currentPath !== '/login') {
                    router.push('/login')
                }
                return Promise.reject(new Error(res.msg || '未授权'))
            }

            const errorMsg = res.msg || '请求失败'
            ElMessage.error(errorMsg)
            return Promise.reject(new Error(errorMsg))
        }

        return res
    },
    error => {
        console.error('[API Response Error]', error)

        // 网络错误或超时
        if (error.code === 'ECONNABORTED' || error.message?.includes('timeout') || error.response?.status === 504) {
            ElMessage.error('请求超时，请稍后重试')
        } else if (error.message === 'Network Error') {
            ElMessage.error('网络错误，请检查后端服务是否启动')
        } else if (error.response?.status === 404) {
            ElMessage.error('接口不存在，请检查路径')
        } else if (error.response?.status === 500) {
            ElMessage.error('服务器内部错误，请稍后重试')
        } else if (error.response?.status === 401 || error.response?.status === 403) {
            // 处理 HTTP 状态码 401/403
            ElMessage.error('登录已过期，请重新登录')
            localStorage.removeItem('token')
            localStorage.removeItem('user')
            if (router.currentRoute.value.path !== '/login') {
                router.push('/login')
            }
        } else {
            ElMessage.error(error.message || '网络错误')
        }

        return Promise.reject(error)
    }
)

export default request