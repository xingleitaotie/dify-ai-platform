import request from './request'

export const agentApi = {
    // Agent 管理
    list() {
        return request.get('/agent/list')
    },

    detail(id) {
        return request.get('/agent/detail', { params: { id } })
    },

    create(data) {
        return request.post('/agent/create', data)
    },

    update(data) {
        return request.put('/agent/update', data)
    },

    delete(id) {
        return request.delete('/agent/delete', { params: { id } })
    },

    // 知识库绑定
    bindKb(data) {
        return request.post('/agent/bind/kb', data)
    },

    // 工具绑定
    bindTool(data) {
        return request.post('/agent/bind/tool', data)
    },

    getToolList(agentId) {
        return request.get('/agent/tool/list', { params: { agentId } })
    },

    // 执行
    execute(data) {
        return request.post('/agent/execute', data)
    },

    chat(data) {
        return request.post('/agent/chat', data)
    },

    // 获取 Agent 绑定的知识库列表
    getKbList(agentId) {
        return request.get('/agent/kb/list', { params: { agentId } })
    },
    // 解绑知识库
    unbindKb(bindId) {
        return request.delete(`/agent/kb/unbind/${bindId}`)
    },

    // 更新知识库绑定配置
    updateKbBind(bindId, data) {
        return request.put(`/agent/kb/${bindId}`, data)
    },

    // 解绑工具
    unbindTool(bindId) {
        return request.delete(`/agent/tool/unbind/${bindId}`)
    },

    // 更新工具状态
    updateToolStatus(bindId, isEnabled) {
        // 将布尔值转换为数字：true -> 1, false -> 0
        const enabledValue = isEnabled ? 1 : 0
        return request.put(`/agent/tool/${bindId}/status?isEnabled=${enabledValue}`)
    },

    // 流式对话
    async streamChat(agentId, query, sessionId, onMessage, onError, onComplete) {
        const token = localStorage.getItem('token')
        const url = '/api/agent/stream/chat'

        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'token': token || ''
            },
            body: JSON.stringify({ agentId, query, sessionId })
        })

        if (!response.ok) {
            onError?.(new Error('请求失败'))
            return
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
            const { done, value } = await reader.read()
            if (done) {
                onComplete?.()
                break
            }

            buffer += decoder.decode(value, { stream: true })

            // 按行分割
            const lines = buffer.split('\n')
            buffer = lines.pop() || ''

            for (const line of lines) {
                const trimmedLine = line.trim()
                if (!trimmedLine) continue

                // 处理 data: 开头的内容
                if (trimmedLine.startsWith('data:')) {
                    let content = trimmedLine.substring(5).trim()

                    // 检查是否是结束标记
                    if (content === '[DONE]') {
                        onComplete?.()
                        return
                    }

                    // 有内容时回调 - 每个 data 块都单独回调
                    if (content) {
                        onMessage?.({ content: content })
                    }
                }
            }
        }
    }
}