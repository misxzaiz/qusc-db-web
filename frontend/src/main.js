import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ConnectionManager from './views/ConnectionManager.vue'
import SqlEditor from './views/SqlEditor.vue'
import AiSettings from './views/AiSettings.vue'
import AiChat from './views/AiChat.vue'
import './styles/theme.css'

const routes = [
  { path: '/', redirect: '/connections' },
  { path: '/connections', component: ConnectionManager },
  { path: '/editor', component: SqlEditor },
  { path: '/ai-settings', component: AiSettings },
  { path: '/ai-chat', component: AiChat }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

createApp(App).use(router).mount('#app')