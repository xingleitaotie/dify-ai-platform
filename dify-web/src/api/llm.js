import request from './request'

export const llmApi = {
    // 同步对话
    chat(data) {
        return request.post('/llm/chat', data)
    }
}