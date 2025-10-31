<template>
  <div class="sql-editor">
    <ConnectionTree
      @connection-selected="onConnectionSelected"
      @database-selected="onDatabaseSelected"
      @table-selected="onTableSelected"
    />

    
    <div class="main-content">
      <div class="editor-section">
        <div class="editor-toolbar">
          <div class="toolbar-left">
            <button class="btn btn-primary" @click="executeSql" :disabled="!currentSession || !sqlText.trim()">
              执行 (F5)
            </button>
            <button class="btn btn-secondary" @click="clearEditor">清空</button>
            <button class="btn btn-secondary" @click="formatSql">格式化</button>
          </div>
          <div class="toolbar-right">
            <div v-if="currentSession" class="connection-info">
              <span class="info-item">
                <span class="label">连接:</span>
                <span class="value">{{ currentSession.connectionInfo.name }}</span>
              </span>
              <span class="info-item">
                <span class="label">数据库:</span>
                <span class="value">{{ currentDatabase || '未选择' }}</span>
              </span>
            </div>
            <span v-if="queryResult" class="result-info">
              {{ queryResult.data ? `${queryResult.data.length} 行` : `${queryResult.affectedRows} 行受影响` }}
            </span>
          </div>
        </div>

        <div class="editor-wrapper">
          <SqlCodeEditor
            ref="codeEditor"
            v-model="sqlText"
            @execute="executeSql"
          />
        </div>
      </div>

      <div v-if="error" class="error-section">
        <h4>错误信息</h4>
        <pre class="error-message">{{ error }}</pre>
      </div>

      <div v-if="queryResult && queryResult.data" class="result-section">
        <div class="result-header">
          <h4>查询结果</h4>
          <div class="result-actions">
            <span class="row-count">{{ queryResult.data.length }} 行</span>
            <button class="btn btn-small" @click="exportData">导出CSV</button>
          </div>
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

    <!-- 查询历史侧边栏 -->
    <div class="history-sidebar">
      <h4>查询历史</h4>
      <div v-if="queryHistory.length === 0" class="empty">暂无历史</div>
      <ul v-else class="history-list">
        <li v-for="(item, index) in queryHistory.slice(-20).reverse()" :key="index" @click="loadHistory(item)">
          <div class="history-item">
            <div class="history-sql">{{ item.sql.substring(0, 60) }}...</div>
            <div class="history-meta">
              <span class="history-time">{{ formatTime(item.time) }}</span>
              <span class="history-duration">{{ item.duration }}ms</span>
            </div>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import { sqlApi } from '../services/api'
import { connectionStore } from '../stores/connectionStore'
import SqlCodeEditor from '../components/SqlCodeEditor.vue'
import ConnectionTree from '../components/ConnectionTree.vue'

export default {
  name: 'SqlEditor',
  components: {
    SqlCodeEditor,
    ConnectionTree
  },

  data() {
    return {
      currentSession: null,
      currentDatabase: null,
      sqlText: '',
      queryResult: null,
      error: null,
      columns: [],
      queryHistory: []
    }
  },

  mounted() {
    this.loadQueryHistory()
  },

  methods: {
    onConnectionSelected(session) {
      this.currentSession = session
      this.currentDatabase = session?.currentDatabase || null
      this.queryResult = null
      this.error = null
    },

    async onDatabaseSelected({ session, database }) {
      this.currentSession = session
      this.currentDatabase = database
      this.queryResult = null
      this.error = null
    },

    onTableSelected({ session, table }) {
      // 生成基本查询语句，如果SQL编辑器有内容则追加
      const query = `SELECT * FROM \`${table}\` LIMIT 100;`
      const insertText = this.sqlText.trim() ? `\n${query}` : query

      // 插入到编辑器
      this.$refs.codeEditor?.insertText(insertText)
      this.$refs.codeEditor?.focus()
    },

    
    // 检测SQL类型
    getSqlType(sql) {
      const trimmedSql = sql.trim().toUpperCase()

      // USE 语句
      if (trimmedSql.startsWith('USE ')) {
        return 'USE'
      }

      // 查询语句
      const queryKeywords = ['SELECT', 'SHOW', 'DESCRIBE', 'DESC', 'EXPLAIN', 'WITH']
      for (const keyword of queryKeywords) {
        if (trimmedSql.startsWith(keyword + ' ') || trimmedSql === keyword) {
          return 'QUERY'
        }
      }

      // 更新语句
      const updateKeywords = ['INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER', 'TRUNCATE', 'REPLACE']
      for (const keyword of updateKeywords) {
        if (trimmedSql.startsWith(keyword + ' ')) {
          return 'UPDATE'
        }
      }

      // 默认作为更新语句处理
      return 'UPDATE'
    },

    async executeSql() {
      if (!this.currentSession || !this.sqlText.trim()) return

      this.error = null
      this.queryResult = null
      const startTime = Date.now()

      const sqlText = this.sqlText.trim()
      const sqlType = this.getSqlType(sqlText)

      // 对于更新语句，添加确认提示
      if (sqlType === 'UPDATE' && !confirm('确定要执行此更新语句吗？')) {
        return
      }

      try {
        if (sqlType === 'USE') {
          // 执行 USE 语句
          const response = await sqlApi.execute(this.currentSession.sessionId, sqlText)

          // 更新当前数据库
          if (response.data.affectedRows > 0) {
            try {
              const dbResponse = await sqlApi.getCurrentDatabase(this.currentSession.sessionId)
              if (dbResponse.data && dbResponse.data.database !== this.currentDatabase) {
                this.currentDatabase = dbResponse.data.database
                // 更新 session 中的当前数据库
                const session = connectionStore.getSession(this.currentSession.sessionId)
                if (session) {
                  session.currentDatabase = this.currentDatabase
                }
              }
            } catch (e) {
              console.error('获取当前数据库失败', e)
            }
          }

          this.queryResult = {
            affectedRows: response.data.affectedRows,
            message: `已切换到数据库: ${this.currentDatabase || '未知'}`
          }
          this.addToHistory(sqlText, 'use', Date.now() - startTime)
        } else if (sqlType === 'QUERY') {
          // 执行查询语句
          const response = await sqlApi.query(this.currentSession.sessionId, sqlText)
          this.queryResult = response.data

          if (this.queryResult.data && this.queryResult.data.length > 0) {
            this.columns = Object.keys(this.queryResult.data[0])
          }
          this.addToHistory(sqlText, 'query', Date.now() - startTime)
        } else {
          // 执行更新语句
          const response = await sqlApi.execute(this.currentSession.sessionId, sqlText)
          this.queryResult = response.data
          this.addToHistory(sqlText, 'update', Date.now() - startTime)
        }
      } catch (error) {
        this.error = error.response?.data?.error || error.message
        this.addToHistory(sqlText, 'error', Date.now() - startTime, this.error)
      }
    },

    clearEditor() {
      this.sqlText = ''
      this.queryResult = null
      this.error = null
      this.$refs.codeEditor?.focus()
    },

    formatSql() {
      let formatted = this.sqlText
        .replace(/\s+/g, ' ')
        .replace(/\bSELECT\b/gi, '\nSELECT')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .replace(/\bUNION\b/gi, '\nUNION')
        .replace(/,/g, ',\n  ')
        .trim()

      this.sqlText = formatted
    },

    loadHistory(item) {
      this.sqlText = item.sql
      this.$refs.codeEditor?.focus()
    },

    addToHistory(sql, type, duration, error = null) {
      this.queryHistory.push({
        sql,
        type,
        duration,
        error,
        time: new Date().toISOString()
      })

      if (this.queryHistory.length > 100) {
        this.queryHistory = this.queryHistory.slice(-100)
      }

      localStorage.setItem('queryHistory', JSON.stringify(this.queryHistory))
    },

    loadQueryHistory() {
      const saved = localStorage.getItem('queryHistory')
      if (saved) {
        this.queryHistory = JSON.parse(saved)
      }
    },

    exportData() {
      if (!this.queryResult?.data) return

      const csv = [
        this.columns.join(','),
        ...this.queryResult.data.map(row =>
          this.columns.map(col => {
            const val = row[col]
            return val === null ? 'NULL' : `"${String(val).replace(/"/g, '""')}"`
          }).join(',')
        )
      ].join('\n')

      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `query_result_${Date.now()}.csv`
      link.click()
    },

    formatTime(timeStr) {
      const date = new Date(timeStr)
      const now = new Date()
      const diff = now - date

      if (diff < 60000) {
        return `${Math.floor(diff / 1000)}秒前`
      } else if (diff < 3600000) {
        return `${Math.floor(diff / 60000)}分钟前`
      } else if (diff < 86400000) {
        return `${Math.floor(diff / 3600000)}小时前`
      } else {
        return date.toLocaleDateString()
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
@import '../styles/theme.css';

.sql-editor {
  height: 100%;
  display: flex;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: var(--spacing-xl);
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
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.toolbar-left {
  display: flex;
  gap: var(--spacing-md);
}

.toolbar-right {
  display: flex;
  gap: var(--spacing-lg);
  align-items: center;
}

.connection-info {
  display: flex;
  gap: var(--spacing-lg);
  font-size: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.info-item .label {
  color: var(--text-secondary);
}

.info-item .value {
  color: var(--accent-primary);
  font-family: var(--font-family-mono);
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: var(--transition-fast);
  font-family: var(--font-family-base);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: var(--btn-primary-bg);
  color: var(--btn-primary-text);
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--btn-primary-hover);
}

.btn-secondary {
  background-color: var(--btn-secondary-bg);
  color: var(--btn-secondary-text);
}

.btn-secondary:hover:not(:disabled) {
  background-color: var(--btn-secondary-hover);
}

.btn-small {
  padding: 6px 12px;
  font-size: 12px;
}

.result-info {
  color: var(--text-secondary);
  font-size: 13px;
}

.editor-wrapper {
  flex: 1;
  min-height: 200px;
}

.error-section {
  margin-top: var(--spacing-xl);
  padding: var(--spacing-lg);
  background-color: var(--error-bg);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-sm);
}

.error-section h4 {
  margin: 0 0 var(--spacing-sm) 0;
  color: var(--error);
  font-size: 14px;
}

.error-message {
  margin: 0;
  color: var(--error);
  font-family: var(--font-family-mono);
  font-size: 13px;
  white-space: pre-wrap;
}

.result-section {
  margin-top: var(--spacing-xl);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.result-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.result-actions {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
}

.row-count {
  color: var(--text-secondary);
  font-size: 13px;
}

.table-wrapper {
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  overflow: auto;
  max-height: 400px;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  background-color: var(--bg-tertiary);
}

.result-table th {
  background-color: var(--bg-quaternary);
  padding: 8px 12px;
  text-align: left;
  font-weight: 600;
  color: var(--text-primary);
  font-size: 13px;
  border-bottom: 1px solid var(--border-secondary);
  position: sticky;
  top: 0;
}

.result-table td {
  padding: 6px 12px;
  border-bottom: 1px solid var(--border-primary);
  color: var(--text-primary);
  font-size: 13px;
  font-family: var(--font-family-mono);
}

.result-table tr:hover {
  background-color: var(--bg-highlight);
}

.success-message {
  padding: var(--spacing-lg);
  background-color: var(--success-bg);
  border: 1px solid var(--success-border);
  border-radius: var(--radius-sm);
  color: var(--success);
  font-size: 14px;
}

.history-sidebar {
  width: 280px;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  padding: var(--spacing-lg);
  overflow-y: auto;
}

.history-sidebar h4 {
  margin: 0 0 var(--spacing-md) 0;
  color: var(--text-secondary);
  font-size: 13px;
  text-transform: uppercase;
}

.empty {
  color: var(--text-secondary);
  font-size: 13px;
}

.history-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.history-list li {
  cursor: pointer;
}

.history-item {
  padding: var(--spacing-sm);
  border-radius: var(--radius-sm);
  transition: var(--transition-fast);
}

.history-item:hover {
  background-color: var(--bg-highlight);
}

.history-sql {
  color: var(--text-primary);
  font-size: 13px;
  font-family: var(--font-family-mono);
  margin-bottom: 4px;
}

.history-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
}

.history-time {
  color: var(--text-tertiary);
}

.history-duration {
  color: var(--accent-primary);
}

/* 对话框样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  width: 500px;
  max-width: 90%;
  box-shadow: var(--shadow-heavy);
}

.dialog-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-fast);
}

.close-btn:hover {
  color: var(--text-primary);
}

.dialog-body {
  padding: var(--spacing-xl);
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 14px;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-highlight);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  transition: var(--transition-fast);
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: var(--border-focus);
}

.form-row {
  display: flex;
  gap: var(--spacing-lg);
}

.form-row .form-group {
  flex: 1;
}

.dialog-footer {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
}
</style>