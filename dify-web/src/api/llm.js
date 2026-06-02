import request from './request'

export const llmApi = {
    chat(prompt) {
        return request.post('/llm/chat', prompt, {
            headers: { 'Content-Type': 'text/plain' }
        })
    },

    conditionChat(params) {
        return request.post('/llm/condition/chat', params)
    },

    ragChat(query, topK = 3) {
        return request.post('/llm/rag-qa/chat', { query, topK })
    },

    chatWithFunction(prompt) {
        return request.post('/llm/chat/function', prompt, {
            headers: { 'Content-Type': 'text/plain' }
        })
    }
}