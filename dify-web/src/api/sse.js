/**
 * 根据业务意图获取合适的温度参数
 */
const getTemperatureByIntent = (intent) => {
    const temperatureMap = {
        'code': 0.3,
        'data': 0.2,
        'general': 0.7,
        'rag': 0.5,
        'creative': 0.9,
        'streaming': 0.7
    }
    return temperatureMap[intent] || 0.7
}

/**
 * 修复碎片化内容
 */
const fixFragmentedContent = (content) => {
    if (!content) return content

    let fixed = content

    // 修复代码块标记
    fixed = fixed.replace(/`\s*``\s*python/g, '```python')
    fixed = fixed.replace(/```\s*python\s*/g, '```python\n')

    // 修复 Python import 语句
    fixed = fixed.replace(/import\s+(\w+)(\d+)\s+import\s+(\w+)\s+(\w+)/g,
        (match, pkg, num, module, next) => {
            return `import ${pkg}\nfrom bs${num} import ${module}\nimport ${next}`
        })

    // 确保代码块完整性
    const openBlocks = (fixed.match(/```/g) || []).length
    if (openBlocks % 2 !== 0) {
        fixed += '\n```'
    }

    return fixed
}

/**
 * 流式对话
 * @param {string} sessionId - 会话ID
 * @param {string} message - 用户消息
 * @param {number} configId - 模型配置ID
 * @param {string} intent - 意图
 * @param {Function} onMessage - 消息回调
 * @param {Function} onError - 错误回调
 * @param {Function} onComplete - 完成回调
 * @param {AbortSignal} signal - 取消信号
 */
export async function streamChat(sessionId, message, configId, intent, onMessage, onError, onComplete, signal) {
    const token = localStorage.getItem('token')
    const temperature = getTemperatureByIntent(intent)

    try {
        const response = await fetch('/api/llm/stream/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'token': token || ''
            },
            body: JSON.stringify({
                sessionId,
                message,
                configId,
                intent: intent,
                temperature,
                saveContext: true
            }),
            signal
        })

        if (!response.ok) {
            onError?.(new Error(`HTTP ${response.status}`))
            return
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        let fullContent = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) {
                const finalContent = fixFragmentedContent(fullContent)
                onMessage?.({ content: finalContent })
                onComplete?.()
                break
            }

            buffer += decoder.decode(value, { stream: true })
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''

            for (let line of lines) {
                line = line.trim()
                if (!line) continue

                let content = line
                if (line.startsWith('data:')) {
                    content = line.substring(5).trim()
                }

                if (content === '[DONE]') {
                    const finalContent = fixFragmentedContent(fullContent)
                    onMessage?.({ content: finalContent })
                    onComplete?.()
                    return
                }

                if (content && content !== '') {
                    fullContent += content
                    const fixedContent = fixFragmentedContent(fullContent)
                    onMessage?.({ content: fixedContent })
                }
            }
        }
    } catch (error) {
        if (error.name === 'AbortError') {
            console.log('Request aborted')
        } else {
            console.error('Stream error:', error)
            onError?.(error)
        }
    }
}