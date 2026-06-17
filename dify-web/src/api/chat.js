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
    clearSessionTemplate(sessionId) {
        return request.delete(`/llm/session/${sessionId}/template-cache`)
    }
}
