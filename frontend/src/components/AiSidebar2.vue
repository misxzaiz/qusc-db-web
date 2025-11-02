<template>
  <div class="ai-sidebar" :class="{ expanded: isExpanded }">
    <div class="ai-header">
      <button class="toggle-btn" @click="toggleSidebar" :title="isExpanded ? '收起' : '展开'">
        <font-awesome-icon icon="comments" />
      </button>
      <div v-if="isExpanded" class="header-title">
        <span>AI助手</span>
        <div class="header-actions">
          <button class="btn-icon" @click="showRoleManager = !showRoleManager" title="角色管理">
            <font-awesome-icon icon="user-friends" />
          </button>
          <button class="btn-icon" @click="clearChat" title="清空">
            <font-awesome-icon icon="trash" />
          </button>
        </div>
      </div>
    </div>

    <div v-if="isExpanded" class="ai-content">
      <!-- 配置选择器 -->
      <div class="config-selector">
        <select v-model="selectedConfig" @change="onConfigChange" class="config-select">
          <option v-for="config in aiConfigs" :key="config.id" :value="config.id">
            {{ config.name }}
          </option>
        </select>
      </div>

      <!-- 角色选择 -->
      <div v-if="selectedRole" class="role-bar">
        <span class="role-avatar">{{ selectedRole.avatar || '🤖' }}</span>
        <span class="role-name">{{ selectedRole.name }}</span>
        <button class="btn-icon small" @click="selectedRole = null" title="切换">
          <font-awesome-icon icon="times" />
        </button>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <button class="tool-btn" @click="useStream = !useStream" :class="{ active: useStream }" title="流式输出">
          <font-awesome-icon icon="stream" />
        </button>
        <button class="tool-btn" @click="quickAction('sql')" title="SQL">
          <font-awesome-icon icon="database" />
        </button>
        <button class="tool-btn" @click="quickAction('analyze')" title="分析">
          <font-awesome-icon icon="chart-bar" />
        </button>
        <button class="tool-btn" @click="quickAction('code')" title="代码">
          <font-awesome-icon icon="code" />
        </button>
      </div>

      <!-- 聊天消息 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="welcome">
          <div class="welcome-icon">👋</div>
          <div class="welcome-text">
            <div>AI助手已就绪</div>
            <div class="welcome-hint" v-if="selectedRole">
              {{ selectedRole.name }} - {{ selectedRole.description }}
            </div>
          </div>
        </div>

        <div v-for="(msg, i) in messages" :key="i" class="message" :class="msg.role">
          <div class="msg-avatar">
            <span v-if="msg.role === 'user'">👤</span>
            <span v-else>{{ currentRoleAvatar || '🤖' }}</span>
          </div>
          <div class="msg-content">
            <div class="msg-text">
              <MarkdownRenderer v-if="msg.role === 'assistant'" :content="msg.content" />
              <div v-else>{{ msg.content }}</div>
            </div>
            <div v-if="msg.sql" class="msg-actions">
              <button class="btn-small" @click="copySql(msg.sql)">复制</button>
              <button class="btn-small" @click="executeSql(msg.sql)">执行</button>
            </div>
          </div>
        </div>

        <!-- 流式输出 -->
        <div v-if="streaming" class="message assistant streaming">
          <div class="msg-avatar">
            <span>{{ currentRoleAvatar || '🤖' }}</span>
          </div>
          <div class="msg-content">
            <div class="msg-text">
              <MarkdownRenderer :content="streamContent" />
              <span class="cursor">|</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-wrapper">
          <textarea
            v-model="inputText"
            :placeholder="selectedRole ? `以${selectedRole.name}身份...` : '输入问题...'"
            rows="2"
            @keydown.enter.prevent="onEnter"
            :disabled="loading || streaming"
            ref="messageInput"
          ></textarea>
          <button
            class="send-btn"
            @click="sendMessage"
            :disabled="!inputText.trim() || loading || streaming"
          >
            <font-awesome-icon v-if="!loading && !streaming" icon="paper-plane" />
            <font-awesome-icon v-else icon="spinner" spin />
          </button>
        </div>
        <div class="input-status">
          <span v-if="useStream" class="status-item">
            <font-awesome-icon icon="stream" />
            流式
          </span>
        </div>
      </div>
    </div>

    <!-- 角色管理面板 -->
    <div v-if="showRoleManager && isExpanded" class="role-manager-panel">
      <div class="panel-header">
        <h4>角色管理</h4>
        <button class="btn-icon" @click="showCreateRole = true" title="创建角色">
          <font-awesome-icon icon="plus" />
        </button>
      </div>
      <div class="role-list">
        <div
          v-for="role in roles"
          :key="role.id"
          class="role-item"
          :class="{ active: selectedRole?.id === role.id }"
          @click="selectRole(role)"
        >
          <span class="role-avatar">{{ role.avatar || '🤖' }}</span>
          <div class="role-info">
            <div class="role-name">{{ role.name }}</div>
            <div class="role-desc">{{ role.description }}</div>
          </div>
          <button v-if="role.isCustom" class="btn-icon small danger" @click.stop="deleteRole(role)">
            <font-awesome-icon icon="trash" />
          </button>
        </div>
      </div>
    </div>

    <!-- 创建角色对话框 -->
    <div v-if="showCreateRole" class="dialog-overlay" @click="showCreateRole = false">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h4>创建角色</h4>
          <button class="close-btn" @click="showCreateRole = false">×</button>
        </div>
        <div class="dialog-body">
          <input v-model="newRole.name" placeholder="角色名称" class="dialog-input" />
          <input v-model="newRole.avatar" placeholder="头像(emoji)" maxlength="2" class="dialog-input" />
          <input v-model="newRole.description" placeholder="描述" class="dialog-input" />
          <textarea v-model="newRole.systemPrompt" placeholder="系统提示词" rows="3" class="dialog-input"></textarea>
        </div>
        <div class="dialog-footer">
          <button class="btn" @click="showCreateRole = false">取消</button>
          <button class="btn btn-primary" @click="createRole">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import MarkdownRenderer from './MarkdownRenderer.vue'
import { aiApi } from '../services/aiApi'
import { connectionStore } from '../stores/connectionStore'

export default {
  name: 'AiSidebar',
  components: {
    MarkdownRenderer
  },
  emits: ['execute-sql'],
  data() {
    return {
      isExpanded: false,
      messages: [],
      inputText: '',
      loading: false,
      streaming: false,
      streamContent: '',
      aiConfigs: [],
      selectedConfig: null,
      selectedRole: null,
      roles: [],
      showRoleManager: false,
      showCreateRole: false,
      newRole: {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      },
      useStream: true,
      eventSource: null
    }
  },
  computed: {
    currentRoleAvatar() {
      return this.selectedRole?.avatar || '🤖'
    }
  },
  mounted() {
    this.loadConfigs()
    this.loadRoles()
    this.loadHistory()
  },
  beforeUnmount() {
    if (this.eventSource) {
      this.eventSource.close()
    }
  },
  methods: {
    toggleSidebar() {
      this.isExpanded = !this.isExpanded
      if (this.isExpanded) {
        this.$nextTick(() => this.scrollToBottom())
      }
      // 通知父组件侧边栏状态变化
      this.$emit('toggle', this.isExpanded)
    },

    async loadConfigs() {
      try {
        await aiApi.ensureSyncedToBackend()
        const response = await aiApi.getConfigs()
        this.aiConfigs = response.data
        if (this.aiConfigs.length > 0) {
          const saved = localStorage.getItem('selected_ai_config')
          this.selectedConfig = saved || this.aiConfigs[0].id
        }
      } catch (error) {
        console.error('加载配置失败', error)
      }
    },

    async loadRoles() {
      try {
        const response = await fetch('/api/ai/roles')
        this.roles = await response.json()
        const savedRoleId = localStorage.getItem('selected_ai_role')
        if (savedRoleId) {
          this.selectedRole = this.roles.find(r => r.id === savedRoleId) || null
        }
      } catch (error) {
        console.error('加载角色失败', error)
      }
    },

    loadHistory() {
      const saved = localStorage.getItem('ai_sidebar_history')
      if (saved) {
        try {
          this.messages = JSON.parse(saved).slice(-30)
        } catch (e) {
          console.error('加载历史失败', e)
        }
      }
    },

    saveHistory() {
      localStorage.setItem('ai_sidebar_history', JSON.stringify(this.messages))
    },

    onConfigChange() {
      localStorage.setItem('selected_ai_config', this.selectedConfig)
    },

    selectRole(role) {
      this.selectedRole = role
      localStorage.setItem('selected_ai_role', role.id)
    },

    async createRole() {
      if (!this.newRole.name || !this.newRole.systemPrompt) {
        alert('请填写名称和提示词')
        return
      }

      try {
        const response = await fetch('/api/ai/roles', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(this.newRole)
        })

        if (response.ok) {
          await this.loadRoles()
          this.showCreateRole = false
          this.newRole = { name: '', avatar: '', description: '', systemPrompt: '' }
        }
      } catch (error) {
        console.error('创建角色失败', error)
      }
    },

    async deleteRole(role) {
      if (!confirm(`删除角色 ${role.name}?`)) return

      try {
        const response = await fetch(`/api/ai/roles/${role.id}`, {
          method: 'DELETE'
        })
        if (response.ok) {
          await this.loadRoles()
          if (this.selectedRole?.id === role.id) {
            this.selectedRole = null
          }
        }
      } catch (error) {
        console.error('删除角色失败', error)
      }
    },

    quickAction(type) {
      const prompts = {
        sql: '帮我生成SQL语句',
        analyze: '帮我分析数据',
        code: '帮我生成代码'
      }
      this.inputText = prompts[type]
      this.$refs.messageInput?.focus()
    },

    async sendMessage() {
      if (!this.inputText.trim() || this.loading || this.streaming) return

      this.messages.push({
        role: 'user',
        content: this.inputText,
        timestamp: new Date()
      })

      const message = this.inputText
      this.inputText = ''
      this.scrollToBottom()

      if (this.useStream) {
        await this.streamMessage(message)
      } else {
        await this.normalMessage(message)
      }

      this.saveHistory()
    },

    async streamMessage(message) {
      this.streaming = true
      this.streamContent = ''

      try {
        let url = `/api/ai/chat/stream?message=${encodeURIComponent(message)}`
        if (this.selectedConfig) url += `&configId=${this.selectedConfig}`
        if (this.selectedRole) url += `&roleId=${this.selectedRole.id}`

        console.log('发起流式请求:', url)

        if (this.eventSource) this.eventSource.close()
        this.eventSource = new EventSource(url)

        this.eventSource.onopen = (e) => {
          console.log('SSE连接已建立')
        }

        this.eventSource.addEventListener('connected', (e) => {
          console.log('收到connected事件:', e.data)
        })

        this.eventSource.addEventListener('message', (e) => {
          console.log('收到SSE消息:', e.data)
          try {
            const data = JSON.parse(e.data)
            if (data.content) {
              this.streamContent += data.content
              this.scrollToBottom()
            } else if (data.done) {
              this.finishStream()
            }
          } catch (err) {
            console.error('解析消息失败', err, e.data)
          }
        })

        this.eventSource.addEventListener('end', () => {
          console.log('收到结束事件')
          this.finishStream()
        })

        this.eventSource.addEventListener('error', (e) => {
          console.error('SSE错误:', e)
          this.addError('连接中断')
          this.streaming = false
        })
      } catch (error) {
        console.error('发送失败', error)
        this.addError('发送失败')
        this.streaming = false
      }
    },

    finishStream() {
      if (this.streamContent) {
        this.messages.push({
          role: 'assistant',
          content: this.streamContent,
          timestamp: new Date()
        })
      }
      this.streaming = false
      this.streamContent = ''
      this.scrollToBottom()
      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
    },

    async normalMessage(message) {
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
          this.messages.push({
            role: 'assistant',
            content: data.response,
            timestamp: new Date()
          })
        } else {
          this.addError(data.error)
        }
      } catch (error) {
        console.error('发送失败', error)
        this.addError('发送失败')
      } finally {
        this.loading = false
        this.scrollToBottom()
      }
    },

    addError(msg) {
      this.messages.push({
        role: 'assistant',
        content: `❌ 错误：${msg}`,
        timestamp: new Date()
      })
    },

    onEnter(e) {
      if (!e.shiftKey) {
        this.sendMessage()
      }
    },

    clearChat() {
      if (confirm('清空聊天记录？')) {
        this.messages = []
        this.saveHistory()
      }
    },

    copySql(sql) {
      navigator.clipboard.writeText(sql)
    },

    executeSql(sql) {
      this.$emit('execute-sql', sql)
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messagesContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    }
  }
}
</script>

<style scoped>
.ai-sidebar {
  position: fixed;
  right: 0;
  top: 56px;
  bottom: 0;
  width: 50px;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  transition: width 0.3s ease;
  z-index: 100;
  display: flex;
  flex-direction: column;
}

.ai-sidebar.expanded {
  width: 400px;
}

.ai-header {
  height: 50px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border-bottom: 1px solid var(--border-primary);
}

.toggle-btn {
  width: 30px;
  height: 30px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.toggle-btn:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.header-title {
  flex: 1;
  margin-left: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 4px;
}

.btn-icon {
  width: 24px;
  height: 24px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.btn-icon:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.btn-icon.small {
  width: 20px;
  height: 20px;
  font-size: 10px;
}

.btn-icon.danger {
  color: var(--danger-color);
}

.btn-icon.danger:hover {
  background-color: var(--danger-bg);
}

.ai-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-selector {
  padding: 10px;
  border-bottom: 1px solid var(--border-primary);
}

.config-select {
  width: 100%;
  padding: 4px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 12px;
}

.role-bar {
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--accent-primary-bg);
  border-bottom: 1px solid var(--border-primary);
}

.role-avatar {
  font-size: 18px;
}

.role-name {
  flex: 1;
  font-size: 12px;
  color: var(--accent-primary);
  font-weight: 500;
}

.toolbar {
  padding: 8px 10px;
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border-primary);
}

.tool-btn {
  width: 28px;
  height: 28px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: var(--transition-fast);
}

.tool-btn:hover,
.tool-btn.active {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.welcome {
  text-align: center;
  color: var(--text-secondary);
  padding: 20px 0;
}

.welcome-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.welcome-text {
  font-size: 14px;
}

.welcome-hint {
  font-size: 12px;
  color: var(--accent-primary);
  margin-top: 4px;
}

.message {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.msg-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background-color: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}

.message.user .msg-avatar {
  background-color: var(--accent-primary);
}

.msg-content {
  flex: 1;
  min-width: 0;
}

.msg-text {
  color: var(--text-primary);
  font-size: 13px;
  line-height: 1.4;
  word-wrap: break-word;
}

.cursor {
  animation: blink 1s infinite;
  color: var(--accent-primary);
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.msg-actions {
  margin-top: 4px;
  display: flex;
  gap: 4px;
}

.btn-small {
  padding: 2px 6px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  color: var(--text-primary);
  font-size: 11px;
  cursor: pointer;
}

.btn-small:hover {
  background-color: var(--accent-primary);
  color: white;
}

.input-area {
  border-top: 1px solid var(--border-primary);
  padding: 10px;
}

.input-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-wrapper textarea {
  flex: 1;
  padding: 6px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  resize: none;
  font-family: inherit;
}

.input-wrapper textarea:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.send-btn {
  width: 32px;
  height: 32px;
  background-color: var(--accent-primary);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-status {
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.status-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.role-manager-panel {
  position: absolute;
  right: 100%;
  top: 0;
  bottom: 0;
  width: 300px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-right: none;
  display: flex;
  flex-direction: column;
}

.panel-header {
  padding: 10px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.panel-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.role-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  margin-bottom: 4px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.role-item:hover {
  background-color: var(--bg-highlight);
}

.role-item.active {
  border-color: var(--accent-primary);
  background-color: var(--accent-primary-bg);
}

.role-info {
  flex: 1;
  min-width: 0;
}

.role-name {
  font-size: 12px;
  color: var(--text-primary);
  font-weight: 500;
}

.role-desc {
  font-size: 11px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  border-radius: var(--radius-md);
  width: 350px;
  max-width: 90%;
}

.dialog-header {
  padding: 12px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 20px;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-body {
  padding: 12px;
}

.dialog-input {
  width: 100%;
  padding: 6px 8px;
  margin-bottom: 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  box-sizing: border-box;
}

.dialog-input:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.dialog-footer {
  padding: 12px;
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 6px 12px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  cursor: pointer;
  font-size: 12px;
}

.btn:hover {
  background-color: var(--bg-highlight);
}

.btn-primary {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.btn-primary:hover {
  background-color: var(--accent-primary-hover);
}
</style>