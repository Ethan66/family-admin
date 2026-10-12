import { createApp } from 'vue'
import '@unocss/reset/tailwind.css'
import 'uno.css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import { ElMessage } from 'element-plus'
import App from './App.vue'
import { setAuthFailureHandler } from './api/client'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import './styles.css'
import './prototype.css'

setAuthFailureHandler(() => {
  const tempAuthStore = useAuthStore(pinia)
  if (!tempAuthStore.token) return
  tempAuthStore.logout()
  ElMessage.warning('登录已失效，请重新登录')
  void router.replace('/login')
})

createApp(App).use(pinia).use(router).mount('#app')
