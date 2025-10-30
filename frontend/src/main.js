import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import ConnectionManager from './views/ConnectionManager.vue'
import SqlEditor from './views/SqlEditor.vue'
import './styles/theme.css'

const routes = [
  { path: '/', redirect: '/connections' },
  { path: '/connections', component: ConnectionManager },
  { path: '/editor', component: SqlEditor }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

createApp(App).use(router).mount('#app')