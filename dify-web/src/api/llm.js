import request from './request'

export const llmApi = {
    chat(data) {
        return request.post('/llm/chat', data)
    }
}