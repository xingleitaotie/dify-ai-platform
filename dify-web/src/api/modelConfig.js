import request from './request'

// ========== 供应商管理 ==========
export const providerApi = {
    getProviderPage(pageNum, pageSize, keyword) {
        return request.get('/provider/page', { params: { pageNum, pageSize, keyword } })
    },
    getEnabledProviders() {
        return request.get('/provider/enabled')
    },
    getProviderDetail(id) {
        return request.get(`/provider/${id}`)
    },
    addProvider(data) {
        return request.post('/provider', data)
    },
    updateProvider(data) {
        return request.put('/provider', data)
    },
    deleteProvider(id) {
        return request.delete(`/provider/${id}`)
    },
    addModelConfig(data) {
        return request.post('/provider/model', data)
    },
    updateModelConfig(data) {
        return request.put('/provider/model', data)
    },
    deleteModelConfig(id) {
        return request.delete(`/provider/model/${id}`)
    },
    testModelConfig(data) {
        return request.post('/provider/model/test', data)
    },
    getModelsByProvider(providerId) {
        return request.get(`/provider/model/provider/${providerId}`)
    },
    getModelsByCapability(capabilityType) {
        return request.get(`/provider/model/capability/${capabilityType}`)
    }
}

// ========== 系统模型配置 ==========
export const systemModelApi = {
    getCapabilities() {
        return request.get('/provider/capability')
    },
    getCapabilityTypes() {
        return request.get('/provider/capability/types')
    },
    getAvailableModels(capabilityType) {
        return request.get(`/provider/capability/${capabilityType}/models`)
    },
    updateCapability(capabilityType, modelConfigId, fallbackConfigId = null) {
        return request.put(`/provider/capability/${capabilityType}`, {
            modelConfigId,
            fallbackConfigId
        })
    },
    refreshCache() {
        return request.post('/provider/capability/refresh')
    }
}
