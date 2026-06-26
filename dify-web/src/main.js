import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './style.css'
import { hasPermi, hasRole } from './directives/permission'
import { useMenuStore } from './stores/menu'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.directive('hasPermi', hasPermi)
app.directive('hasRole', hasRole)

app.use(pinia)
app.use(router)
app.use(ElementPlus)

app.mount('#app')

const menuStore = useMenuStore()
menuStore.loadMenus()
menuStore.loadPermissions()