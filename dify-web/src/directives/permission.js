import { useMenuStore } from '@/stores/menu'

export const hasPermi = {
    mounted(el, binding) {
        const { value } = binding
        const menuStore = useMenuStore()
        
        if (value && value instanceof Array && value.length > 0) {
            const hasPermission = value.some(permission => menuStore.hasPermission(permission))
            
            if (!hasPermission) {
                el.style.display = 'none'
            }
        } else {
            el.style.display = 'none'
        }
    }
}

export const hasRole = {
    mounted(el, binding) {
        const { value } = binding
        const menuStore = useMenuStore()
        
        if (value && value instanceof Array && value.length > 0) {
            const hasRole = value.some(role => menuStore.roles?.includes(role))
            
            if (!hasRole) {
                el.style.display = 'none'
            }
        } else {
            el.style.display = 'none'
        }
    }
}
