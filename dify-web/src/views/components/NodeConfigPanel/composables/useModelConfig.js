import { ref } from 'vue'
import { ElMessage } from 'element-plus'
// import { modelConfigApi } from '@/api'

export function useModelConfig() {
    const modelList = ref([])
    const selectedModel = ref(null)

    const loadModels = async () => {
        try {
            // TODO: 替换为实际的 API 调用
            // const res = await modelConfigApi.getEnabledConfigs()
            // if (res.code === 200 && res.data) {
            //   modelList.value = res.data
            // }

            // 临时模拟数据
            modelList.value = [
                { id: 1, configName: 'GPT-3.5 Turbo', type: 'openai', modelName: 'gpt-3.5-turbo', temperature: 0.7, maxTokens: 4096 },
                { id: 2, configName: 'Qwen-Turbo', type: 'qwen', modelName: 'qwen-turbo', temperature: 0.7, maxTokens: 2048 }
            ]
        } catch (error) {
            console.error('加载模型配置失败:', error)
            ElMessage.error('加载模型配置失败')
        }
    }

    const getTypeLabel = (type) => {
        const labels = {
            openai: 'OpenAI',
            ollama: 'Ollama',
            modelscope: 'ModelScope',
            qwen: '通义千问',
            ernie: '文心一言',
            spark: '讯飞星火',
            zhipu: '智谱AI'
        }
        return labels[type] || type
    }

    return {
        modelList,
        selectedModel,
        loadModels,
        getTypeLabel
    }
}