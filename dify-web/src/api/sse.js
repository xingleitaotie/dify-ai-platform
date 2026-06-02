export async function streamChat(sessionId, message, configId, onMessage, onError, onComplete, signal) {
    const token = localStorage.getItem('token')

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
                configId  // 传入模型配置ID
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
                    onComplete?.()
                    return
                }

                if (content && content !== '') {
                    fullContent += content
                    onMessage?.({ content: fullContent })
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

// 普通对话
export async function sendChatMessage(sessionId, message, modelType, signal) {
    const token = localStorage.getItem('token')

    const response = await fetch('/api/llm/chat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'token': token || ''
        },
        body: JSON.stringify({
            sessionId,
            message,
            modelType
        }),
        signal
    })

    return response.json()
}