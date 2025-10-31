<template>
  <div class="simple-sidebar" :class="{ collapsed: isCollapsed }">
    <!-- 导航图标 -->
    <div class="nav-icons">
      <button
        class="nav-icon"
        :class="{ active: !isCollapsed && activeTab === 'connections' }"
        @click="toggleTab('connections')"
        title="数据库连接"
      >
        <font-awesome-icon icon="database" />
      </button>
      <button
        class="nav-icon"
        :class="{ active: !isCollapsed && activeTab === 'history' }"
        @click="toggleTab('history')"
        title="查询历史"
      >
        <font-awesome-icon icon="history" />
      </button>
    </div>

    <!-- 弹出面板 -->
    <div v-if="!isCollapsed" class="popup-panel">
      <!-- 连接管理 -->
      <div v-if="activeTab === 'connections'" class="panel-content">
        <ConnectionTree />
      </div>

      <!-- 查询历史 -->
      <div v-if="activeTab === 'history'" class="panel-content">
        <QueryHistory
          ref="queryHistoryRef"
          @select="onHistorySelect"
          @copy="onHistoryCopy"
          @execute="onHistoryExecute"
          @clear="onHistoryClear"
        />
      </div>
    </div>
  </div>
</template>

<script>
import ConnectionTree from './ConnectionTree.vue'
import QueryHistory from './QueryHistory.vue'

export default {
  name: 'SimpleSidebar',

  components: {
    ConnectionTree,
    QueryHistory
  },

  data() {
    return {
      isCollapsed: true,
      activeTab: 'connections',
      queryHistoryRef: null
    }
  },

  mounted() {
    this.loadSidebarState()
    // 确保初始宽度正确设置
    const initialWidth = this.isCollapsed ? 40 : 250
    this.$emit('resize', initialWidth)

    // 向父组件暴露QueryHistory引用
    this.$nextTick(() => {
      this.$emit('query-history-ready', this.queryHistoryRef)
    })
  },

  watch: {
    isCollapsed() {
      this.$emit('resize', this.isCollapsed ? 40 : 250)
      this.saveSidebarState()
    },

    activeTab() {
      this.saveSidebarState()
    }
  },

  methods: {
    toggleTab(tab) {
      if (this.isCollapsed) {
        // 如果折叠状态，展开并切换到该标签
        this.isCollapsed = false
        this.activeTab = tab
      } else if (this.activeTab === tab) {
        // 如果已经展开且是当前标签，则折叠
        this.isCollapsed = true
      } else {
        // 如果展开但不是当前标签，切换标签
        this.activeTab = tab
      }
    },

    loadSidebarState() {
      try {
        const state = localStorage.getItem('simple_sidebar_state')
        if (state) {
          const parsed = JSON.parse(state)
          this.isCollapsed = parsed.isCollapsed !== false
          this.activeTab = parsed.activeTab || 'connections'
        }
      } catch (error) {
        console.error('加载侧边栏状态失败:', error)
      }
    },

    saveSidebarState() {
      const state = {
        isCollapsed: this.isCollapsed,
        activeTab: this.activeTab
      }
      localStorage.setItem('simple_sidebar_state', JSON.stringify(state))
    },

    // 查询历史事件处理
    onHistorySelect(sql) {
      this.$emit('history-select', sql)
    },

    onHistoryCopy(sql) {
      this.$emit('history-copy', sql)
    },

    onHistoryExecute(sql) {
      this.$emit('history-execute', sql)
    },

    onHistoryClear() {
      this.$emit('history-clear')
    }
  }
}
</script>

<style scoped>
.simple-sidebar {
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-primary);
  display: flex;
  flex-direction: column;
  height: 100vh;
  position: relative;
  z-index: 100;
  flex-shrink: 0;
}

.nav-icons {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px 0;
  gap: 10px;
}

.nav-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
  position: relative;
}

.nav-icon:hover {
  background-color: var(--bg-hover);
  color: var(--text-primary);
}

.nav-icon.active {
  background-color: var(--accent-primary);
  color: white;
}

.nav-icon.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background-color: var(--accent-primary);
}

.nav-icon svg {
  width: 16px;
  height: 16px;
}

.popup-panel {
  position: absolute;
  left: 40px;
  top: 0;
  bottom: 0;
  width: 250px;
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-primary);
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
}

.panel-content {
  height: 100%;
  overflow: auto;
}
</style>