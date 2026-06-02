// API 统一导出入口
// 使用方法：import { userApi, llmApi, ragApi, functionApi, agentApi, promptApi } from '@/api'

// 用户相关 API
export { userApi } from './user'

// 大模型对话相关 API
export { llmApi } from './llm'

// RAG 知识库相关 API
export { ragApi } from './rag'

// Function Calling 工具相关 API
export { functionApi } from './function'

// Agent 相关 API
export { agentApi } from './agent'

// Prompt 模板相关 API
export { promptApi } from './prompt'

// SSE 流式处理
export { SSEHandler, streamChat } from './sse'

// 请求实例（用于特殊场景）
export { default as request } from './request'

// 统一错误处理
export const handleApiError = (error, defaultMessage = '操作失败') => {
    console.error('API Error:', error)
    const message = error.response?.data?.msg || error.message || defaultMessage
    return { success: false, message }
}

// 统一成功处理
export const handleApiSuccess = (response, successMessage = null) => {
    if (response.code === 200) {
        if (successMessage) {
            ElMessage.success(successMessage)
        }
        return { success: true, data: response.data }
    }
    return { success: false, message: response.msg || '操作失败' }
}