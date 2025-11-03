<template>
  <div class="connection-tree">

    <div class="tree-content">
      <!-- 所有连接（已保存的+活跃的） -->
      <div v-if="allConnections.length > 0">
        <div v-for="item in allConnections" :key="item.id || item.sessionId" class="tree-node">
          <!-- 连接节点 -->
          <div
            class="node-item connection-node"
            :class="{
              active: currentSessionId === item.sessionId,
              connected: item.sessionId && isConnected(item.connectionInfo?.id)
            }"
            @click="handleConnectionClick(item)"
            @contextmenu.prevent="handleConnectionContextMenu($event, item)"
          >
            <span
              class="expand-icon"
              @click.stop="item.sessionId ? toggleExpand(item.sessionId, 'connection', item.connectionInfo.name) : null"
            >
              {{ item.sessionId && isExpanded(item.sessionId, 'connection', item.connectionInfo.name) ? '▼' : '▶' }}
            </span>
            <span class="node-icon">
              <font-awesome-icon :icon="item.sessionId ? 'plug' : 'database'" />
            </span>
            <span class="node-label">{{ item.name || item.connectionInfo.name }}</span>
            <span class="node-status" :class="{ connected: item.sessionId }">
              {{ item.sessionId ? '●' : '○' }}
            </span>
          </div>

          <!-- 数据库列表（仅当连接活跃时显示） -->
          <div v-if="item.sessionId && isExpanded(item.sessionId, 'connection', item.connectionInfo.name)" class="children">
            <div
              v-for="database in item.databases"
              :key="database"
              class="node-item database-node"
              :class="{ active: item.currentDatabase === database }"
              @click="selectDatabase(item, database)"
            >
              <div>
                  <span class="expand-icon" @click.stop="toggleExpand(item.sessionId, 'database', database)">
                    {{ isExpanded(item.sessionId, 'database', database) ? '▼' : '▶' }}
                  </span>
                  <span class="node-icon">
                    <font-awesome-icon icon="folder" />
                  </span>
                  <span class="node-label">{{ database }}</span>
              </div>

              <!-- 数据库对象分类 -->
              <div v-if="isExpanded(item.sessionId, 'database', database)" class="children">
                <!-- 表 -->
                <div class="object-category">
                  <div
                    class="node-item category-node"
                    @click.stop="toggleExpand(item.sessionId, 'category', `${database}-tables`)"
                  >
                    <span class="expand-icon">
                      {{ isExpanded(item.sessionId, 'category', `${database}-tables`) ? '▼' : '▶' }}
                    </span>
                    <span class="node-icon">
                      <font-awesome-icon icon="table" />
                    </span>
                    <span class="node-label">表 ({{ (item.tables[database] || []).length }})</span>
                  </div>
                  <div v-if="isExpanded(item.sessionId, 'category', `${database}-tables`)" class="children">
                    <div
                      v-for="table in item.tables[database] || []"
                      :key="table"
                      class="node-item table-node"
                      @click="selectTable(item, database, table, 'TABLE')"
                    >
                      <span class="node-icon">
                        <font-awesome-icon icon="table" />
                      </span>
                      <span class="node-label">{{ table }}</span>
                    </div>
                  </div>
                </div>

                <!-- 视图 -->
                <div class="object-category">
                  <div
                    class="node-item category-node"
                    @click.stop="toggleExpand(item.sessionId, 'category', `${database}-views`)"
                  >
                    <span class="expand-icon">
                      {{ isExpanded(item.sessionId, 'category', `${database}-views`) ? '▼' : '▶' }}
                    </span>
                    <span class="node-icon">
                      <font-awesome-icon icon="eye" />
                    </span>
                    <span class="node-label">视图 ({{ (item.views[database] || []).length }})</span>
                  </div>
                  <div v-if="isExpanded(item.sessionId, 'category', `${database}-views`)" class="children">
                    <div
                      v-for="view in item.views[database] || []"
                      :key="view"
                      class="node-item view-node"
                      @click="selectTable(item, database, view, 'VIEW')"
                    >
                      <span class="node-icon">
                        <font-awesome-icon icon="eye" />
                      </span>
                      <span class="node-label">{{ view }}</span>
                    </div>
                  </div>
                </div>

                <!-- 存储过程 -->
                <div class="object-category">
                  <div
                    class="node-item category-node"
                    @click.stop="toggleExpand(item.sessionId, 'category', `${database}-procedures`)"
                  >
                    <span class="expand-icon">
                      {{ isExpanded(item.sessionId, 'category', `${database}-procedures`) ? '▼' : '▶' }}
                    </span>
                    <span class="node-icon">
                      <font-awesome-icon icon="cogs" />
                    </span>
                    <span class="node-label">存储过程 ({{ (item.procedures[database] || []).length }})</span>
                  </div>
                  <div v-if="isExpanded(item.sessionId, 'category', `${database}-procedures`)" class="children">
                    <div
                      v-for="procedure in item.procedures[database] || []"
                      :key="procedure"
                      class="node-item procedure-node"
                      @click="selectTable(item, database, procedure, 'PROCEDURE')"
                    >
                      <span class="node-icon">
                        <font-awesome-icon icon="cogs" />
                      </span>
                      <span class="node-label">{{ procedure }}</span>
                    </div>
                  </div>
                </div>

                <!-- 函数 -->
                <div class="object-category">
                  <div
                    class="node-item category-node"
                    @click.stop="toggleExpand(item.sessionId, 'category', `${database}-functions`)"
                  >
                    <span class="expand-icon">
                      {{ isExpanded(item.sessionId, 'category', `${database}-functions`) ? '▼' : '▶' }}
                    </span>
                    <span class="node-icon">
                      <font-awesome-icon icon="cube" />
                    </span>
                    <span class="node-label">函数 ({{ (item.functions[database] || []).length }})</span>
                  </div>
                  <div v-if="isExpanded(item.sessionId, 'category', `${database}-functions`)" class="children">
                    <div
                      v-for="func in item.functions[database] || []"
                      :key="func"
                      class="node-item function-node"
                      @click="selectTable(item, database, func, 'FUNCTION')"
                    >
                      <span class="node-icon">
                        <font-awesome-icon icon="cube" />
                      </span>
                      <span class="node-label">{{ func }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <p>暂无连接</p>
        <p class="empty-hint">请先在连接管理中创建数据库连接</p>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div
      v-show="contextMenu.visible"
      class="context-menu"
      :style="contextMenu.style"
      @click.stop
    >
      <div
        class="menu-item"
        @click="handleReconnect"
        :class="{ disabled: !contextMenu.connection || !contextMenu.connection.sessionId }"
      >
        <font-awesome-icon icon="refresh" />
        <span>重新连接</span>
      </div>
    </div>

    <!-- 连接状态栏 -->
    <div v-if="currentSession" class="status-bar">
      <div class="status-item">
        <span class="status-label">连接:</span>
        <span class="status-value">{{ currentSession.connectionInfo.name }}</span>
      </div>
      <div class="status-item">
        <span class="status-label">数据库:</span>
        <span class="status-value">{{ currentSession.currentDatabase || '未选择' }}</span>
      </div>
      <button class="btn btn-small disconnect-btn" @click="disconnect">
        断开
      </button>
    </div>
  </div>
</template>

<script>
import { connectionStore } from '../stores/connectionStore'
import { sessionStorage } from '../utils/storage'

export default {
  name: 'ConnectionTree',
  emits: ['connection-selected', 'database-selected', 'table-selected'],

  data() {
    return {
      currentSessionId: null,
      currentSession: null,
      savedConnections: [],
      // 右键菜单相关
      contextMenu: {
        visible: false,
        x: 0,
        y: 0,
        connection: null,
        style: {}
      }
    }
  },

  computed: {
    activeSessions() {
      return connectionStore.getActiveSessions()
    },

    // 合并已保存的连接和活跃会话，保持原始顺序
    allConnections() {
      const result = []

      // 遍历已保存的连接，保持原始顺序
      this.savedConnections.forEach(savedConn => {
        const activeSession = this.activeSessions.find(session =>
          session.connectionInfo.id === savedConn.id
        )
        if (activeSession) {
          // 如果连接活跃，使用活跃会话数据（包含数据库和表信息）
          result.push(activeSession)
        } else {
          // 如果未连接，使用保存的连接信息
          result.push(savedConn)
        }
      })

      // 添加没有对应保存连接的活跃会话（例如直接创建的会话）
      this.activeSessions.forEach(session => {
        if (!session.connectionInfo.id || !this.savedConnections.find(sc => sc.id === session.connectionInfo.id)) {
          result.push(session)
        }
      })

      return result
    }
  },

  mounted() {
    connectionStore.loadConnections()
    this.loadSavedConnections()
    this.checkExistingSession()

    // 监听localStorage变化（跨标签页同步）
    window.addEventListener('storage', (e) => {
      if (e.key === 'db_connections') {
        this.loadSavedConnections()
      }
    })
  },

  beforeUnmount() {
    // 清理右键菜单事件监听
    if (this.contextMenu.visible) {
      document.removeEventListener('click', this.hideContextMenu)
    }
  },

  methods: {
    loadSavedConnections() {
      const saved = localStorage.getItem('db_connections')
      if (saved) {
        this.savedConnections = JSON.parse(saved)
      }
    },

    isConnected(connectionId) {
      return this.activeSessions.some(session =>
        session.connectionInfo.id === connectionId
      )
    },

    async handleConnectionClick(item) {
      // 如果是已保存的连接（未连接），则连接
      if (!item.sessionId) {
        try {
          const sessionId = await connectionStore.connect(item)
          this.selectSession(sessionId)
          const session = connectionStore.getSession(sessionId)
          this.$emit('connection-selected', session)
        } catch (error) {
          alert('连接失败: ' + (error.response?.data?.error || error.message))
        }
      } else {
        // 如果是已连接的会话，直接选择
        this.selectSession(item.sessionId)
        this.$emit('connection-selected', item)
      }
    },

    checkExistingSession() {
      const savedSessionId = sessionStorage.get('sessionId')
      if (savedSessionId && savedSessionId.sessionId && connectionStore.getSession(savedSessionId.sessionId)) {
        this.selectSession(savedSessionId.sessionId)
      }
    },

    selectSession(sessionId) {
      this.currentSessionId = sessionId
      this.currentSession = connectionStore.getSession(sessionId)
      sessionStorage.save('sessionId', { sessionId })
    },

    selectConnection(session) {
      this.selectSession(session.sessionId)
      this.$emit('connection-selected', session)
    },

    async selectDatabase(session, database) {
      this.selectSession(session.sessionId)
      await connectionStore.switchDatabase(session.sessionId, database)
      this.$emit('database-selected', {
        session,
        database
      })
    },

    selectTable(session, database, name, type) {
      this.$emit('table-selected', {
        session,
        database,
        name,
        type
      })
    },

    async toggleExpand(sessionId, type, value) {
      connectionStore.toggleNode(sessionId, type, value)

      // 如果展开数据库且未加载对象，则加载
      if (type === 'database') {
        const session = connectionStore.getSession(sessionId)
        if (session) {
          if (!session.tables[value]) {
            await connectionStore.loadTables(sessionId, value)
          }
          if (!session.views[value]) {
            await connectionStore.loadViews(sessionId, value)
          }
          if (!session.procedures[value]) {
            await connectionStore.loadProcedures(sessionId, value)
          }
          if (!session.functions[value]) {
            await connectionStore.loadFunctions(sessionId, value)
          }
        }
      }
    },

    isExpanded(sessionId, type, value) {
      return connectionStore.isNodeExpanded(sessionId, type, value)
    },

    async disconnect() {
      if (!this.currentSessionId) return

      if (confirm('确定要断开当前连接吗？')) {
        await connectionStore.disconnect(this.currentSessionId)
        this.currentSessionId = null
        this.currentSession = null
        sessionStorage.remove('sessionId')
        this.$emit('connection-selected', null)
      }
    },

    // 刷新已保存的连接列表
    refreshSavedConnections() {
      this.loadSavedConnections()
    },

    // 处理连接节点右键
    handleConnectionContextMenu(event, connection) {
      // 只对已连接的连接显示重新连接
      if (!connection.sessionId) return

      this.contextMenu.connection = connection
      this.contextMenu.visible = true

      // 计算位置
      const x = event.clientX
      const y = event.clientY

      // 防止超出视窗
      const menuHeight = 40
      const adjustedY = y + menuHeight > window.innerHeight
        ? window.innerHeight - menuHeight - 5
        : y

      this.contextMenu.style = {
        left: `${x}px`,
        top: `${adjustedY}px`
      }

      // 点击其他地方关闭
      document.addEventListener('click', this.hideContextMenu)
    },

    // 隐藏右键菜单
    hideContextMenu() {
      this.contextMenu.visible = false
      document.removeEventListener('click', this.hideContextMenu)
    },

    // 重新连接实现
    async handleReconnect() {
      const connection = this.contextMenu.connection
      if (!connection || !connection.sessionId) return

      try {
        // 保存当前状态
        const wasCurrent = this.currentSessionId === connection.sessionId
        const connectionInfo = connection.connectionInfo

        // 断开连接
        await connectionStore.disconnect(connection.sessionId)

        // 重新连接
        const newSessionId = await connectionStore.connect(connectionInfo)

        // 如果是当前选中的连接，更新选中状态
        if (wasCurrent) {
          this.selectSession(newSessionId)
          const session = connectionStore.getSession(newSessionId)
          this.$emit('connection-selected', session)
        }

        // 简单的成功提示
        console.log('重新连接成功')

      } catch (error) {
        alert('重新连接失败: ' + (error.response?.data?.error || error.message))
      }

      this.hideContextMenu()
    }
  }
}
</script>

<style scoped>
@import '../styles/theme.css';

.connection-tree {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-secondary);
  border-right: 1px solid var(--border-primary);
}

.tree-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.tree-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

.tree-node {
  margin-bottom: 2px;
}

.node-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
  font-size: 13px;
}

.database-node {
  flex-direction: column;
  align-items: flex-start;
}

.database-node > span {
  display: inline-flex;
  align-items: center;
}

.node-item:hover {
  background-color: var(--bg-highlight);
}

.node-item.active {
  background-color: var(--bg-quaternary);
}

.node-item.connection-node.active {
  background-color: rgba(86, 156, 214, 0.2);
}

.expand-icon {
  width: 16px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 10px;
  margin-right: 2px;
}

.node-icon {
  margin-right: 6px;
  font-size: 14px;
}

.node-label {
  flex: 1;
  color: var(--text-primary);
}

.node-status {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--text-disabled);
  margin-left: 8px;
}

.node-status.connected {
  background-color: var(--success);
}

.children {
  margin-top: 4px;
  padding-left: 20px;
  width: 100%;
}

.object-category {
  margin-bottom: 2px;
}

.category-node {
  font-weight: 500;
  color: var(--text-secondary);
}

.category-node:hover {
  color: var(--text-primary);
}

.table-node .node-icon {
  color: var(--accent-primary);
}

.view-node .node-icon {
  color: var(--info);
}

.procedure-node .node-icon {
  color: var(--warning);
}

.function-node .node-icon {
  color: var(--success);
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: var(--text-secondary);
}

.empty-hint {
  margin-top: var(--spacing-sm);
  font-size: 12px;
  color: var(--text-tertiary);
}

.status-bar {
  padding: var(--spacing-md);
  border-top: 1px solid var(--border-primary);
  background-color: var(--bg-tertiary);
}

.status-item {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-sm);
  font-size: 12px;
}

.status-item:last-child {
  margin-bottom: 0;
  align-items: center;
  justify-content: space-between;
}

.status-label {
  color: var(--text-secondary);
  margin-right: var(--spacing-sm);
  min-width: 50px;
}

.status-value {
  color: var(--text-primary);
  font-family: var(--font-family-mono);
}

.disconnect-btn {
  padding: 4px 12px;
  font-size: 11px;
}

/* 右键菜单样式 */
.context-menu {
  position: fixed;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  padding: 4px 0;
  z-index: 1000;
  min-width: 120px;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.menu-item:hover {
  background-color: var(--bg-highlight);
}

.menu-item.disabled {
  color: var(--text-disabled);
  cursor: not-allowed;
}

.menu-item.disabled:hover {
  background-color: transparent;
}

.menu-item svg {
  margin-right: 8px;
  font-size: 14px;
}
</style>