import request from './request'

export const promptApi = {
    // ==================== 模板管理（已有接口）====================

    listTemplates() {
        return request.get('/prompt/templates')
    },

    getTemplate(id) {
        return request.get(`/prompt/template/${id}`)
    },

    saveTemplate(data) {
        return request.post('/prompt/template', data)
    },

    updateTemplate(id, data) {
        return request.put(`/prompt/template/${id}`, data)
    },

    deleteTemplate(id) {
        return request.delete(`/prompt/template/${id}`)
    },

    setStatus(id, status) {
        return request.put(`/prompt/template/${id}/status?status=${status}`)
    },

    copyTemplate(id, newName) {
        return request.post(`/prompt/template/${id}/copy?newName=${newName}`)
    },

    generate(data) {
        return request.post('/prompt/generate', data)
    },

    syncAllToVector() {
        return request.post('/prompt/templates/sync-to-vector')
    },

    route(data) {
        return request.post('/prompt/route', data)
    },

    // ==================== 向量库操作（新增接口）====================

    /**
     * 获取向量库中的所有模板
     */
    listAllPromptTemplates() {
        return request.get('/prompt/vector/templates')
    },

    /**
     * 获取向量库中的模板数量
     */
    getPromptTemplateCount() {
        return request.get('/prompt/vector/count')
    },

    /**
     * 向量检索相似模板
     * @param {Object} data { query: string, topK?: number }
     */
    searchPromptTemplates(data) {
        return request.post('/prompt/vector/search', data)
    },

    /**
     * 强制重新同步（清空后重新同步）
     */
    forceResync() {
        return request.post('/prompt/vector/resync')
    },

    /**
     * 同步单个模板到向量库
     * @param {string} templateId 模板ID
     */
    syncTemplateToVector(templateId) {
        return request.post(`/prompt/vector/sync/${templateId}`)
    },

    /**
     * 从向量库删除模板
     * @param {string} templateId 模板ID
     */
    deleteVectorTemplate(templateId) {
        return request.delete(`/prompt/vector/template/${templateId}`)
    }
}