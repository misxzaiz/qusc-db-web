<template>
  <div class="ai-chat">
    <div class="chat-header">
      <div class="header-left">
        <h2>AI助手</h2>
        <select v-model="selectedConfig" @change="onConfigChange" class="config-select">
          <option v-for="config in aiConfigs" :key="config.id" :value="config.id">
            {{ config.name }}
          </option>
        </select>
      </div>
      <div class="header-actions">
        <button class="btn-icon" @click="showRoleManager = !showRoleManager" title="角色管理">
          <font-awesome-icon icon="user-friends" />
        </button>
        <button class="btn-icon" @click="clearChat" title="清空聊天">
          <font-awesome-icon icon="trash" />
        </button>
        <button class="btn-icon" @click="showSettings = true" title="设置">
          <font-awesome-icon icon="cog" />
        </button>
      </div>
    </div>

    <div class="chat-body">
      <!-- 角色选择器 -->
      <div v-if="selectedRole" class="role-selector">
        <div class="current-role">
          <span class="role-avatar">{{ selectedRole.avatar || '🤖' }}</span>
          <span class="role-name">{{ selectedRole.name }}</span>
          <button class="btn-icon" @click="selectedRole = null" title="切换角色">
            <font-awesome-icon icon="times" />
          </button>
        </div>
      </div>

      <!-- 快捷工具栏 -->
      <div class="toolbar">
        <div class="toolbar-section">
          <button class="tool-btn" @click="useStream = !useStream" :class="{ active: useStream }" title="流水输出">
            <font-awesome-icon icon="stream" />
            <span>{{ useStream ? '流式' : '普通' }}</span>
          </button>
          <button class="tool-btn" @click="quickAction('sql')" title="SQL助手">
            <font-awesome-icon icon="database" />
            <span>SQL</span>
          </button>
          <button class="tool-btn" @click="quickAction('analyze')" title="数据分析">
            <font-awesome-icon icon="chart-bar" />
            <span>分析</span>
          </button>
          <button class="tool-btn" @click="quickAction('code')" title="代码生成">
            <font-awesome-icon icon="code" />
            <span>代码</span>
          </button>
        </div>
        <div v-if="tables.length > 0" class="toolbar-section">
          <button class="tool-btn" @click="toggleTableList" :class="{ active: showTableList }" title="数据库表">
            <font-awesome-icon icon="table" />
            <span>表({{ tables.length }})</span>
          </button>
        </div>
      </div>

      <!-- 表引用面板 -->
      <div v-if="showTableList && tables.length > 0" class="table-panel">
        <div class="table-grid">
          <div v-for="table in tables" :key="table" class="table-card" @click="referenceTable(table)">
            <div class="table-info">
              <font-awesome-icon icon="table" class="table-icon" />
              <span class="table-name">{{ table }}</span>
            </div>
            <button class="btn-mini" @click.stop="describeTable(table)">结构</button>
          </div>
        </div>
      </div>

      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome-message">
          <h3>👋 欢迎使用AI助手！</h3>
          <p>我可以帮助您：</p>
          <ul>
            <li>💬 自由对话 - 任何问题都可以</li>
            <li>📝 生成代码 - SQL、Python、JavaScript等</li>
            <li>📊 数据分析 - 解读数据、生成报告</li>
            <li>🔧 问题诊断 - 错误分析、解决方案</li>
          </ul>
          <p v-if="selectedRole" class="role-hint">
            当前角色：{{ selectedRole.name }} - {{ selectedRole.description }}
          </p>
        </div>

        <div v-for="(message, index) in messages" :key="index" class="message" :class="message.role">
          <div class="message-avatar">
            <span v-if="message.role === 'user'">👤</span>
            <span v-else>{{ currentRoleAvatar || '🤖' }}</span>
          </div>
          <div class="message-content">
            <div class="message-text">
              <!-- 使用Markdown渲染器 -->
              <MarkdownRenderer v-if="message.role === 'assistant' && message.useMarkdown" :content="message.content" />
              <div v-else v-html="formatMessage(message.content)"></div>
            </div>
            <div v-if="message.role === 'assistant' && message.sql" class="message-actions">
              <button class="btn-small" @click="copySql(message.sql)">复制SQL</button>
              <button class="btn-small" @click="executeSql(message.sql)">执行</button>
            </div>
            <div class="message-time">{{ formatTime(message.timestamp) }}</div>
          </div>
        </div>

        <!-- 流式输出指示器 -->
        <div v-if="streaming" class="message assistant">
          <div class="message-avatar">
            <span>{{ currentRoleAvatar || '🤖' }}</span>
          </div>
          <div class="message-content">
            <div class="streaming-content">
              <MarkdownRenderer :content="streamContent" />
              <span class="cursor">|</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <div class="input-container">
        <textarea
          v-model="inputText"
          :placeholder="selectedRole ? `以${selectedRole.name}的身份回答... (Shift+Enter换行，Enter发送)` : '输入您的问题... (Shift+Enter换行，Enter发送)'"
          rows="3"
          @keydown.enter.prevent="onEnter"
          :disabled="loading || streaming"
          ref="messageInput"
        ></textarea>
        <div class="input-actions">
          <button class="btn-attach" title="附加上下文" v-if="contextText" @click="attachContext">
            <font-awesome-icon icon="paperclip" />
          </button>
          <button class="send-btn" @click="sendMessage" :disabled="!inputText.trim() || loading || streaming">
            <font-awesome-icon v-if="!loading && !streaming" icon="paper-plane" />
            <font-awesome-icon v-else icon="spinner" spin />
          </button>
        </div>
      </div>
      <div class="input-status">
        <span v-if="selectedRole" class="role-indicator">
          {{ selectedRoleAvatar }} {{ selectedRole.name }}
        </span>
        <span v-if="currentDatabase" class="db-indicator">
          <font-awesome-icon icon="database" />
          {{ currentDatabase }}
          <span v-if="tables.length > 0"> · {{ tables.length }} 个表</span>
        </span>
        <span v-if="useStream" class="mode-indicator">
          <font-awesome-icon icon="stream" />
          流式输出
        </span>
      </div>
    </div>

    <!-- 角色管理面板 -->
    <div v-if="showRoleManager" class="role-panel">
      <RoleManager
        :selectedRoleId="selectedRole?.id"
        @select-role="onRoleSelect"
        @close="showRoleManager = false"
      />
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
import MarkdownRenderer from '../components/MarkdownRenderer.vue'
import RoleManager from '../components/RoleManager.vue'
import { aiApi } from '../services/aiApi'
import { connectionStore } from '../stores/connectionStore'

export default {
  name: 'AiChat',
  components: {
    MarkdownRenderer,
    RoleManager
  },
  data() {
    return {
      messages: [],
      inputText: '',
      loading: false,
      streaming: false,
      streamContent: '',
      aiConfigs: [],
      selectedConfig: null,
      showSettings: false,
      currentSession: null,
      currentDatabase: null,
      tables: [],
      showTableList: true,
      selectedRole: null,
      roles: [],
      showRoleManager: false,
      useStream: true,
      contextText: '',
      eventSource: null
    }
  },
  computed: {
    currentRoleAvatar() {
      return this.selectedRole?.avatar || '🤖'
    }
  },
  mounted() {
    this.loadAiConfigs()
    this.loadRoles()
    this.loadSessionInfo()
    this.loadChatHistory()
  },
  beforeUnmount() {
    if (this.eventSource) {
      this.eventSource.close()
    }
  },
  methods: {
    async loadAiConfigs() {
      try {
        await aiApi.ensureSyncedToBackend()
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

    async loadRoles() {
      try {
        const response = await fetch('/api/ai/roles')
        this.roles = await response.json()

        // 恢复上次选择的角色
        const savedRoleId = localStorage.getItem('selected_ai_role')
        if (savedRoleId) {
          this.selectedRole = this.roles.find(r => r.id === savedRoleId) || null
        }
      } catch (error) {
        console.error('加载角色失败', error)
      }
    },

    onRoleSelect(roleId) {
      this.selectedRole = this.roles.find(r => r.id === roleId) || null
      localStorage.setItem('selected_ai_role', roleId || '')
    },

    loadSessionInfo() {
      const sessions = connectionStore.getActiveSessions()
      if (sessions.length > 0) {
        this.currentSession = sessions[0]
        this.currentDatabase = this.currentSession.currentDatabase
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

    loadChatHistory() {
      const saved = localStorage.getItem('ai_chat_history')
      if (saved) {
        try {
          this.messages = JSON.parse(saved).slice(-50) // 只保留最近50条
        } catch (e) {
          console.error('加载聊天历史失败', e)
        }
      }
    },

    saveChatHistory() {
      localStorage.setItem('ai_chat_history', JSON.stringify(this.messages))
    },

    async sendMessage() {
      if (!this.inputText.trim() || this.loading || this.streaming) return

      const userMessage = {
        role: 'user',
        content: this.inputText,
        timestamp: new Date()
      }

      this.messages.push(userMessage)
      const input = this.inputText
      this.inputText = ''
      this.scrollToBottom()

      if (this.useStream) {
        await this.sendStreamMessage(input)
      } else {
        await this.sendNormalMessage(input)
      }

      this.saveChatHistory()
    },

    async sendStreamMessage(message) {
      this.streaming = true
      this.streamContent = ''

      try {
        // 构建URL
        let url = `/api/ai/chat/stream?message=${encodeURIComponent(message)}`
        if (this.selectedConfig) url += `&configId=${this.selectedConfig}`
        if (this.selectedRole) url += `&roleId=${this.selectedRole.id}`

        // 关闭之前的连接
        if (this.eventSource) {
          this.eventSource.close()
        }

        // 创建EventSource连接
        this.eventSource = new EventSource(url)

        this.eventSource.addEventListener('message', (event) => {
          try {
            const data = JSON.parse(event.data)
            if (data.content) {
              this.streamContent += data.content
              this.scrollToBottom()
            }
          } catch (e) {
            console.error('解析消息失败', e)
          }
        })

        this.eventSource.addEventListener('end', () => {
          this.finishStreaming()
        })

        this.eventSource.addEventListener('error', (event) => {
          console.error('流式连接错误', event)
          this.addErrorMessage('连接中断，请重试')
          this.streaming = false
          this.eventSource.close()
        })

      } catch (error) {
        console.error('发送消息失败', error)
        this.addErrorMessage('发送失败：' + error.message)
        this.streaming = false
      }
    },

    finishStreaming() {
      if (this.streamContent) {
        const assistantMessage = {
          role: 'assistant',
          content: this.streamContent,
          timestamp: new Date(),
          useMarkdown: true
        }

        // 检查是否包含SQL
        const sqlMatch = this.streamContent.match(/```sql\n?([\s\S]*?)\n?```/gi)
        if (sqlMatch && sqlMatch.length > 0) {
          assistantMessage.sql = sqlMatch.map(block =>
            block.replace(/```sql\n?/gi, '').replace(/```\s*$/gi, '').trim()
          ).join('\n\n')
        }

        this.messages.push(assistantMessage)
        this.saveChatHistory()
      }

      this.streaming = false
      this.streamContent = ''
      this.scrollToBottom()

      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
    },

    async sendNormalMessage(message) {
      this.loading = true

      try {
        const history = this.messages.slice(-10).map(msg => ({
          role: msg.role,
          content: msg.content
        }))

        const response = await fetch('/api/ai/chat/free', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message,
            configId: this.selectedConfig,
            roleId: this.selectedRole?.id,
            history
          })
        })

        const data = await response.json()

        if (response.ok) {
          const assistantMessage = {
            role: 'assistant',
            content: data.response,
            timestamp: new Date(),
            useMarkdown: true
          }

          // 检查是否包含SQL
          const sqlMatch = data.response.match(/```sql\n?([\s\S]*?)\n?```/gi)
          if (sqlMatch && sqlMatch.length > 0) {
            assistantMessage.sql = sqlMatch.map(block =>
              block.replace(/```sql\n?/gi, '').replace(/```\s*$/gi, '').trim()
            ).join('\n\n')
          }

          this.messages.push(assistantMessage)
        } else {
          this.addErrorMessage(data.error || '请求失败')
        }
      } catch (error) {
        console.error('发送消息失败', error)
        this.addErrorMessage('发送失败：' + error.message)
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },

    addErrorMessage(error) {
      this.messages.push({
        role: 'assistant',
        content: `❌ 错误：${error}`,
        timestamp: new Date()
      })
    },

    quickAction(type) {
      let prompt = ''

      switch(type) {
        case 'sql':
          prompt = '请帮我生成或优化SQL语句。' +
                  (this.currentDatabase ? `\n当前数据库：${this.currentDatabase}` : '') +
                  (this.tables.length > 0 ? `\n可用表：${this.tables.slice(0, 5).join(', ')}` : '')
          break
        case 'analyze':
          prompt = '请帮我分析数据或生成分析报告。'
          break
        case 'code':
          prompt = '请帮我生成代码或解释代码逻辑。'
          break
      }

      this.inputText = prompt
      this.$refs.messageInput?.focus()
    },

    attachContext() {
      if (this.contextText) {
        this.inputText = this.inputText + '\n\n--- 上下文 ---\n' + this.contextText
      }
    },

    onEnter(event) {
      if (!event.shiftKey) {
        this.sendMessage()
      }
    },

    clearChat() {
      if (confirm('确定要清空聊天记录吗？')) {
        this.messages = []
        this.saveChatHistory()
      }
    },

    onConfigChange() {
      localStorage.setItem('selected_ai_config', this.selectedConfig)
    },

    copySql(sql) {
      navigator.clipboard.writeText(sql).then(() => {
        // 可以添加toast提示
      })
    },

    executeSql(sql) {
      this.$emit('execute-sql', sql)
    },

    formatMessage(content) {
      // 基础格式化，保留兼容性
      return content
        .replace(/```sql\n?([\s\S]*?)\n?```/gi, '<pre class="sql-code"><code>$1</code></pre>')
        .replace(/```([\s\S]*?)```/gi, '<pre><code>$1</code></pre>')
        .replace(/\n/g, '<br>')
    },

    formatTime(timestamp) {
      return new Date(timestamp).toLocaleTimeString()
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
      this.contextText = `表名: ${tableName}`
      this.inputText = this.inputText + `\n表名: ${tableName}`
      this.$refs.messageInput?.focus()
    },

    async describeTable(tableName) {
      if (!this.currentSession) {
        this.addErrorMessage('请先连接数据库')
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
          let structure = `## 表 \`${tableName}\` 的结构\n\n`
          structure += '| 字段名 | 类型 | 是否为空 | 键 | 额外 |\n'
          structure += '|--------|------|----------|-----|------|\n'

          result.data.data.forEach(col => {
            structure += `| ${col.Field} | ${col.Type} | ${col.Null} | ${col.Key || ''} | ${col.Extra || ''} |\n`
          })

          this.contextText = structure
          this.inputText = this.inputText + '\n\n' + structure
        } else {
          this.addErrorMessage(`无法获取表 ${tableName} 的结构`)
        }
      } catch (error) {
        this.addErrorMessage('获取表结构失败: ' + error.message)
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
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: var(--bg-secondary);
  gap: var(--spacing-md);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
}

.chat-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.header-actions {
  display: flex;
  gap: var(--spacing-xs);
}

.config-select {
  padding: 4px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  min-width: 120px;
}

.btn-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.btn-icon:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.chat-body {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.role-selector {
  padding: var(--spacing-sm) var(--spacing-lg);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
}

.current-role {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background-color: var(--accent-primary-bg);
  border: 1px solid var(--accent-primary);
  border-radius: var(--radius-sm);
  color: var(--accent-primary);
}

.role-avatar {
  font-size: 20px;
}

.role-name {
  font-weight: 500;
  flex: 1;
}

.toolbar {
  padding: var(--spacing-sm) var(--spacing-lg);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
}

.toolbar-section {
  display: flex;
  gap: var(--spacing-xs);
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.tool-btn:hover,
.tool-btn.active {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.tool-btn span {
  font-weight: 500;
}

.table-panel {
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
  max-height: 200px;
  overflow-y: auto;
}

.table-grid {
  padding: var(--spacing-sm);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--spacing-sm);
}

.table-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm);
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.table-card:hover {
  background-color: var(--bg-highlight);
  border-color: var(--accent-primary);
}

.table-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex: 1;
  min-width: 0;
}

.table-icon {
  color: var(--text-tertiary);
  font-size: 14px;
  flex-shrink: 0;
}

.table-name {
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-mini {
  padding: 2px 8px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  font-size: 11px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.btn-mini:hover {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
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

.role-hint {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
  background-color: var(--accent-primary-bg);
  border: 1px solid var(--accent-primary);
  border-radius: var(--radius-sm);
  color: var(--accent-primary);
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
  background-color: var(--bg-tertiary);
}

.message.user .message-avatar {
  background-color: var(--accent-primary);
}

.message-content {
  flex: 1;
}

.message-text {
  color: var(--text-primary);
  line-height: 1.6;
  margin-bottom: var(--spacing-sm);
}

.streaming-content {
  display: flex;
  align-items: flex-end;
}

.cursor {
  animation: blink 1s infinite;
  color: var(--accent-primary);
  font-weight: bold;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
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

.btn-small {
  padding: 4px 8px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 12px;
  cursor: pointer;
  transition: var(--transition-fast);
}

.btn-small:hover {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.message-time {
  font-size: 12px;
  color: var(--text-tertiary);
}

.chat-input {
  border-top: 1px solid var(--border-primary);
  background-color: var(--bg-secondary);
  padding: var(--spacing-md) var(--spacing-lg);
}

.input-container {
  display: flex;
  gap: var(--spacing-sm);
  align-items: flex-end;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm);
  transition: var(--transition-fast);
}

.input-container:focus-within {
  border-color: var(--accent-primary);
}

.input-container textarea {
  flex: 1;
  background: none;
  border: none;
  color: var(--text-primary);
  font-size: 14px;
  resize: none;
  font-family: var(--font-family-base);
  line-height: 1.5;
  padding: 0;
}

.input-container textarea::placeholder {
  color: var(--text-tertiary);
}

.input-container textarea:focus {
  outline: none;
}

.input-actions {
  display: flex;
  gap: var(--spacing-xs);
  align-items: center;
}

.btn-attach {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.btn-attach:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.send-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  background-color: var(--accent-primary-hover);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-xs);
  padding: 4px 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.role-indicator,
.db-indicator,
.mode-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
}

.role-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  width: 400px;
  background-color: var(--bg-primary);
  border-left: 1px solid var(--border-primary);
  z-index: 1000;
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
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