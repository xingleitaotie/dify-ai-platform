// stores/modelStore.js
import { defineStore } from 'pinia'

export const useModelStore = defineStore('model', {
    state: () => ({
        // 缓存所有模型数据
        allModels: [],
        providers: [],
        capabilities: [],
        lastUpdateTime: null,
        // 新增：数据变更版本号，每次变更递增
        dataVersion: 0
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

        // 刷新所有模型数据（触发重新加载）
        async refreshAllModels() {
            // 触发重新加载，由各个组件监听并重新获取数据
            this.lastUpdateTime = Date.now()
        },

        // 新增：标记数据已变更（轻量级，不触发加载）
        markDataChanged() {
            this.dataVersion++
            this.lastUpdateTime = Date.now()
            console.log('模型数据已变更，版本号:', this.dataVersion)
        },

        // 新增：获取当前数据版本号
        getDataVersion() {
            return this.dataVersion
        },

        // 清除缓存
        clearCache() {
            this.allModels = []
            this.providers = []
            this.capabilities = []
            this.lastUpdateTime = null
            this.dataVersion = 0
        }
    }
})