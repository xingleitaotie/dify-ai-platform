import request from './request'

export const functionApi = {
    getFunctionList() {
        return request.get('/function/list')
    },

    callFunction(data) {
        return request.post('/function/call', data)
    }
}