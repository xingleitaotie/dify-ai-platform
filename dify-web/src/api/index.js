// API 统一导出入口
// 使用方法：import { userApi, llmApi, ragApi, functionApi, agentApi, promptApi, providerApi, systemModelApi, workflowApi } from '@/api'

// ========== 各模块 API ==========
export { userApi } from './user'
export { llmApi } from './llm'
export { ragApi } from './rag'
export { functionApi } from './function'
export { agentApi } from './agent'
export { promptApi } from './prompt'
export { providerApi, systemModelApi } from './modelConfig'
export { workflowApi, streamExecuteWorkflow } from './workflow'

// ========== 兼容旧版（chat.js 保留用于过渡） ==========
export { chatApi  } from './chat'

// ========== SSE 流式处理 ==========
export { streamChat } from './sse'

// ========== 请求实例 ==========
export { default as request } from './request'

// ========== 统一响应处理工具 ==========

/**
 * 统一错误处理
 * @param {Error} error - 错误对象
 * @param {string} defaultMessage - 默认错误消息
 * @returns {Object}
 */
export const handleApiError = (error, defaultMessage = '操作失败') => {
    console.error('API Error:', error)
    const message = error.response?.data?.msg || error.response?.data?.message || error.message || defaultMessage
    return { success: false, message }
}

/**
 * 统一成功处理（需要在组件中导入 ElMessage 使用）
 * @param {Object} response - 响应对象
 * @param {Function} messageFn - 消息提示函数（如 ElMessage.success）
 * @returns {Object}
 */
export const handleApiResponse = (response, messageFn = null) => {
    if (response?.code === 200) {
        if (messageFn && response.msg) {
            messageFn(response.msg)
        }
        return { success: true, data: response.data, message: response.msg }
    }
    return { success: false, data: null, message: response?.msg || '操作失败' }
}