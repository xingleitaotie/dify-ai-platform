import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { promptApi } from '@/api'

export function usePromptGenerator() {
    const visible = ref(false)
    const generating = ref(false)
    const result = ref({ prompt: '', confidenceScore: 0, suggestions: [] })

    const generate = async (formData) => {
        if (!formData.requirement) {
            ElMessage.warning('请输入需求描述')
            return false
        }
        generating.value = true
        try {
            const res = await promptApi.generate({
                requirement: formData.requirement,
                type: formData.type,
                language: formData.language
            })
            if (res && res.code === 200 && res.data) {
                result.value = {
                    prompt: res.data.prompt || res.data.userPromptTemplate || '',
                    confidenceScore: res.data.confidenceScore || 80,
                    suggestions: res.data.suggestions || []
                }
                ElMessage.success('生成成功')
                return true
            } else {
                ElMessage.error(res?.msg || '生成失败')
                return false
            }
        } catch (error) {
            ElMessage.error('生成失败：' + error.message)
            return false
        } finally {
            generating.value = false
        }
    }

    const reset = () => {
        result.value = { prompt: '', confidenceScore: 0, suggestions: [] }
    }

    const getConfidenceType = (score) => {
        if (score >= 80) return 'success'
        if (score >= 60) return 'warning'
        return 'info'
    }

    return {
        visible,
        generating,
        result,
        generate,
        reset,
        getConfidenceType
    }
}