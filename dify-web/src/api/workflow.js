// dify-web/src/api/workflow.js
import request from './request';

// 创建工作流
export const createWorkflow = (data) => {
    return request.post('/workflow/create', data);
};

// 更新工作流
export const updateWorkflow = (id, data) => {
    return request.put(`/workflow/update/${id}`, data);
};

// 获取工作流列表
export const getWorkflowList = (appId) => {
    return request.get('/workflow/list', { params: { appId } });
};

// 获取工作流详情
export const getWorkflowDetail = (id) => {
    return request.get(`/workflow/detail/${id}`);
};

// 删除工作流
export const deleteWorkflow = (id) => {
    return request.delete(`/workflow/delete/${id}`);
};

// 发布工作流
export const publishWorkflow = (id) => {
    return request.post(`/workflow/publish/${id}`);
};

// 执行工作流
export const executeWorkflow = (data) => {
    return request.post('/workflow/execute', data);
};

// 流式执行工作流（使用EventSource）
export const streamExecuteWorkflow = (data, onMessage, onError, onComplete) => {
    const token = localStorage.getItem('token');
    const url = '/api/workflow/stream/execute';

    // 使用fetch进行流式请求
    fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'token': token
        },
        body: JSON.stringify(data)
    }).then(response => {
        const reader = response.body.getReader();
        const decoder = new TextDecoder();

        function read() {
            reader.read().then(({ done, value }) => {
                if (done) {
                    if (onComplete) onComplete();
                    return;
                }

                const chunk = decoder.decode(value);
                const lines = chunk.split('\n');

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        try {
                            const data = JSON.parse(line.substring(6));
                            if (onMessage) onMessage(data);
                        } catch (e) {
                            console.error('解析SSE数据失败', e);
                        }
                    } else if (line.startsWith('event: ')) {
                        // 事件类型，可以忽略或处理
                        console.log('Event type:', line.substring(7));
                    }
                }

                read();
            }).catch(error => {
                if (onError) onError(error);
            });
        }

        read();
    }).catch(error => {
        if (onError) onError(error);
    });
};

// 获取执行历史
export const getExecutions = (workflowId, page, size) => {
    return request.get('/workflow/executions', {
        params: { workflowId, page, size }
    });
};

// 获取执行详情
export const getExecutionDetail = (executionId) => {
    return request.get(`/workflow/execution/${executionId}`);
};

export const workflowApi = {
    // 获取节点输出类型
    getNodeOutputType(nodeId) {
        return request({
            url: `/workflow/node/output-type/${nodeId}`,
            method: 'get'
        })
    },

    // 获取工作流所有节点的输出类型
    getNodeOutputTypes(workflowId) {
        return request({
            url: `/workflow/node/output-types/${workflowId}`,
            method: 'get'
        })
    }
}