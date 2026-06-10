import { defineStore } from 'pinia'

export const useModelStore = defineStore('model', {
    state: () => ({
        // 缓存所有模型数据
        allModels: [],
        providers: [],
        capabilities: [],
        lastUpdateTime: null
    }),

    actions: {
        // 设置模型数据
        setAllModels(models) {
            this.allModels = models
            this.lastUpdateTime = Date.now()
        },

        // 设置供应商数据
        setProviders(providers) {
            this.providers = providers
        },

        // 设置能力配置
        setCapabilities(capabilities) {
            this.capabilities = capabilities
        },

        // 刷新所有模型数据
        async refreshAllModels() {
            // 触发重新加载，由各个组件监听并重新获取数据
            this.lastUpdateTime = Date.now()
        },

        // 清除缓存
        clearCache() {
            this.allModels = []
            this.providers = []
            this.capabilities = []
            this.lastUpdateTime = null
        }
    }
})