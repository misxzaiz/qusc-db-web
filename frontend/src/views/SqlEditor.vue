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
            <button class="btn btn-ai-toggle" @click="toggleAiPanel" :class="{ active: showAiPanel }">
              <span class="ai-icon">🤖</span> AI助手
            </button>
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

    <!-- AI助手侧边栏 -->
    <div v-if="showAiPanel" class="ai-sidebar">
      <AiChat @execute-sql="handleAiExecuteSql" />
    </div>

    <!-- AI生成对话框 -->
    <div v-if="showAiGenerate" class="dialog-overlay" @click="showAiGenerate = false">
      <div class="dialog ai-dialog" @click.stop>
        <div class="dialog-header">
          <h2>AI生成SQL</h2>
          <button class="close-btn" @click="showAiGenerate = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>描述你的需求</label>
            <textarea
              v-model="aiInput"
              placeholder="例如：查询所有用户的订单信息，包含用户姓名和订单金额"
              rows="4"
            ></textarea>
          </div>
          <div v-if="aiConfig" class="ai-config-info">
            <span class="ai-provider">使用: {{ aiConfig.name }}</span>
            <button class="btn btn-small" @click="showAiSettings = true">更换</button>
          </div>
          <div v-else class="ai-no-config">
            <p>未配置AI服务</p>
            <button class="btn btn-primary" @click="showAiSettings = true">去配置</button>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn btn-secondary" @click="showAiGenerate = false">取消</button>
          <button class="btn btn-primary" @click="generateSql" :disabled="!aiInput.trim() || aiGenerating || !aiConfig">
            <span v-if="aiGenerating" class="loading-spinner"></span>
            {{ aiGenerating ? '生成中...' : '生成' }}
          </button>
        </div>
      </div>
    </div>

    <!-- AI解释/优化结果对话框 -->
    <div v-if="showAiResult" class="dialog-overlay" @click="showAiResult = false">
      <div class="dialog ai-dialog" @click.stop>
        <div class="dialog-header">
          <h2>{{ aiResultTitle }}</h2>
          <button class="close-btn" @click="showAiResult = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="ai-result-content">
            <pre>{{ aiResultContent }}</pre>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn btn-secondary" @click="showAiResult = false">关闭</button>
          <button v-if="aiResultType === 'optimize'" class="btn btn-primary" @click="applyOptimizedSql">应用优化</button>
        </div>
      </div>
    </div>

    <!-- AI设置对话框 -->
    <div v-if="showAiSettings" class="dialog-overlay" @click="showAiSettings = false">
      <div class="dialog ai-settings-dialog" @click.stop>
        <div class="dialog-header">
          <h2>AI服务设置</h2>
          <button class="close-btn" @click="showAiSettings = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="ai-configs">
            <div v-if="aiConfigs.length === 0" class="no-configs">
              <p>暂无AI配置</p>
              <button class="btn btn-primary" @click="openAiConfigManager">管理配置</button>
            </div>
            <div v-else>
              <div v-for="config in aiConfigs" :key="config.id"
                   class="config-item"
                   :class="{ active: selectedAiConfig === config.id }"
                   @click="selectedAiConfig = config.id">
                <div class="config-name">{{ config.name }}</div>
                <div class="config-provider">{{ config.provider }} - {{ config.model }}</div>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn btn-secondary" @click="showAiSettings = false">取消</button>
          <button class="btn btn-primary" @click="saveAiConfigSelection" :disabled="!selectedAiConfig">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { sqlApi } from '../services/api'
import { aiApi } from '../services/aiApi'
import { connectionStore } from '../stores/connectionStore'
import SqlCodeEditor from '../components/SqlCodeEditor.vue'
import ConnectionTree from '../components/ConnectionTree.vue'
import AiChat from './AiChat.vue'

export default {
  name: 'SqlEditor',
  components: {
    SqlCodeEditor,
    ConnectionTree,
    AiChat
  },

  data() {
    return {
      currentSession: null,
      currentDatabase: null,
      sqlText: '',
      queryResult: null,
      error: null,
      columns: [],
      queryHistory: [],
      executing: false,

      // AI相关
      showAiPanel: false,
      showAiGenerate: false,
      showAiResult: false,
      showAiSettings: false,
      aiInput: '',
      aiGenerating: false,
      aiResultTitle: '',
      aiResultContent: '',
      aiResultType: '',
      aiConfigs: [],
      aiConfig: null,
      selectedAiConfig: null,
      optimizedSql: ''
    }
  },

  mounted() {
    this.loadQueryHistory()
    this.loadAiConfigs()
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
    },

    // AI相关方法
    async loadAiConfigs() {
      try {
        const response = await aiApi.getConfigs()
        this.aiConfigs = response.data
        // 加载已保存的AI配置选择
        const savedConfigId = localStorage.getItem('selected_ai_config')
        if (savedConfigId) {
          this.aiConfig = this.aiConfigs.find(c => c.id === savedConfigId)
        } else if (this.aiConfigs.length > 0) {
          // 默认选择第一个启用的配置
          this.aiConfig = this.aiConfigs.find(c => c.enabled) || this.aiConfigs[0]
        }
      } catch (error) {
        console.error('加载AI配置失败', error)
      }
    },

    async generateSql() {
      if (!this.aiInput.trim() || !this.aiConfig) return

      this.aiGenerating = true
      try {
        const response = await aiApi.generateSql(this.aiInput, this.aiConfig.id)
        const generatedSql = response.data.sql
        this.sqlText = generatedSql
        this.showAiGenerate = false
        this.aiInput = ''
      } catch (error) {
        alert('生成失败: ' + (error.response?.data?.error || error.message))
      } finally {
        this.aiGenerating = false
      }
    },

    async explainSql() {
      if (!this.sqlText.trim() || !this.aiConfig) return

      this.aiGenerating = true
      try {
        const response = await aiApi.explainSql(this.sqlText, this.aiConfig.id)
        this.aiResultTitle = 'SQL解释'
        this.aiResultContent = response.data.explanation
        this.aiResultType = 'explain'
        this.showAiResult = true
      } catch (error) {
        alert('解释失败: ' + (error.response?.data?.error || error.message))
      } finally {
        this.aiGenerating = false
      }
    },

    async optimizeSql() {
      if (!this.sqlText.trim() || !this.aiConfig) return

      this.aiGenerating = true
      try {
        const response = await aiApi.optimizeSql(this.sqlText, this.aiConfig.id)
        this.aiResultTitle = 'SQL优化建议'
        this.aiResultContent = response.data.optimized
        this.aiResultType = 'optimize'
        this.optimizedSql = this.extractSqlFromOptimization(response.data.optimized)
        this.showAiResult = true
      } catch (error) {
        alert('优化失败: ' + (error.response?.data?.error || error.message))
      } finally {
        this.aiGenerating = false
      }
    },

    extractSqlFromOptimization(optimizedText) {
      // 从优化文本中提取SQL语句
      const sqlMatch = optimizedText.match(/```sql\n([\s\S]*?)\n```/);
      if (sqlMatch) {
        return sqlMatch[1].trim();
      }
      // 如果没有找到代码块，尝试提取第一行看起来像SQL的内容
      const lines = optimizedText.split('\n');
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.match(/^(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)/i)) {
          return trimmed;
        }
      }
      return optimizedText;
    },

    applyOptimizedSql() {
      if (this.optimizedSql) {
        this.sqlText = this.optimizedSql
        this.showAiResult = false
      }
    },

    saveAiConfigSelection() {
      if (this.selectedAiConfig) {
        this.aiConfig = this.aiConfigs.find(c => c.id === this.selectedAiConfig)
        localStorage.setItem('selected_ai_config', this.selectedAiConfig)
        this.showAiSettings = false
      }
    },

    openAiConfigManager() {
      // 打开AI配置管理页面（可以创建新路由或窗口）
      window.open('#/ai-settings', '_blank')
    },

    toggleAiPanel() {
      this.showAiPanel = !this.showAiPanel
    },

    handleAiExecuteSql(sql) {
      this.sqlText = sql
      this.executeSql()
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

.ai-buttons {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: var(--spacing-lg);
  padding-left: var(--spacing-lg);
  border-left: 1px solid var(--border-secondary);
}

.btn-ai {
  background: linear-gradient(135deg, var(--accent-primary), var(--accent-secondary));
  color: white;
  font-size: 12px;
  padding: 6px 12px;
  position: relative;
  overflow: hidden;
}

.btn-ai:hover:not(:disabled) {
  background: linear-gradient(135deg, var(--accent-primary-hover), var(--accent-secondary-hover));
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.btn-ai:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-icon {
  margin-right: 4px;
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

/* AI侧边栏 */
.ai-sidebar {
  width: 400px;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  display: flex;
  flex-direction: column;
}

.btn-ai-toggle {
  background-color: var(--btn-secondary-bg);
  color: var(--btn-secondary-text);
  position: relative;
}

.btn-ai-toggle.active {
  background-color: var(--accent-primary);
  color: white;
}

.btn-ai-toggle:hover:not(:disabled) {
  background-color: var(--btn-secondary-hover);
}

.btn-ai-toggle.active:hover:not(:disabled) {
  background-color: var(--accent-primary-hover);
}

/* AI相关样式 */
.ai-dialog {
  width: 600px;
  max-width: 90%;
}

.ai-settings-dialog {
  width: 500px;
  max-width: 90%;
}

.ai-config-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  background-color: var(--bg-secondary);
  border-radius: var(--radius-sm);
  margin-top: var(--spacing-md);
}

.ai-provider {
  color: var(--text-primary);
  font-size: 14px;
}

.ai-no-config {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.ai-result-content {
  max-height: 400px;
  overflow-y: auto;
  padding: var(--spacing-md);
  background-color: var(--bg-secondary);
  border-radius: var(--radius-sm);
}

.ai-result-content pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: var(--font-family-mono);
  font-size: 13px;
  color: var(--text-primary);
}

.ai-configs {
  max-height: 300px;
  overflow-y: auto;
}

.no-configs {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.config-item {
  padding: var(--spacing-md);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.config-item:hover {
  background-color: var(--bg-highlight);
}

.config-item.active {
  background-color: var(--accent-primary);
  color: white;
}

.config-name {
  font-weight: 600;
  margin-bottom: 4px;
}

.config-provider {
  font-size: 12px;
  opacity: 0.8;
}

.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-highlight);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  font-family: var(--font-family-mono);
  resize: vertical;
}

/* 加载动画 */
.loading-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid transparent;
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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