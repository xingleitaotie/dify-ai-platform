// src/api/chat.js
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
    }
}

// 模型配置 API（路径与后端保持一致）
export const modelConfigApi = {
    // ==================== 原有接口（兼容旧版前端） ====================

    // 获取当前配置（原有接口）
    getConfig() {
        return request.get('/llm/config')
    },

    // 更新配置（原有接口）
    updateMemoryConfig(config) {
        return request.post('/llm/config', config)
    },

    // 测试连接（原有接口）
    testConnection() {
        return request.post('/llm/config/test')
    },

    // ==================== 新增接口（配置管理） ====================

    // 获取配置列表（分页）
    getConfigPage(pageNum, pageSize, type, status) {
        return request.get('/llm/config/page', {
            params: {
                pageNum,
                pageSize,
                type,
                status
            }
        })
    },

    // 获取当前配置详情
    getCurrentConfigDetail() {
        return request.get('/llm/config/current/detail')
    },

    // 根据ID获取配置
    getConfigById(id) {
        return request.get(`/llm/config/detail/${id}`)
    },

    // 获取所有启用的配置
    getEnabledConfigs() {
        return request.get('/llm/config/enabled')
    },

    // 新增配置
    addConfig(config) {
        return request.post('/llm/config/add', config)
    },

    // 更新配置
    updateConfig(config) {
        return request.put('/llm/config/update', config)
    },

    // 删除配置
    deleteConfig(id) {
        return request.delete(`/llm/config/delete/${id}`)
    },

    // 切换配置（热加载）
    switchConfig(configId) {
        return request.post(`/llm/config/switch/${configId}`)
    },

    // 测试指定配置连接
    testConfig(config) {
        return request.post('/llm/config/test-config', config)
    },

    // ==================== 模型类型相关接口 ====================

    // 获取支持的模型类型
    getSupportedTypes() {
        return request.get('/llm/config/types')
    },

    // 测试指定模型类型的连接
    testModelType(modelType) {
        return request.post('/llm/config/test-model', { modelType })
    }
}