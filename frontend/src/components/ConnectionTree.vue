<template>
  <div class="connection-tree">
    <div class="tree-header">
      <h3>数据库连接</h3>
      <button class="btn btn-small" @click="$emit('new-connection')">新建连接</button>
    </div>

    <div class="tree-content">
      <div v-for="session in activeSessions" :key="session.sessionId" class="tree-node">
        <!-- 连接节点 -->
        <div
          class="node-item connection-node"
          :class="{ active: currentSessionId === session.sessionId }"
          @click="selectConnection(session)"
        >
          <span class="expand-icon" @click.stop="toggleExpand(session.sessionId, 'connection', session.connectionInfo.name)">
            {{ isExpanded(session.sessionId, 'connection', session.connectionInfo.name) ? '▼' : '▶' }}
          </span>
          <span class="node-icon">🔗</span>
          <span class="node-label">{{ session.connectionInfo.name }}</span>
          <span class="node-status" :class="{ connected: true }">●</span>
        </div>

        <!-- 数据库列表 -->
        <div v-if="isExpanded(session.sessionId, 'connection', session.connectionInfo.name)" class="children">
          <div
            v-for="database in session.databases"
            :key="database"
            class="node-item database-node"
            :class="{ active: session.currentDatabase === database }"
            @click="selectDatabase(session, database)"
          >
            <span class="expand-icon" @click.stop="toggleExpand(session.sessionId, 'database', database)">
              {{ isExpanded(session.sessionId, 'database', database) ? '▼' : '▶' }}
            </span>
            <span class="node-icon">📁</span>
            <span class="node-label">{{ database }}</span>

            <!-- 表列表 -->
            <div v-if="isExpanded(session.sessionId, 'database', database)" class="children">
              <div
                v-for="table in session.tables[database] || []"
                :key="table"
                class="node-item table-node"
                @click="selectTable(session, table)"
              >
                <span class="node-icon">📊</span>
                <span class="node-label">{{ table }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="activeSessions.length === 0" class="empty-state">
        <p>暂无活跃连接</p>
        <p class="empty-hint">点击"新建连接"添加数据库连接</p>
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
  emits: ['new-connection', 'connection-selected', 'database-selected', 'table-selected'],

  data() {
    return {
      currentSessionId: null,
      currentSession: null
    }
  },

  computed: {
    activeSessions() {
      return connectionStore.getActiveSessions()
    }
  },

  mounted() {
    connectionStore.loadConnections()
    this.checkExistingSession()
  },

  methods: {
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

    selectTable(session, table) {
      this.$emit('table-selected', {
        session,
        table
      })
    },

    async toggleExpand(sessionId, type, value) {
      connectionStore.toggleNode(sessionId, type, value)

      // 如果展开数据库且未加载表，则加载
      if (type === 'database') {
        const session = connectionStore.getSession(sessionId)
        if (session && !session.tables[value]) {
          await connectionStore.loadTables(sessionId, value)
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
  margin-left: 24px;
  border-left: 1px solid var(--border-primary);
  padding-left: 8px;
  margin-left: 24px;
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
</style>