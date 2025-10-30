<template>
  <div class="sql-editor">
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>数据库连接</h3>
        <select v-model="selectedConnectionId" @change="handleConnectionChange" class="connection-select">
          <option value="">选择连接</option>
          <option v-for="conn in connections" :key="conn.id" :value="conn.id">
            {{ conn.name }}
          </option>
        </select>
        <button v-if="selectedConnection" class="btn btn-small btn-connect" @click="toggleConnection">
          {{ isConnected ? '断开' : '连接' }}
        </button>
      </div>

      <div v-if="isConnected" class="tables-section">
        <h4>数据表</h4>
        <div v-if="loadingTables" class="loading">加载中...</div>
        <div v-else-if="tables.length === 0" class="empty">无数据表</div>
        <ul v-else class="table-list">
          <li v-for="table in tables" :key="table" @click="insertTableName(table)">
            {{ table }}
          </li>
        </ul>
      </div>
    </div>

    <div class="main-content">
      <div class="editor-section">
        <div class="editor-toolbar">
          <button class="btn btn-primary" @click="executeQuery" :disabled="!isConnected || !sqlText.trim()">
            执行查询 (F5)
          </button>
          <button class="btn btn-secondary" @click="executeUpdate" :disabled="!isConnected || !sqlText.trim()">
            执行更新
          </button>
          <button class="btn btn-secondary" @click="clearEditor">清空</button>
          <span v-if="queryResult" class="result-info">
            {{ queryResult.data ? `${queryResult.data.length} 行` : `${queryResult.affectedRows} 行受影响` }}
          </span>
        </div>

        <textarea
          v-model="sqlText"
          class="sql-textarea"
          placeholder="输入SQL语句..."
          @keydown="handleKeyDown"
          spellcheck="false"
        ></textarea>
      </div>

      <div v-if="error" class="error-section">
        <h4>错误信息</h4>
        <pre class="error-message">{{ error }}</pre>
      </div>

      <div v-if="queryResult && queryResult.data" class="result-section">
        <div class="result-header">
          <h4>查询结果</h4>
          <span class="row-count">{{ queryResult.data.length }} 行</span>
        </div>
        <div class="table-wrapper">
          <table class="result-table">
            <thead>
              <tr>
                <th v-for="column in columns" :key="column">{{ column }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in queryResult.data" :key="index">
                <td v-for="column in columns" :key="column">
                  {{ formatValue(row[column]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="queryResult && queryResult.affectedRows !== undefined" class="result-section">
        <div class="success-message">
          执行成功，{{ queryResult.affectedRows }} 行受影响
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { connectionApi, sqlApi } from '../services/api'
import { connectionStorage, sessionStorage } from '../utils/storage'

export default {
  name: 'SqlEditor',
  data() {
    return {
      connections: [],
      selectedConnectionId: '',
      selectedConnection: null,
      sessionId: '',
      isConnected: false,
      tables: [],
      loadingTables: false,
      sqlText: '',
      queryResult: null,
      error: null,
      columns: []
    }
  },

  mounted() {
    this.loadConnections()
    this.checkExistingConnection()
  },

  beforeUnmount() {
    if (this.isConnected && this.sessionId) {
      this.disconnect()
    }
  },

  methods: {
    loadConnections() {
      this.connections = connectionStorage.getAll()
    },

    checkExistingConnection() {
      const savedSessionId = sessionStorage.get('sessionId')
      const savedConnection = sessionStorage.get(savedSessionId?.sessionId)

      if (savedSessionId && savedConnection) {
        this.sessionId = savedSessionId.sessionId
        this.selectedConnection = savedConnection
        this.selectedConnectionId = savedConnection.id
        this.isConnected = true
        this.loadTables()
      }
    },

    async handleConnectionChange() {
      if (this.selectedConnectionId) {
        this.selectedConnection = this.connections.find(c => c.id === this.selectedConnectionId)
        if (this.isConnected) {
          await this.disconnect()
        }
      } else {
        this.selectedConnection = null
      }
    },

    async toggleConnection() {
      if (this.isConnected) {
        await this.disconnect()
      } else {
        await this.connect()
      }
    },

    async connect() {
      if (!this.selectedConnection) {
        alert('请选择连接')
        return
      }

      try {
        const response = await connectionApi.connect(this.selectedConnection)
        this.sessionId = response.data.sessionId
        this.isConnected = true

        // 保存session信息
        sessionStorage.save(this.sessionId, this.selectedConnection)
        sessionStorage.save('sessionId', { sessionId: this.sessionId })

        this.loadTables()
        alert('连接成功')
      } catch (error) {
        alert('连接失败: ' + error.response?.data?.error || error.message)
      }
    },

    async disconnect() {
      if (!this.sessionId) return

      try {
        await connectionApi.disconnect(this.sessionId)
      } catch (error) {
        console.error('断开连接失败', error)
      }

      this.isConnected = false
      this.sessionId = ''
      this.tables = []
      this.queryResult = null
      this.error = null

      // 清理session信息
      sessionStorage.remove(this.sessionId)
      sessionStorage.remove('sessionId')
    },

    async loadTables() {
      if (!this.sessionId) return

      this.loadingTables = true
      try {
        const response = await sqlApi.getTables(this.sessionId)
        this.tables = response.data.tables
      } catch (error) {
        console.error('加载表列表失败', error)
        this.tables = []
      }
      this.loadingTables = false
    },

    async executeQuery() {
      if (!this.sessionId || !this.sqlText.trim()) {
        return
      }

      this.error = null
      this.queryResult = null

      try {
        const response = await sqlApi.query(this.sessionId, this.sqlText.trim())
        this.queryResult = response.data

        if (this.queryResult.data && this.queryResult.data.length > 0) {
          this.columns = Object.keys(this.queryResult.data[0])
        }
      } catch (error) {
        this.error = error.response?.data?.error || error.message
      }
    },

    async executeUpdate() {
      if (!this.sessionId || !this.sqlText.trim()) {
        return
      }

      if (!confirm('确定要执行此更新语句吗？')) {
        return
      }

      this.error = null
      this.queryResult = null

      try {
        const response = await sqlApi.execute(this.sessionId, this.sqlText.trim())
        this.queryResult = response.data
      } catch (error) {
        this.error = error.response?.data?.error || error.message
      }
    },

    clearEditor() {
      this.sqlText = ''
      this.queryResult = null
      this.error = null
    },

    insertTableName(tableName) {
      this.sqlText += `${tableName}\n`
    },

    handleKeyDown(e) {
      if (e.key === 'F5') {
        e.preventDefault()
        this.executeQuery()
      }
    },

    formatValue(value) {
      if (value === null) return 'NULL'
      if (value === undefined) return ''
      return String(value)
    }
  }
}
</script>

<style scoped>
.sql-editor {
  height: 100%;
  display: flex;
}

.sidebar {
  width: 250px;
  background-color: #252526;
  border-right: 1px solid #3e3e42;
  padding: 20px;
  overflow-y: auto;
}

.sidebar-header h3 {
  margin: 0 0 12px 0;
  color: #d4d4d4;
  font-size: 14px;
}

.connection-select {
  width: 100%;
  padding: 6px 10px;
  background-color: #3c3c3c;
  border: 1px solid #5a5a5a;
  border-radius: 4px;
  color: #d4d4d4;
  font-size: 13px;
  margin-bottom: 8px;
}

.btn-connect {
  width: 100%;
  margin-top: 8px;
  font-size: 13px;
}

.tables-section {
  margin-top: 24px;
}

.tables-section h4 {
  margin: 0 0 12px 0;
  color: #969696;
  font-size: 13px;
  text-transform: uppercase;
}

.loading {
  color: #969696;
  font-size: 13px;
}

.empty {
  color: #969696;
  font-size: 13px;
}

.table-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.table-list li {
  padding: 6px 10px;
  color: #d4d4d4;
  cursor: pointer;
  font-size: 13px;
  border-radius: 3px;
}

.table-list li:hover {
  background-color: #3e3e42;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 20px;
  overflow-y: auto;
}

.editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 300px;
}

.editor-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: center;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: #0e639c;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #1177bb;
}

.btn-secondary {
  background-color: #3c3c3c;
  color: #d4d4d4;
}

.btn-secondary:hover:not(:disabled) {
  background-color: #464647;
}

.btn-small {
  padding: 6px 12px;
  font-size: 12px;
}

.result-info {
  color: #969696;
  font-size: 13px;
  margin-left: auto;
}

.sql-textarea {
  flex: 1;
  width: 100%;
  min-height: 200px;
  padding: 12px;
  background-color: #1e1e1e;
  border: 1px solid #3e3e42;
  border-radius: 4px;
  color: #d4d4d4;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.5;
  resize: vertical;
}

.sql-textarea:focus {
  outline: none;
  border-color: #569cd6;
}

.error-section {
  margin-top: 20px;
  padding: 16px;
  background-color: #2d1b1b;
  border: 1px solid #8b0000;
  border-radius: 4px;
}

.error-section h4 {
  margin: 0 0 8px 0;
  color: #f14c4c;
  font-size: 14px;
}

.error-message {
  margin: 0;
  color: #f14c4c;
  font-family: 'Consolas', monospace;
  font-size: 13px;
  white-space: pre-wrap;
}

.result-section {
  margin-top: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.result-header h4 {
  margin: 0;
  color: #d4d4d4;
  font-size: 14px;
}

.row-count {
  color: #969696;
  font-size: 13px;
}

.table-wrapper {
  border: 1px solid #3e3e42;
  border-radius: 4px;
  overflow: auto;
  max-height: 400px;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  background-color: #2d2d30;
}

.result-table th {
  background-color: #3e3e42;
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  color: #d4d4d4;
  font-size: 13px;
  border-bottom: 1px solid #5a5a5a;
  position: sticky;
  top: 0;
}

.result-table td {
  padding: 6px 12px;
  border-bottom: 1px solid #3e3e42;
  color: #d4d4d4;
  font-size: 13px;
  font-family: 'Consolas', monospace;
}

.result-table tr:hover {
  background-color: #3e3e42;
}

.success-message {
  padding: 16px;
  background-color: #1b2d1b;
  border: 1px solid #008000;
  border-radius: 4px;
  color: #4ec9b0;
  font-size: 14px;
}
</style>