// src/api/prompt.js
import request from './request'

export const promptApi = {
    // 生成提示词
    generate(data) {
        return request.post('/prompt/generate', data)
    },

    // 保存模板
    saveTemplate(data) {
        return request.post('/prompt/template', data)
    },

    // 更新模板
    updateTemplate(id, data) {
        return request.put(`/prompt/template/${id}`, data)
    },

    // 获取模板详情
    getTemplate(id) {
        return request.get(`/prompt/template/${id}`)
    },

    // 获取模板列表
    listTemplates() {
        return request.get('/prompt/templates')
    },

    // 分页获取模板
    pageTemplates(params) {
        return request.get('/prompt/templates/page', { params })
    },

    // 删除模板
    deleteTemplate(id) {
        return request.delete(`/prompt/template/${id}`)
    },

    // 测试模板
    testTemplate(id, context) {
        return request.post(`/prompt/template/${id}/test`, context)
    },

    // 复制模板
    copyTemplate(id, newName) {
        return request.post(`/prompt/template/${id}/copy`, null, { params: { newName } })
    },

    // 设置状态
    setStatus(id, status) {
        return request.put(`/prompt/template/${id}/status`, null, {
            params: { status }
        })
    },

    // 按类型获取模板
    getTemplatesByType(type) {
        return request.get('/prompt/templates/by-type', { params: { type } })
    },

    // 搜索模板
    searchTemplates(keyword) {
        return request.get('/prompt/templates/search', { params: { keyword } })
    }
}