// 本地存储工具
export const storage = {
    get(key) {
        const value = localStorage.getItem(key)
        if (!value) return null
        try {
            return JSON.parse(value)
        } catch {
            return value
        }
    },

    set(key, value) {
        if (typeof value === 'object') {
            localStorage.setItem(key, JSON.stringify(value))
        } else {
            localStorage.setItem(key, value)
        }
    },

    remove(key) {
        localStorage.removeItem(key)
    },

    clear() {
        localStorage.clear()
    },

    // 会话存储
    session: {
        get(key) {
            const value = sessionStorage.getItem(key)
            if (!value) return null
            try {
                return JSON.parse(value)
            } catch {
                return value
            }
        },

        set(key, value) {
            if (typeof value === 'object') {
                sessionStorage.setItem(key, JSON.stringify(value))
            } else {
                sessionStorage.setItem(key, value)
            }
        },

        remove(key) {
            sessionStorage.removeItem(key)
        },

        clear() {
            sessionStorage.clear()
        }
    }
}