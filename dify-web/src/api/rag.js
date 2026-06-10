import request from './request'

export const ragApi = {
    // ==================== 原有接口 ====================

    // 上传文档
    uploadDocument(file, kbName) {
        const formData = new FormData()
        formData.append('file', file)
        if (kbName) {
            formData.append('kbName', kbName)
        }
        return request.post('/rag/upload', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        })
    },

    // 单条文本向量化
    singleEmbedding(data) {
        return request.post('/rag/embedding/single', data)
    },

    // 处理文本
    processText(text) {
        return request.post('/rag/text', { text })
    },

    // ==================== 新增：知识库管理接口 ====================

    // 获取所有知识库
    getKnowledgeBases() {
        return request.get('/rag/kb/list')
    },

    // 创建知识库
    createKnowledgeBase(data) {
        return request.post('/rag/kb/create', data)
    },

    // 删除知识库
    deleteKnowledgeBase(kbId) {
        return request.delete(`/rag/kb/${kbId}`)
    },

    // 获取指定知识库的分块
    getChunksByKb(kbId) {
        return request.get(`/rag/kb/${kbId}/chunks`)
    },

    // 获取指定知识库的文档列表
    getDocumentsByKb(kbId) {
        return request.get(`/rag/kb/${kbId}/documents`)
    },

    // 在指定知识库中检索
    searchInKb(kbId, data) {
        return request.post(`/rag/kb/${kbId}/search`, data)
    },

    // 更新知识库配置
    updateKnowledgeBaseConfig(kbId, data) {
        return request.put(`/rag/kb/${kbId}/config`, data)
    },

    deleteDocument(kbName, documentId) {
        return request.delete(`/rag/kb/${encodeURIComponent(kbName)}/documents/${documentId}`)
    },

    searchDocument(data) {
        return request.post(`/rag/search/single/document`,data)
    }
}