import request from './request'

// ========== 工作流 API ==========
export const workflowApi = {
    // 创建工作流
    create(data) {
        return request.post('/workflow/create', data)
    },

    // 更新工作流
    update(id, data) {
        return request.put(`/workflow/update/${id}`, data)
    },

    // 获取工作流列表
    getList(appId) {
        return request.get('/workflow/list', { params: { appId } })
    },

    // 获取工作流详情
    getDetail(id) {
        return request.get(`/workflow/detail/${id}`)
    },

    // 删除工作流
    delete(id) {
        return request.delete(`/workflow/delete/${id}`)
    },

    // 发布工作流
    publish(id) {
        return request.post(`/workflow/publish/${id}`)
    },

    // 执行工作流
    execute(data) {
        return request.post('/workflow/execute', data)
    },

    // 获取执行历史
    getExecutions(workflowId, page, size) {
        return request.get('/workflow/executions', { params: { workflowId, page, size } })
    },

    // 获取执行详情
    getExecutionDetail(executionId) {
        return request.get(`/workflow/execution/${executionId}`)
    },

    // 获取节点输出类型
    getNodeOutputType(nodeId) {
        return request.get(`/workflow/node/output-type/${nodeId}`)
    },

    // 获取工作流所有节点的输出类型
    getNodeOutputTypes(workflowId) {
        return request.get(`/workflow/node/output-types/${workflowId}`)
    }
}

// 流式执行工作流
export const streamExecuteWorkflow = (data, onMessage, onError, onComplete) => {
    const token = localStorage.getItem('token')
    const url = '/api/workflow/stream/execute'

    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'token': token
        },
        body: JSON.stringify(data)
    }).then(response => {
        const reader = response.body.getReader()
        const decoder = new TextDecoder()

        function read() {
            reader.read().then(({ done, value }) => {
                if (done) {
                    onComplete?.()
                    return
                }

                const chunk = decoder.decode(value)
                const lines = chunk.split('\n')

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        try {
                            const data = JSON.parse(line.substring(6))
                            onMessage?.(data)
                        } catch (e) {
                            console.error('解析SSE数据失败', e)
                        }
                    }
                }

                read()
            }).catch(error => {
                onError?.(error)
            })
        }

        read()
    }).catch(error => {
        onError?.(error)
    })
}