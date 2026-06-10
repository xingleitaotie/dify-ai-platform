import request from './request'

export const chatApi = {
    // 获取会话列表
    getSessions() {
        return request.get('/user/chat/sessions')
    },

    // 获取会话消息
    getMessages(sessionId) {
        return request.get(`/user/chat/sessions/${sessionId}/messages`)
    },

    // 创建或更新会话
    saveSession(sessionId, title, modelType) {
        return request.post('/user/chat/sessions', { sessionId, title, modelType })
    },

    // 保存消息
    saveMessage(sessionId, role, content, modelType) {
        return request.post('/user/chat/messages', { sessionId, role, content, modelType })
    },

    // 获取对话统计
    getStats() {
        return request.get('/user/chat/stats')
    },

    // 删除会话
    deleteSession(sessionId) {
        return request.delete(`/user/chat/sessions/${sessionId}`)
    },
    // 清除会话模板缓存
    clearSessionTemplate: (sessionId) => request.delete(`/llm/session/${sessionId}/template-cache`)
}

// 模型配置 API（路径与后端保持一致）
export const modelConfigApi = {
    // ==================== 原有接口（兼容旧版前端） ====================

    // 获取当前配置（原有接口）
    getConfig() {
        return request.get('/llm/config')
    },

    // 获取所有启用的配置
    getEnabledConfigs() {
        return request.get('/llm/config/enabled')
    }

}
// ========== 系统模型配置 ==========
export const systemModelApi = {
    // 获取所有系统能力配置
    getCapabilities() {
        return request.get('/provider/capability')
    },

    // 获取能力类型列表
    getCapabilityTypes() {
        return request.get('/provider/capability/types')
    },

    // 更新系统能力配置
    updateCapability(capabilityType, modelConfigId) {
        return request.put(`/provider/capability/${capabilityType}`, {
            modelConfigId
        })
    }
}