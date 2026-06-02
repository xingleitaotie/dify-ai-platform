import request from './request'

export const userApi = {
    register(data) {
        return request.post('/user/register', data)
    },

    login(data) {
        return request.post('/user/login', data)
    },

    createApp(data) {
        return request.post('/user/app/create', data)
    },

    getAppList() {
        return request.get('/user/app/list')
    },

    deleteApp(id) {
        return request.delete('/user/app/delete', { params: { id } })
    }
}