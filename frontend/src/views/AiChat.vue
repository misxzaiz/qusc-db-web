<template>
  <div class="ai-chat">
    <div class="chat-header">
      <h2>AI SQL助手</h2>
      <div class="header-actions">
        <select v-model="selectedConfig" @change="onConfigChange" class="config-select">
          <option v-for="config in aiConfigs" :key="config.id" :value="config.id">
            {{ config.name }}
          </option>
        </select>
        <button class="btn btn-small" @click="clearChat">清空</button>
        <button class="btn btn-small" @click="showSettings = true">设置</button>
      </div>
    </div>

    <div class="chat-body">
      <!-- 表结构引用栏 -->
      <div v-if="tables.length > 0" class="table-reference">
        <div class="reference-header">
          <span>📊 数据库表（点击引用）</span>
          <button class="btn btn-small" @click="toggleTableList">
            {{ showTableList ? '收起' : '展开' }}
          </button>
        </div>
        <div v-if="showTableList" class="table-list">
          <div v-for="table in tables" :key="table" class="table-item" @click="referenceTable(table)">
            <span class="table-name">{{ table }}</span>
            <button class="btn btn-small" @click.stop="describeTable(table)">查看结构</button>
          </div>
        </div>
      </div>

      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-message">
          <h3>欢迎使用AI SQL助手！</h3>
          <p>我可以帮助您：</p>
          <ul>
            <li>生成SQL语句</li>
            <li>解释SQL逻辑</li>
            <li>优化SQL性能</li>
            <li>分析数据表结构</li>
          </ul>
          <p>您可以：</p>
          <ul>
            <li>直接输入问题</li>
            <li>使用下方的快捷操作</li>
            <li>点击上方表名引用表结构</li>
          </ul>
        </div>

        <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <div class="message-avatar">
            <span v-if="message.role === 'user'">👤</span>
            <span v-else>🤖</span>
          </div>
          <div class="message-content">
            <div class="message-text" v-html="formatMessage(message.content)"></div>
            <div v-if="message.role === 'assistant' && message.sql" class="message-actions">
              <button class="btn btn-small" @click="copySql(message.sql)">复制SQL</button>
              <button class="btn btn-small" @click="executeSql(message.sql)">执行</button>
            </div>
            <div class="message-time">{{ formatTime(message.timestamp) }}</div>
          </div>
        </div>

        <div v-if="loading" class="message assistant">
          <div class="message-avatar">
            <span>🤖</span>
          </div>
          <div class="message-content">
            <div class="typing-indicator">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <div class="quick-actions">
        <button class="quick-btn" @click="quickAction('generate')">
          <span class="icon">✨</span> 生成SQL
        </button>
        <button class="quick-btn" @click="quickAction('explain')">
          <span class="icon">💡</span> 解释SQL
        </button>
        <button class="quick-btn" @click="quickAction('optimize')">
          <span class="icon">⚡</span> 优化SQL
        </button>
        <button class="quick-btn" @click="quickAction('analyze')">
          <span class="icon">📊</span> 分析表
        </button>
      </div>

      <div class="input-container">
        <textarea
          v-model="inputText"
          placeholder="输入您的问题..."
          rows="3"
          @keydown.enter.prevent="onEnter"
          :disabled="loading"
        ></textarea>
        <button class="send-btn" @click="sendMessage" :disabled="!inputText.trim() || loading">
          <span v-if="!loading">发送</span>
          <span v-else>...</span>
        </button>
      </div>
    </div>

    <!-- 设置对话框 -->
    <div v-if="showSettings" class="dialog-overlay" @click="showSettings = false">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h2>AI设置</h2>
          <button class="close-btn" @click="showSettings = false">×</button>
        </div>
        <div class="dialog-body">
          <p>请前往 <router-link to="/ai-settings">AI设置页面</router-link> 配置AI服务</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { aiApi } from '../services/aiApi'
import { connectionStore } from '../stores/connectionStore'

export default {
  name: 'AiChat',

  data() {
    return {
      messages: [],
      inputText: '',
      loading: false,
      aiConfigs: [],
      selectedConfig: null,
      showSettings: false,
      currentSession: null,
      currentDatabase: null,
      tables: [],
      showTableList: true
    }
  },

  mounted() {
    this.loadAiConfigs()
    this.loadSessionInfo()
    this.scrollToBottom()
  },

  methods: {
    async loadAiConfigs() {
      try {
        const response = await aiApi.getConfigs()
        this.aiConfigs = response.data
        if (this.aiConfigs.length > 0) {
          const saved = localStorage.getItem('selected_ai_config')
          this.selectedConfig = saved || this.aiConfigs[0].id
        }
      } catch (error) {
        console.error('加载AI配置失败', error)
      }
    },

    loadSessionInfo() {
      // 获取当前连接信息
      const sessions = connectionStore.getActiveSessions()
      if (sessions.length > 0) {
        this.currentSession = sessions[0]
        this.currentDatabase = this.currentSession.currentDatabase
        // 获取表列表
        this.loadTables()
      }
    },

    async loadTables() {
      if (!this.currentSession || !this.currentDatabase) return

      try {
        const sessionData = connectionStore.getSession(this.currentSession.sessionId)
        if (sessionData && sessionData.tables[this.currentDatabase]) {
          this.tables = sessionData.tables[this.currentDatabase]
        }
      } catch (error) {
        console.error('加载表列表失败', error)
      }
    },

    async sendMessage() {
      if (!this.inputText.trim() || this.loading) return

      const userMessage = {
        role: 'user',
        content: this.inputText,
        timestamp: new Date()
      }

      this.messages.push(userMessage)
      const input = this.inputText
      this.inputText = ''
      this.loading = true
      this.scrollToBottom()

      try {
        // 检测用户意图并调用相应的API
        let response
        const lowerInput = input.toLowerCase()

        if (lowerInput.includes('生成') || lowerInput.includes('查询') || lowerInput.includes('select')) {
          response = await aiApi.generateSql(input, this.selectedConfig)
          this.addAssistantMessage(response.data.sql, 'sql')
        } else if (lowerInput.includes('解释') || lowerInput.includes('explain')) {
          const sqlMatch = input.match(/```sql\n?([\s\S]*?)\n?```/i)
          const sql = sqlMatch ? sqlMatch[1] : this.getLastSql()
          if (sql) {
            response = await aiApi.explainSql(sql, this.selectedConfig)
            this.addAssistantMessage(response.data.explanation, 'text')
          } else {
            this.addAssistantMessage('请提供要解释的SQL语句', 'text')
          }
        } else if (lowerInput.includes('优化') || lowerInput.includes('optimize')) {
          const sqlMatch = input.match(/```sql\n?([\s\S]*?)\n?```/i)
          const sql = sqlMatch ? sqlMatch[1] : this.getLastSql()
          if (sql) {
            response = await aiApi.optimizeSql(sql, this.selectedConfig)
            this.addAssistantMessage(response.data.optimized, 'optimize')
          } else {
            this.addAssistantMessage('请提供要优化的SQL语句', 'text')
          }
        } else {
          // 默认作为SQL生成处理
          response = await aiApi.generateSql(input, this.selectedConfig)
          this.addAssistantMessage(response.data.sql, 'sql')
        }
      } catch (error) {
        this.addAssistantMessage('抱歉，处理您的请求时出错：' + (error.response?.data?.error || error.message), 'error')
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },

    addAssistantMessage(content, type) {
      const message = {
        role: 'assistant',
        content: content,
        timestamp: new Date(),
        type: type
      }

      if (type === 'sql' || type === 'optimize') {
        // 提取SQL代码
        const sqlMatch = content.match(/```sql\n?([\s\S]*?)\n?```/i)
        message.sql = sqlMatch ? sqlMatch[1] : content
      }

      this.messages.push(message)
    },

    quickAction(action) {
      let prompt = ''

      switch(action) {
        case 'generate':
          prompt = '请帮我生成一个SQL查询语句。\n\n当前数据库：' + (this.currentDatabase || '未选择') +
                  '\n可用表：' + (this.tables.length > 0 ? this.tables.join(', ') : '无') +
                  '\n\n请描述您的需求：'
          break
        case 'explain':
          prompt = '请解释以下SQL语句：\n\n```sql\n-- 请在此处粘贴SQL语句\n```'
          break
        case 'optimize':
          prompt = '请优化以下SQL语句：\n\n```sql\n-- 请在此处粘贴SQL语句\n```'
          break
        case 'analyze':
          if (this.tables.length > 0) {
            prompt = '请分析数据库表结构：\n\n' + this.tables.join('\n')
          } else {
            prompt = '请先连接数据库并选择表'
          }
          break
      }

      this.inputText = prompt
      this.$refs.messagesContainer.querySelector('textarea')?.focus()
    },

    onEnter(event) {
      if (!event.shiftKey) {
        this.sendMessage()
      }
    },

    clearChat() {
      if (confirm('确定要清空聊天记录吗？')) {
        this.messages = []
      }
    },

    onConfigChange() {
      localStorage.setItem('selected_ai_config', this.selectedConfig)
    },

    copySql(sql) {
      navigator.clipboard.writeText(sql).then(() => {
        alert('SQL已复制到剪贴板')
      })
    },

    executeSql(sql) {
      // 将SQL传递给SQL编辑器
      this.$emit('execute-sql', sql)
    },

    formatMessage(content) {
      // 格式化消息，支持代码高亮
      return content
        .replace(/```sql\n?([\s\S]*?)\n?```/gi, '<pre class="sql-code"><code>$1</code></pre>')
        .replace(/```([\s\S]*?)```/gi, '<pre><code>$1</code></pre>')
        .replace(/\n/g, '<br>')
    },

    formatTime(timestamp) {
      return new Date(timestamp).toLocaleTimeString()
    },

    getLastSql() {
      // 从消息中获取最后的SQL
      for (let i = this.messages.length - 1; i >= 0; i--) {
        if (this.messages[i].sql) {
          return this.messages[i].sql
        }
      }
      return null
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messagesContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    },

    toggleTableList() {
      this.showTableList = !this.showTableList
    },

    referenceTable(tableName) {
      // 在输入框中引用表名
      const reference = `表名: ${tableName}\n`
      this.inputText = this.inputText + reference
      this.$refs.messagesContainer.querySelector('textarea')?.focus()
    },

    async describeTable(tableName) {
      // 获取表结构信息
      if (!this.currentSession) {
        this.addAssistantMessage('请先连接数据库', 'error')
        return
      }

      this.loading = true
      try {
        const sql = `DESCRIBE ${tableName}`
        const response = await fetch(`/api/sql/query`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sessionId: this.currentSession.sessionId, sql })
        })
        const result = await response.json()

        if (result.data && result.data.data) {
          let structure = `表 ${tableName} 的结构：\n\n`
          result.data.data.forEach(col => {
            structure += `- ${col.Field}: ${col.Type} ${col.Null === 'NO' ? 'NOT NULL' : ''} ${col.Key ? '(' + col.Key + ')' : ''}\n`
          })

          this.addAssistantMessage(structure, 'text')
        } else {
          this.addAssistantMessage(`无法获取表 ${tableName} 的结构`, 'error')
        }
      } catch (error) {
        this.addAssistantMessage('获取表结构失败: ' + error.message, 'error')
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
@import '../styles/theme.css';

.ai-chat {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-primary);
}

.chat-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--bg-secondary);
}

.chat-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
}

.config-select {
  padding: 6px 12px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
}

.chat-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.table-reference {
  border-bottom: 1px solid var(--border-primary);
  background-color: var(--bg-tertiary);
}

.reference-header {
  padding: var(--spacing-md);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.table-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 0 var(--spacing-md) var(--spacing-md);
}

.table-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm);
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.table-item:hover {
  background-color: var(--bg-highlight);
}

.table-name {
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  font-size: 13px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-lg);
}

.welcome-message {
  text-align: center;
  color: var(--text-secondary);
  padding: var(--spacing-xl);
}

.welcome-message h3 {
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
}

.welcome-message ul {
  text-align: left;
  display: inline-block;
  margin: var(--spacing-md) 0;
}

.message {
  display: flex;
  margin-bottom: var(--spacing-lg);
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-right: var(--spacing-md);
  flex-shrink: 0;
}

.message.user .message-avatar {
  background-color: var(--accent-primary);
}

.message.assistant .message-avatar {
  background-color: var(--bg-tertiary);
}

.message-content {
  flex: 1;
}

.message-text {
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: var(--spacing-sm);
}

.sql-code {
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  padding: var(--spacing-md);
  margin: var(--spacing-sm) 0;
  overflow-x: auto;
}

.sql-code code {
  font-family: var(--font-family-mono);
  font-size: 13px;
  color: var(--text-primary);
}

.message-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.message-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: var(--spacing-sm) 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--text-tertiary);
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-10px); }
}

.chat-input {
  border-top: 1px solid var(--border-primary);
  background-color: var(--bg-secondary);
  padding: var(--spacing-lg);
}

.quick-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.quick-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.quick-btn:hover {
  background-color: var(--bg-highlight);
}

.quick-btn .icon {
  font-size: 14px;
}

.input-container {
  display: flex;
  gap: var(--spacing-sm);
  align-items: flex-end;
}

.input-container textarea {
  flex: 1;
  padding: 10px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  resize: none;
  font-family: var(--font-family-base);
}

.input-container textarea:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.send-btn {
  padding: 10px 20px;
  background-color: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
  transition: var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background-color: var(--accent-primary-hover);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
  width: 400px;
  max-width: 90%;
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
}

.dialog-body {
  padding: var(--spacing-lg);
}

.dialog-body a {
  color: var(--accent-primary);
  text-decoration: none;
}

.dialog-body a:hover {
  text-decoration: underline;
}
</style>