import request from './request'

// ========== 供应商管理 ==========
export const providerApi = {
    // 分页查询供应商
    getProviderPage(pageNum, pageSize, keyword) {
        return request.get('/provider/page', {
            params: { pageNum, pageSize, keyword }
        })
    },

    // 获取所有启用的供应商
    getEnabledProviders() {
        return request.get('/provider/enabled')
    },

    // 获取供应商详情
    getProviderDetail(id) {
        return request.get(`/provider/${id}`)
    },

    // 新增供应商
    addProvider(data) {
        return request.post('/provider', data)
    },

    // 更新供应商
    updateProvider(data) {
        return request.put('/provider', data)
    },

    // 删除供应商
    deleteProvider(id) {
        return request.delete(`/provider/${id}`)
    },

    // 添加模型配置
    addModelConfig(data) {
        return request.post('/provider/model', data)
    },

    // 更新模型配置
    updateModelConfig(data) {
        return request.put('/provider/model', data)
    },

    // 删除模型配置
    deleteModelConfig(id) {
        return request.delete(`/provider/model/${id}`)
    },

    // 测试模型连接
    testModelConfig(data) {
        return request.post('/provider/model/test', data)
    },

    // 获取供应商下的所有模型
    getModelsByProvider(providerId) {
        return request.get(`/provider/model/provider/${providerId}`)
    },

    // 获取指定能力的可用模型
    getModelsByCapability(capabilityType) {
        return request.get(`/provider/model/capability/${capabilityType}`)
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

    // 获取指定能力的可用模型
    getAvailableModels(capabilityType) {
        return request.get(`/provider/capability/${capabilityType}/models`)
    },

    // 更新系统能力配置
    updateCapability(capabilityType, modelConfigId, fallbackConfigId = null) {
        return request.put(`/provider/capability/${capabilityType}`, {
            modelConfigId,
            fallbackConfigId
        })
    },

    // 刷新缓存
    refreshCache() {
        return request.post('/provider/capability/refresh')
    }
}

// ========== 兼容旧接口（对话功能仍使用） ==========
export const chatApi = {
    // 普通对话
    chat(data) {
        return request.post('/llm/chat', data)
    },

    // 测试模型连接（旧接口兼容）
    testConnection(data) {
        return request.post('/llm/config/test', data)
    }
}