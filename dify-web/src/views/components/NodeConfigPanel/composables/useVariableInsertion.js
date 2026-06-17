import { ElMessage } from 'element-plus'

/**
 * 变量插入 Composable
 * @param {Object} target - 响应式目标对象
 * @param {string} field - 要插入的字段名
 */
export function useVariableInsertion(target, field) {
    const insertVariable = (varPath, template = '{{%s}}') => {
        const variable = template.replace('%s', varPath)
        const currentValue = target[field] || ''
        target[field] = currentValue + variable
        ElMessage.info(`已插入变量: ${variable}`)
    }

    const insertAtCursor = (textareaElement, varPath) => {
        if (!textareaElement) return
        const variable = `{{${varPath}}}`
        const start = textareaElement.selectionStart
        const end = textareaElement.selectionEnd
        const currentValue = textareaElement.value || ''
        const newValue = currentValue.substring(0, start) + variable + currentValue.substring(end)
        textareaElement.value = newValue
        textareaElement.dispatchEvent(new Event('input'))
    }

    return { insertVariable, insertAtCursor }
}