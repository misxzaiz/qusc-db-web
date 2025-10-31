import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import { library } from '@fortawesome/fontawesome-svg-core'
import {
  faDatabase,
  faPlus,
  faTrash,
  faEdit,
  faPlay,
  faCog,
  faHistory,
  faChevronRight,
  faChevronDown,
  faTable,
  faColumns,
  faKey,
  faServer,
  faPlug,
  faUnlink,
  faEye,
  faEyeSlash,
  faCopy,
  faDownload,
  faUpload,
  faSave,
  faUndo,
  faRedo,
  faSearch,
  faFilter,
  faSort,
  faSortUp,
  faSortDown,
  faRefresh,
  faExpand,
  faCompress,
  faTimes,
  faCheck,
  faExclamationTriangle,
  faInfoCircle,
  faQuestionCircle,
  faRobot,
  faComments,
  faPaperPlane,
  faCode,
  faFileAlt,
  faFolder,
  faFolderOpen,
  faBars,
  faAngleLeft,
  faAngleRight,
  faAngleUp,
  faAngleDown,
  faEllipsisV,
  faGear,
  faFloppyDisk,
  faLightbulb,
  faBolt,
  faStar,
  faUser,
  faSpinner
} from '@fortawesome/free-solid-svg-icons'
import App from './App.vue'
import ConnectionManager from './views/ConnectionManager.vue'
import SqlEditor from './views/SqlEditor.vue'
import AiSettings from './views/AiSettings.vue'
import AiChat from './views/AiChat.vue'
import './styles/theme.css'

// 添加图标到库
library.add(
  faDatabase,
  faPlus,
  faTrash,
  faEdit,
  faPlay,
  faCog,
  faHistory,
  faChevronRight,
  faChevronDown,
  faTable,
  faColumns,
  faKey,
  faServer,
  faPlug,
  faUnlink,
  faEye,
  faEyeSlash,
  faCopy,
  faDownload,
  faUpload,
  faSave,
  faUndo,
  faRedo,
  faSearch,
  faFilter,
  faSort,
  faSortUp,
  faSortDown,
  faRefresh,
  faExpand,
  faCompress,
  faTimes,
  faCheck,
  faExclamationTriangle,
  faInfoCircle,
  faQuestionCircle,
  faRobot,
  faComments,
  faPaperPlane,
  faCode,
  faFileAlt,
  faFolder,
  faFolderOpen,
  faBars,
  faAngleLeft,
  faAngleRight,
  faAngleUp,
  faAngleDown,
  faEllipsisV,
  faGear,
  faFloppyDisk,
  faLightbulb,
  faBolt,
  faStar,
  faUser,
  faSpinner
)

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

createApp(App)
  .component('font-awesome-icon', FontAwesomeIcon)
  .use(router)
  .mount('#app')