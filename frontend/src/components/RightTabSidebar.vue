<template>
  <div class="right-sidebar" :class="{ collapsed: isCollapsed }">
    <!-- 右侧导航图标 -->
    <div class="nav-icons">
      <!-- AI助手 -->
      <button
        class="nav-icon"
        :class="{ active: !isCollapsed && activeTab === 'ai' }"
        @click="toggleTab('ai')"
        title="AI助手"
      >
        <font-awesome-icon icon="comments" />
      </button>

      <!-- 预留其他功能图标 -->
      <!--
      <button
        class="nav-icon"
        :class="{ active: !isCollapsed && activeTab === 'tools' }"
        @click="toggleTab('tools')"
        title="工具"
      >
        <font-awesome-icon icon="tools" />
      </button>
      <button
        class="nav-icon"
        :class="{ active: !isCollapsed && activeTab === 'settings' }"
        @click="toggleTab('settings')"
        title="设置"
      >
        <font-awesome-icon icon="cog" />
      </button>
      -->
    </div>

    <!-- 弹出面板 -->
    <div v-if="!isCollapsed" class="popup-panel">
      <!-- AI助手 -->
      <div v-if="activeTab === 'ai'" class="panel-content ai-panel">
        <div class="ai-header">
          <span class="header-title">AI助手</span>
          <div class="header-actions">
            <button class="btn-icon" @click="showRoleManager = !showRoleManager" title="角色管理">
              <font-awesome-icon icon="user-friends" />
            </button>
            <button class="btn-icon" @click="clearChat" title="清空">
              <font-awesome-icon icon="trash" />
            </button>
          </div>
        </div>

        <!-- AI配置选择器 -->
        <div class="ai-config-bar">
          <select v-model="selectedConfig" @change="onConfigChange" class="compact-select">
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

        <!-- 无角色提示 -->
        <div v-else class="no-role-hint">
          <span class="hint-icon">💡</span>
          <span class="hint-text">请先创建一个AI角色</span>
          <button class="btn-icon small" @click="showCreateRole = true" title="创建角色">
            <font-awesome-icon icon="plus" />
          </button>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
          <button class="tool-btn" @click="useStream = !useStream" :class="{ active: useStream }" title="流式输出">
            <font-awesome-icon icon="bolt" />
          </button>
          <button class="tool-btn" @click="showPrompts = !showPrompts" :class="{ active: showPrompts }" title="提示词">
            <font-awesome-icon icon="lightbulb" />
          </button>
        </div>

        <!-- 聊天消息 -->
        <div ref="messagesContainer" class="messages-container">
          <div v-for="(msg, index) in messages" :key="index" class="message" :class="msg.role">
            <div class="message-avatar">
              <span v-if="msg.role === 'user'">👤</span>
              <span v-else>{{ currentRoleAvatar }}</span>
            </div>
            <div class="message-content">
              <div v-if="msg.role === 'assistant' && msg.streaming" class="streaming-text">
                {{ msg.content }}<span class="cursor">|</span>
              </div>
              <MarkdownRenderer v-else :content="msg.content" />
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-container">
          <!-- @表引用标签栏 -->
          <div v-if="referencedTables.size > 0" class="table-tags-bar">
            <span
              v-for="[tableName, info] in referencedTables"
              :key="tableName"
              class="table-tag"
              :class="{ active: info.active }"
              @click="toggleTableActive(tableName)"
            >
              @{{ tableName }}
              <button class="remove-tag" @click.stop="removeTableReference(tableName)">
                <font-awesome-icon icon="times" />
              </button>
            </span>
          </div>

          <!-- 快捷提示词 -->
          <div v-if="showPrompts" class="quick-prompts">
            <button
              v-for="(prompt, type) in quickPrompts"
              :key="type"
              class="prompt-btn"
              @click="insertPrompt(type)"
            >
              {{ prompt }}
            </button>
          </div>

          <!-- 输入框 -->
          <div class="input-wrapper">
            <textarea
              ref="messageInput"
              v-model="inputText"
              placeholder="输入消息... (@引用表名)"
              class="message-input"
              @keydown="handleKeyDown"
              @input="handleInput"
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
              <font-awesome-icon icon="bolt" /> 流式
            </span>
            <span v-if="selectedRole" class="status-item">
              {{ selectedRole.name }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 角色管理弹窗 -->
    <div v-if="showRoleManager" class="modal-overlay" @click.self="showRoleManager = false">
      <div class="modal">
        <div class="modal-header">
          <h3>AI角色管理</h3>
          <button class="close-btn" @click="showRoleManager = false">
            <font-awesome-icon icon="times" />
          </button>
        </div>
        <div class="modal-content">
          <!-- 角色列表 -->
          <div class="role-list">
            <div
              v-for="role in roles"
              :key="role.id"
              class="role-item"
              :class="{ active: selectedRole?.id === role.id }"
              @click="selectedRole = role"
            >
              <span class="role-avatar">{{ role.avatar || '🤖' }}</span>
              <div class="role-info">
                <span class="role-name">{{ role.name }}</span>
                <span class="role-desc">{{ role.description }}</span>
              </div>
              <button class="delete-role" @click.stop="deleteRole(role.id)">
                <font-awesome-icon icon="trash" />
              </button>
            </div>
          </div>
          <button class="create-role-btn" @click="showCreateRole = true">
            <font-awesome-icon icon="plus" /> 创建新角色
          </button>
        </div>
      </div>
    </div>

    <!-- 创建角色弹窗 -->
    <div v-if="showCreateRole" class="modal-overlay" @click.self="showCreateRole = false">
      <div class="modal">
        <div class="modal-header">
          <h3>创建AI角色</h3>
          <button class="close-btn" @click="showCreateRole = false">
            <font-awesome-icon icon="times" />
          </button>
        </div>
        <div class="modal-content">
          <div class="form-group">
            <label>角色名称</label>
            <input v-model="newRole.name" placeholder="例如：SQL专家" />
          </div>
          <div class="form-group">
            <label>头像</label>
            <input v-model="newRole.avatar" placeholder="例如：👨‍💻" maxlength="2" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <textarea v-model="newRole.description" placeholder="简短描述角色特点"></textarea>
          </div>
          <div class="form-group">
            <label>系统提示词</label>
            <textarea v-model="newRole.systemPrompt" placeholder="定义角色的行为和说话方式"></textarea>
          </div>
          <div class="form-actions">
            <button class="btn-secondary" @click="generateRolePrompt">AI生成</button>
            <button class="btn-primary" @click="createRole">创建</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import MarkdownRenderer from './MarkdownRenderer.vue'
import { aiApi } from '../services/aiApi'
import { connectionStore } from '../stores/connectionStore'
import { faMagic } from '@fortawesome/free-solid-svg-icons'
import { library } from '@fortawesome/fontawesome-svg-core'

library.add(faMagic)

export default {
  name: 'RightTabSidebar',

  components: {
    MarkdownRenderer
  },

  emits: ['execute-sql', 'resize', 'toggle'],

  data() {
    return {
      isCollapsed: true,
      activeTab: 'ai',

      // AI相关数据
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
      showPrompts: false,
      useStream: true,
      referencedTables: new Map(),
      tableSelectorVisible: false,
      tableSelectorPosition: { x: 0, y: 0 },
      tableSearchQuery: '',
      availableTables: [],
      newRole: {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      },
      quickPrompts: {
        explain: '解释SQL',
        optimize: '优化SQL',
        error: '分析错误'
      },
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
    // 初始宽度
    this.$emit('resize', this.isCollapsed ? 40 : 400)
  },

  watch: {
    isCollapsed() {
      this.$emit('resize', this.isCollapsed ? 40 : 400)
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

    // AI相关方法（从AiSidebar2.vue复制）
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
        console.error('加载AI配置失败:', error)
      }
    },

    loadRoles() {
      const saved = localStorage.getItem('ai_roles')
      if (saved) {
        this.roles = JSON.parse(saved)
      }
    },

    loadHistory() {
      const saved = localStorage.getItem('ai_chat_history')
      if (saved) {
        this.messages = JSON.parse(saved)
      }
    },

    saveHistory() {
      localStorage.setItem('ai_chat_history', JSON.stringify(this.messages))
    },

    clearChat() {
      this.messages = []
      this.saveHistory()
    },

    // 确保侧边栏展开并切换到AI标签
    ensureAiTabOpen() {
      if (this.isCollapsed) {
        this.isCollapsed = false
      }
      this.activeTab = 'ai'
    },

    async sendMessage(customMessage = null) {
      const message = customMessage || this.inputText
      if (!message.trim() || this.loading || this.streaming) return

      this.messages.push({
        role: 'user',
        content: message,
        timestamp: new Date()
      })

      if (!customMessage) {
        this.inputText = ''
      }
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
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole?.systemPrompt || ''
        const tableContexts = Array.from(this.referencedTables.values())
          .filter(info => info.active && info.createSql)
          .map(info => ({
            tableName: info.tableName,
            createSql: info.createSql
          }))

        this.eventSource = aiApi.streamChat(message, this.selectedConfig, systemPrompt, history, tableContexts)

        this.eventSource.onmessage = (event) => {
          const data = event.data
          // 处理SSE格式数据
          if (data.startsWith('data: ')) {
            const jsonStr = data.slice(6) // 移除 'data: ' 前缀
            try {
              const parsed = JSON.parse(jsonStr)

              // 检查是否完成
              if (parsed.done || parsed.status === 'done') {
                this.streaming = false
                // 将最后一条消息标记为非流式
                const lastMessage = this.messages[this.messages.length - 1]
                if (lastMessage && lastMessage.role === 'assistant' && lastMessage.streaming) {
                  lastMessage.streaming = false
                }
                this.scrollToBottom()
              } else if (parsed.content) {
                this.streamContent += parsed.content

                // 更新或添加消息
                const lastMessage = this.messages[this.messages.length - 1]
                if (lastMessage && lastMessage.role === 'assistant' && lastMessage.streaming) {
                  lastMessage.content = this.streamContent
                } else {
                  this.messages.push({
                    role: 'assistant',
                    content: this.streamContent,
                    streaming: true,
                    timestamp: new Date()
                  })
                }
                this.scrollToBottom()
              }
            } catch (e) {
              console.error('解析流数据失败:', e, '原始数据:', data)
            }
          }
        }

        this.eventSource.onerror = (error) => {
          console.error('流式连接错误:', error)
          this.streaming = false
          this.eventSource?.close()
        }
      } catch (error) {
        console.error('发送消息失败:', error)
        this.streaming = false
      }
    },

    async normalMessage(message) {
      this.loading = true
      try {
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole?.systemPrompt || ''
        const tableContexts = Array.from(this.referencedTables.values())
          .filter(info => info.active && info.createSql)
          .map(info => ({
            tableName: info.tableName,
            createSql: info.createSql
          }))

        const response = await aiApi.freeChat(message, this.selectedConfig, systemPrompt, history, tableContexts)

        this.messages.push({
          role: 'assistant',
          content: response.data.response,
          timestamp: new Date()
        })

        this.scrollToBottom()
      } catch (error) {
        console.error('发送消息失败:', error)
        this.messages.push({
          role: 'assistant',
          content: '抱歉，发送消息时出现错误。',
          timestamp: new Date()
        })
      } finally {
        this.loading = false
      }
    },

    getFilteredHistory() {
      return this.messages.slice(-10).map(msg => ({
        role: msg.role,
        content: msg.content
      }))
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messagesContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    },

    // 其他方法...
    onConfigChange() {
      localStorage.setItem('selected_ai_config', this.selectedConfig)
    },

    insertPrompt(type) {
      const prompts = {
        explain: '请解释以下SQL语句的执行逻辑：',
        optimize: '请帮我优化以下SQL语句，提高性能：',
        error: '我遇到了SQL错误，请帮我分析原因：'
      }
      this.inputText = prompts[type]
      this.$refs.messageInput?.focus()
    },

    handleKeyDown(event) {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault()
        this.sendMessage()
      }
    },

    handleInput(event) {
      const text = event.target.value
      const cursorPos = event.target.selectionStart

      // 检测@符号
      const atIndex = text.lastIndexOf('@', cursorPos - 1)
      if (atIndex !== -1) {
        const searchText = text.substring(atIndex + 1, cursorPos)
        if (searchText.includes(' ')) {
          this.tableSelectorVisible = false
        } else {
          this.showTableSelector(atIndex, searchText)
        }
      } else {
        this.tableSelectorVisible = false
      }
    },

    // 其他AI相关方法（简化版）
    createRole() {
      if (!this.newRole.name) return

      const role = {
        id: Date.now().toString(),
        ...this.newRole,
        createdAt: new Date().toISOString()
      }

      this.roles.push(role)
      this.saveRoles()

      this.newRole = {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      }
      this.showCreateRole = false
    },

    deleteRole(roleId) {
      this.roles = this.roles.filter(r => r.id !== roleId)
      if (this.selectedRole?.id === roleId) {
        this.selectedRole = null
      }
      this.saveRoles()
    },

    saveRoles() {
      localStorage.setItem('ai_roles', JSON.stringify(this.roles))
    },

    generateRolePrompt() {
      // AI生成角色提示词的逻辑
      console.log('AI生成角色提示词')
    },

    toggleTableActive(tableName) {
      const table = this.referencedTables.get(tableName)
      if (table) {
        table.active = !table.active
      }
    },

    removeTableReference(tableName) {
      this.referencedTables.delete(tableName)
    },

    showTableSelector(atIndex, searchText) {
      // 显示表选择器的逻辑
      console.log('显示表选择器')
    }
  }
}
</script>

<style scoped>
.right-sidebar {
  position: fixed;
  right: 0;
  top: 56px; /* header高度 */
  bottom: 0;
  width: 40px;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  transition: width 0.3s ease;
  z-index: 100; /* 降低z-index，避免覆盖header */
  display: flex;
  flex-direction: column;
}

.right-sidebar.collapsed {
  width: 40px;
}

/* 展开时需要增加宽度以容纳面板 */
.right-sidebar:not(.collapsed) {
  width: 400px; /* 40px图标 + 360px面板 */
}

.nav-icons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  width: 40px;
  height: auto;
  position: absolute;
  top: 0;
  right: 0; /* 固定在右侧 */
  z-index: 10; /* 确保在面板之上 */
}

.nav-icon {
  width: 40px;
  height: 40px;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0 8px 8px 0; /* 右侧圆角 */
  transition: all 0.2s ease;
  font-size: 16px;
}

.nav-icon:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.nav-icon.active {
  background-color: var(--primary-color);
  color: white;
}

.popup-panel {
  position: absolute;
  right: 40px; /* 图标宽度 */
  top: 0;
  bottom: 0;
  width: 360px;
  background-color: var(--bg-primary);
  border-right: 1px solid var(--border-primary);
  border-left: none;
}

.panel-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* AI面板样式 */
.ai-panel {
  position: relative;
}

.ai-header {
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 15px;
  border-bottom: 1px solid var(--border-primary);
}

.header-title {
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
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.btn-icon.small {
  width: 20px;
  height: 20px;
  font-size: 12px;
}

.ai-config-bar {
  padding: 10px 15px;
  border-bottom: 1px solid var(--border-primary);
}

.compact-select {
  width: 100%;
  padding: 6px 10px;
  background-color: var(--bg-input);
  border: 1px solid var(--border-primary);
  color: var(--text-primary);
  border-radius: 4px;
  font-size: 12px;
}

.role-bar {
  padding: 10px 15px;
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
}

.role-avatar {
  font-size: 20px;
}

.role-name {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
}

.no-role-hint {
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
}

.hint-icon {
  font-size: 20px;
}

.hint-text {
  flex: 1;
  color: var(--text-secondary);
  font-size: 13px;
}

.toolbar {
  padding: 8px 15px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-primary);
}

.tool-btn {
  width: 28px;
  height: 28px;
  background: none;
  border: 1px solid var(--border-primary);
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  transition: all 0.2s ease;
}

.tool-btn:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.tool-btn.active {
  background-color: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
}

.message {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

.message.avatar {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background-color: var(--bg-secondary);
  font-size: 14px;
}

.message-content {
  flex: 1;
  max-width: calc(100% - 50px);
}

/* 用户消息右对齐 */
.message.user .message-content {
  text-align: right;
}

/* AI消息左对齐 */
.message.assistant .message-content {
  text-align: left;
}

/* 消息气泡样式 */
.message.user .message-content {
  background-color: var(--accent-primary);
  color: white;
  padding: 10px 15px;
  border-radius: 18px 18px 4px 18px;
  display: inline-block;
  margin-left: auto;
}

.message.assistant .message-content {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  padding: 10px 15px;
  border-radius: 18px 18px 18px 4px;
  display: inline-block;
}

.streaming-text {
  color: var(--text-primary);
  line-height: 1.5;
}

.cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.input-container {
  border-top: 1px solid var(--border-primary);
}

.table-tags-bar {
  padding: 8px 15px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  border-bottom: 1px solid var(--border-primary);
}

.table-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}

.table-tag.active {
  background-color: var(--primary-color);
  color: white;
  border-color: var(--primary-color);
}

.remove-tag {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  padding: 0;
  font-size: 10px;
}

.quick-prompts {
  padding: 8px 15px;
  display: flex;
  gap: 8px;
  border-bottom: 1px solid var(--border-primary);
}

.prompt-btn {
  padding: 4px 10px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  color: var(--text-secondary);
  border-radius: 12px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.prompt-btn:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.input-wrapper {
  padding: 10px 15px;
  display: flex;
  gap: 10px;
}

.message-input {
  flex: 1;
  min-height: 36px;
  max-height: 120px;
  padding: 8px 12px;
  background-color: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  color: var(--text-primary);
  resize: none;
  font-size: 13px;
  line-height: 1.4;
}

.send-btn {
  width: 36px;
  height: 36px;
  background-color: var(--primary-color);
  border: none;
  color: white;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.send-btn:hover:not(:disabled) {
  background-color: var(--primary-hover);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-status {
  padding: 5px 15px;
  display: flex;
  gap: 15px;
}

.status-item {
  font-size: 11px;
  color: var(--text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.modal {
  background-color: var(--bg-primary);
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  padding: 15px 20px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.modal-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}

.close-btn {
  width: 24px;
  height: 24px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}

.modal-content {
  padding: 20px;
  overflow-y: auto;
}

.role-list {
  margin-bottom: 15px;
}

.role-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 8px;
}

.role-item:hover {
  background-color: var(--bg-highlight);
}

.role-item.active {
  background-color: var(--primary-color);
  color: white;
}

.role-info {
  flex: 1;
}

.role-info .role-name {
  display: block;
  font-weight: 500;
}

.role-info .role-desc {
  display: block;
  font-size: 12px;
  opacity: 0.7;
}

.delete-role {
  width: 24px;
  height: 24px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
}

.create-role-btn {
  width: 100%;
  padding: 10px;
  background-color: var(--bg-secondary);
  border: 1px dashed var(--border-primary);
  color: var(--text-primary);
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  color: var(--text-primary);
  font-size: 13px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 13px;
}

.form-group textarea {
  min-height: 80px;
  resize: vertical;
}

.form-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.btn-secondary,
.btn-primary {
  padding: 8px 20px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  border: none;
}

.btn-secondary {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.btn-primary {
  background-color: var(--primary-color);
  color: white;
}

.btn-secondary:hover {
  background-color: var(--bg-highlight);
}

.btn-primary:hover {
  background-color: var(--primary-hover);
}

/* CSS变量 */
:root {
  --bg-primary: #1a1a1a;
  --bg-secondary: #2d2d2d;
  --bg-highlight: #3a3a3a;
  --bg-input: #2a2a2a;
  --border-primary: #404040;
  --text-primary: #ffffff;
  --text-secondary: #b0b0b0;
  --text-tertiary: #808080;
  --primary-color: #007acc;
  --primary-hover: #005c99;
}
</style>