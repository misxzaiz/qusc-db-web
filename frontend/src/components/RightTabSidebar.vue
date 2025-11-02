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
          <span class="role-avatar">
            <font-awesome-icon icon="robot" />
          </span>
          <span class="role-name">{{ selectedRole.name }}</span>
          <button class="btn-icon small" @click="selectedRole = null" title="切换">
            <font-awesome-icon icon="times" />
          </button>
        </div>

        <!-- 无角色提示 -->
        <div v-else class="no-role-hint">
          <span class="hint-icon">
            <font-awesome-icon icon="lightbulb" />
          </span>
          <span class="hint-text">请先创建一个AI角色</span>
          <button class="btn-icon small" @click="showCreateRole = true" title="创建角色">
            <font-awesome-icon icon="plus" />
          </button>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
          <button class="tool-btn" @click="showPrompts = !showPrompts" :class="{ active: showPrompts }" title="提示词">
            <font-awesome-icon icon="lightbulb" />
          </button>
        </div>

        <!-- 聊天消息 -->
        <div ref="messagesContainer" class="messages-container">
          <div v-for="(msg, index) in messages" :key="index" class="message" :class="msg.role">
            <div class="message-avatar">
              <font-awesome-icon v-if="msg.role === 'user'" icon="user" />
              <font-awesome-icon v-else :icon="currentRoleAvatar" />
            </div>
            <div class="message-content">
              <MarkdownRenderer
                :content="msg.content"
                :streaming="msg.streaming"
                :allow-sql-execution="true"
                @execute-sql="(sql) => handleExecuteSql(sql, msg)"
                @copy-sql="handleCopySql"
                @open-in-new-tab="handleOpenInNewTab"
                @execute-batch-sql="$emit('execute-batch-sql', $event)"
                @execute-sql-in-tab="$emit('execute-sql-in-tab', $event)"
                @cancel-sql-in-tab="$emit('cancel-sql-in-tab', $event)"
                @batch-sql-execute="$emit('batch-sql-execute', $event)"
                @batch-sql-cancelled="$emit('batch-sql-cancelled', $event)"
                @batch-sql-completed="$emit('batch-sql-completed', $event)"
              />
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

            <!-- @表选择器 -->
            <div
              v-if="tableSelectorVisible"
              class="table-selector"
              :style="{
                left: tableSelectorPosition.x + 'px',
                top: tableSelectorPosition.y + 'px'
              }"
            >
              <div class="table-list" ref="tableList">
                <div
                  v-for="(table, index) in filteredTables"
                  :key="table"
                  class="table-item"
                  :class="{ selected: index === selectedTableIndex }"
                  @click="selectTable(table)"
                >
                  <font-awesome-icon class="icon" icon="table" />
                  <span class="table-name">{{ table }}</span>
                </div>
                <div v-if="filteredTables.length === 0" class="empty">
                  没有找到匹配的表
                </div>
              </div>
            </div>

            <button
              class="send-btn"
              @click="streaming ? stopStreaming() : sendMessage()"
              :disabled="!inputText.trim() && !streaming"
              :class="{ 'stop-btn': streaming }"
            >
              <font-awesome-icon v-if="!loading && !streaming" icon="paper-plane" />
              <font-awesome-icon v-else-if="streaming" icon="stop" />
              <font-awesome-icon v-else icon="spinner" spin />
            </button>
          </div>
          <div class="input-status">
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
              <span class="role-avatar">
                <font-awesome-icon icon="robot" />
              </span>
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
    <div v-if="showCreateRole" class="modal-overlay" @click.self="closeCreateRole">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ roleGenerationMode ? 'AI生成角色' : '创建AI角色' }}</h3>
          <button class="close-btn" @click="closeCreateRole">
            <font-awesome-icon icon="times" />
          </button>
        </div>
        <div class="modal-content">
          <!-- AI生成模式 -->
          <div v-if="roleGenerationMode">
            <div class="form-group">
              <label>描述你想要创建的角色</label>
              <textarea
                v-model="roleGenerationPrompt"
                placeholder="例如：一个专业的SQL专家，擅长优化查询和解释复杂SQL语句"
                rows="4"
                :disabled="isGeneratingRole"
              ></textarea>
            </div>
            <div class="form-actions">
              <button class="btn-secondary" @click="switchToManualMode" :disabled="isGeneratingRole">
                手动创建
              </button>
              <button
                class="btn-primary"
                @click="generateRoleWithAI"
                :disabled="!roleGenerationPrompt.trim() || isGeneratingRole"
              >
                <font-awesome-icon v-if="isGeneratingRole" icon="spinner" spin />
                {{ isGeneratingRole ? '生成中...' : '生成角色' }}
              </button>
            </div>
          </div>

          <!-- 手动创建模式 -->
          <div v-else>
            <div class="form-group">
              <label>角色名称</label>
              <input v-model="newRole.name" placeholder="例如：SQL专家" />
            </div>
            <div class="form-group">
              <label>头像</label>
              <input v-model="newRole.avatar" placeholder="例如：robot" />
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
              <button class="btn-secondary" @click="switchToAIMode">
                AI生成
              </button>
              <button class="btn-primary" @click="createRole">创建</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import MarkdownRenderer from './MarkdownRenderer.vue'
import { aiApi } from '../services/aiApi'
import { sqlApi } from '../services/api'
import { connectionStore } from '../stores/connectionStore'
import { faMagic, faStop } from '@fortawesome/free-solid-svg-icons'
import { library } from '@fortawesome/fontawesome-svg-core'

library.add(faMagic, faStop)

export default {
  name: 'RightTabSidebar',

  components: {
    MarkdownRenderer
  },

  props: {
    getCurrentTabTables: {
      type: Function,
      default: () => []
    },
    getCurrentTabInfo: {
      type: Function,
      default: () => null
    }
  },

  emits: ['execute-sql', 'resize', 'toggle', 'execute-batch-sql', 'execute-sql-in-tab', 'cancel-sql-in-tab', 'batch-sql-execute', 'batch-sql-cancelled', 'batch-sql-completed'],

  data() {
    return {
      isCollapsed: true,
      activeTab: 'ai',
      isResizing: false, // 添加isResizing属性

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
      roleGenerationMode: true, // true为AI生成模式，false为手动创建模式
      roleGenerationPrompt: '', // AI生成角色的输入提示
      isGeneratingRole: false, // 是否正在生成角色
      referencedTables: new Map(),
      tableSelectorVisible: false,
      tableSelectorPosition: { x: 0, y: 0 },
      tableSearchQuery: '',
      availableTables: [],
      selectedTableIndex: -1,
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
      // 返回图标名称而不是emoji
      return this.selectedRole?.avatar || 'robot'
    },

    filteredTables() {
      if (!this.tableSearchQuery) {
        return this.availableTables
      }
      return this.availableTables.filter(table =>
        typeof table === 'string' && table.toLowerCase().includes(this.tableSearchQuery.toLowerCase())
      )
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

      if (!this.selectedConfig) {
        console.warn('没有选择AI配置')
        return
      }

      this.messages.push({
        role: 'user',
        content: message,
        timestamp: new Date()
      })

      if (!customMessage) {
        this.inputText = ''
      }
      this.scrollToBottom()

      // 始终使用流式输出
      await this.streamMessage(message)
      
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

        const eventSourcePromise = aiApi.streamChat(message, this.selectedConfig, systemPrompt, history, tableContexts)

        // 等待 Promise 解析以获取 eventSource 对象
        eventSourcePromise.then(eventSource => {
          this.eventSource = eventSource

          eventSource.onmessage = (event) => {
            const data = event.data
            // aiApi.js已经处理了SSE格式，这里直接处理纯JSON
            if (!data) return

            try {
              const parsed = JSON.parse(data)

              // 忽略连接确认消息
              if (parsed.status === 'connected') {
                return
              }

              // 检查是否完成
              if (parsed.done === true) {
                this.streaming = false
                this.streamContent = ''
                // 将最后一条消息标记为非流式
                const lastMessage = this.messages[this.messages.length - 1]
                if (lastMessage && lastMessage.role === 'assistant' && lastMessage.streaming) {
                  lastMessage.streaming = false
                }
                this.scrollToBottom()
              } else if (parsed.content) {
                // 累积流式内容
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
              console.error('解析流数据失败:', e)
            }
          }

          eventSource.onerror = (error) => {
            console.error('流式连接错误:', error)
            this.streaming = false
            eventSource?.close()
          }
        })

        // 错误处理
        eventSourcePromise.catch(error => {
          console.error('创建eventSource失败:', error)
          this.streaming = false
        })
      } catch (error) {
        console.error('发送消息失败:', error)
        this.streaming = false
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
      // 表选择器键盘导航
      if (this.tableSelectorVisible) {
        if (event.key === 'ArrowDown') {
          event.preventDefault()
          this.selectedTableIndex = Math.min(this.selectedTableIndex + 1, this.filteredTables.length - 1)
          this.scrollToSelectedTable()
        } else if (event.key === 'ArrowUp') {
          event.preventDefault()
          this.selectedTableIndex = Math.max(this.selectedTableIndex - 1, 0)
          this.scrollToSelectedTable()
        } else if (event.key === 'Enter') {
          event.preventDefault()
          if (this.selectedTableIndex >= 0 && this.filteredTables[this.selectedTableIndex]) {
            this.selectTable(this.filteredTables[this.selectedTableIndex])
          }
        } else if (event.key === 'Escape') {
          event.preventDefault()
          this.tableSelectorVisible = false
          this.selectedTableIndex = -1
        }
        return
      }

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

    async generateRoleWithAI() {
      if (!this.roleGenerationPrompt.trim() || this.isGeneratingRole) return

      this.isGeneratingRole = true

      try {
        // 构建生成角色的提示词
        const prompt = `你是一个专业的AI角色设计师，请根据以下描述生成一个AI角色。

用户需求：${this.roleGenerationPrompt}

请生成一个JSON格式的角色配置，包含以下字段：
{
  "name": "角色名称（简洁专业，2-5个字）",
  "avatar": "FontAwesome图标名称（小写，如robot、user、code、database、chart-bar等）",
  "description": "角色简短描述（一句话概括角色特点）",
  "systemPrompt": "系统提示词（详细描述，包含：1.专业背景 2.核心能力 3.沟通风格 4.回答特点 5.使用场景）"
}

要求：
1. 角色名称要专业且易于理解
2. 选择符合角色特点的图标
3. 系统提示词要详细具体，让AI知道如何扮演这个角色
4. 确保返回的是有效的JSON格式

请只返回JSON，不要包含其他文字。`

        // 使用AI生成
        const response = await aiApi.freeChat(prompt, this.selectedConfig)

        if (response.data && response.data.response) {
          // 尝试解析JSON
          try {
            const jsonMatch = response.data.response.match(/\{[\s\S]*\}/)
            if (jsonMatch) {
              const roleData = JSON.parse(jsonMatch[0])

              // 填充到表单
              this.newRole = {
                name: roleData.name || '',
                avatar: roleData.avatar || 'robot',
                description: roleData.description || '',
                systemPrompt: roleData.systemPrompt || ''
              }

              // 切换到手动模式供用户调整
              this.roleGenerationMode = false
            }
          } catch (parseError) {
            console.error('解析AI生成的角色数据失败', parseError)
            alert('AI生成失败，请重试或手动创建')
          }
        }
      } catch (error) {
        console.error('AI生成角色失败', error)
        alert('AI生成失败：' + (error.message || '未知错误'))
      } finally {
        this.isGeneratingRole = false
      }
    },

    switchToManualMode() {
      this.roleGenerationMode = false
    },

    switchToAIMode() {
      this.roleGenerationMode = true
      this.roleGenerationPrompt = ''
    },

    closeCreateRole() {
      this.showCreateRole = false
      this.roleGenerationMode = true
      this.roleGenerationPrompt = ''
      this.newRole = {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      }
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
      this.tableSearchQuery = searchText
      this.selectedTableIndex = -1 // 重置选中索引

      // 获取当前连接的表列表
      if (typeof this.getCurrentTabTables === 'function') {
        const tables = this.getCurrentTabTables()
        // 确保表列表是数组格式
        this.availableTables = Array.isArray(tables) ? tables : []
      } else {
        this.availableTables = []
      }

      // 计算位置
      const textarea = this.$refs.messageInput
      if (textarea) {
        const rect = textarea.getBoundingClientRect()
        const lineHeight = 20 // 估算的行高
        const lineIndex = this.inputText.substring(0, atIndex).split('\n').length - 1

        // 选择器高度
        const selectorHeight = 180
        // 计算默认位置（textarea下方）
        let defaultTop = rect.top + (lineIndex + 1) * lineHeight + 10

        // 检查是否会超出视窗底部
        const willOverflowBottom = defaultTop + selectorHeight > window.innerHeight

        if (willOverflowBottom) {
          // 显示在textarea上方
          this.tableSelectorPosition = {
            x: rect.left + 10,
            y: rect.top + (lineIndex - 1) * lineHeight - selectorHeight - 10
          }
        } else {
          // 显示在textarea下方
          this.tableSelectorPosition = {
            x: rect.left + 10,
            y: defaultTop
          }
        }

        this.tableSelectorVisible = true
      }
    },

    selectTable(tableName) {
      const textarea = this.$refs.messageInput
      if (!textarea) return

      // 查找@符号位置
      const cursorPos = textarea.selectionStart
      const text = this.inputText
      const atIndex = text.lastIndexOf('@', cursorPos - 1)

      if (atIndex !== -1) {
        // 替换@符号后的内容为表名
        const beforeAt = text.substring(0, atIndex)
        const afterCursor = text.substring(cursorPos)
        this.inputText = beforeAt + tableName + ' ' + afterCursor

        // 设置光标位置
        this.$nextTick(() => {
          const newPos = beforeAt.length + tableName.length + 1
          textarea.setSelectionRange(newPos, newPos)
          textarea.focus()
        })
      }

      // 添加到引用表列表
      if (!this.referencedTables.has(tableName)) {
        this.referencedTables.set(tableName, {
          tableName,
          active: true,
          createSql: null
        })

        // 获取表结构
        this.loadTableStructure(tableName)
      }

      this.tableSelectorVisible = false
    },

    scrollToSelectedTable() {
      this.$nextTick(() => {
        const tableList = this.$refs.tableList
        if (tableList && this.selectedTableIndex >= 0) {
          const selectedItem = tableList.children[this.selectedTableIndex]
          if (selectedItem) {
            selectedItem.scrollIntoView({ block: 'nearest' })
          }
        }
      })
    },

    // 停止流式输出
    stopStreaming() {
      if (this.eventSource) {
        this.eventSource.close()
        this.eventSource = null
      }
      this.streaming = false
      this.streamContent = ''

      // 将最后一条消息标记为非流式
      const lastMessage = this.messages[this.messages.length - 1]
      if (lastMessage && lastMessage.role === 'assistant' && lastMessage.streaming) {
        lastMessage.streaming = false
      }
    },

    // 加载表结构
    async loadTableStructure(tableName) {
      try {
        // 获取当前tab的会话信息
        const tabInfo = this.getCurrentTabInfo()
        if (!tabInfo || !tabInfo.sessionId || !tabInfo.database) {
          console.warn('会话ID或数据库为空')
          return
        }

        const response = await sqlApi.getTableCreate(tabInfo.sessionId, tabInfo.database, tableName)

        // 更新表结构信息
        const tableInfo = this.referencedTables.get(tableName)
        if (tableInfo) {
          tableInfo.createSql = response.data.createSql
        }
      } catch (error) {
        console.error('加载表结构失败:', error)
      }
    },

    // SQL执行相关方法
    async handleExecuteSql(sql, message) {
      try {
        // 获取当前Tab信息
        const tabInfo = this.getCurrentTabInfo()
        if (!tabInfo || !tabInfo.sessionId) {
          this.showToast('请先选择数据库连接', 'error')
          return
        }

        // 执行SQL
        await sqlApi.execute(tabInfo.sessionId, sql)

        // 通知父组件刷新结果
        this.$emit('execute-sql', sql)

        this.showToast('SQL执行成功', 'success')
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message || '执行失败'
        this.showToast('SQL执行失败: ' + errorMsg, 'error')
      }
    },

    handleCopySql(sql) {
      // 复制功能已在 MarkdownRenderer 中处理
      console.log('SQL copied:', sql)
    },

    handleOpenInNewTab(sql) {
      // 通知父组件在新Tab中打开SQL
      this.$emit('open-in-new-tab', sql)
    },

    formatNumber(num) {
      if (num === null || num === undefined) return '0'
      if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
      if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
      return num.toString()
    },

    formatCellValue(value) {
      if (value === null || value === undefined) return 'NULL'
      if (typeof value === 'string') {
        if (value.length > 50) return value.substring(0, 50) + '...'
      }
      return value
    },

    showToast(message, type = 'info') {
      // 创建临时提示
      const toast = document.createElement('div')
      toast.className = `sql-toast sql-toast-${type}`
      toast.textContent = message
      toast.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 10px 20px;
        background: ${type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#007bff'};
        color: white;
        border-radius: 4px;
        z-index: 10000;
        animation: slideIn 0.3s ease;
      `

      document.body.appendChild(toast)
      setTimeout(() => {
        toast.remove()
      }, 3000)
    },
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

/* 用户消息和AI消息都使用左对齐文本 */
.message-content {
  text-align: left;
}

/* 消息气泡样式 */
.message.user .message-content {
  background-color: var(--accent-primary);
  color: white;
  padding: 10px 15px;
  border-radius: 18px 18px 4px 18px;
  display: inline-block;
  margin-left: auto; /* 保持气泡在右侧 */
}

.message.assistant .message-content {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  padding: 10px 15px;
  border-radius: 18px 18px 18px 4px;
  display: inline-block;
}

.input-container {
  border-top: 1px solid var(--border-primary);
}

/* @表选择器样式 */
.table-selector {
  position: fixed;
  background: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  max-height: 180px;
  overflow-y: auto;
  z-index: 1000;
  min-width: 180px;
  font-size: 13px;
}

.table-list {
  max-height: 180px;
  overflow-y: auto;
}

.table-item {
  padding: 6px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background-color 0.15s;
}

.table-item:hover {
  background-color: var(--bg-highlight);
}

.table-item.selected {
  background-color: var(--accent-primary);
  color: white;
}

.table-item.selected .icon {
  opacity: 1;
}

.table-item .icon {
  font-size: 12px;
  opacity: 0.6;
}

.table-selector .empty {
  padding: 12px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
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

.send-btn.stop-btn {
  background-color: var(--error-color, #e74c3c);
  border-color: var(--error-color, #e74c3c);
}

.send-btn.stop-btn:hover:not(:disabled) {
  background-color: #c0392b;
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

/* AI生成模式的特殊样式 */
.form-group textarea[disabled] {
  background-color: var(--bg-tertiary);
  cursor: not-allowed;
  opacity: 0.7;
}

/* 生成按钮动画 */
.btn-primary:disabled {
  position: relative;
}

.btn-primary:disabled .fa-spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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

/* SQL结果展示样式 */
.sql-result-section {
  margin-top: 15px;
  border: 1px solid var(--border-primary);
  border-radius: 6px;
  overflow: hidden;
}

.sql-result-header {
  padding: 10px 15px;
  background-color: var(--bg-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--border-primary);
  transition: background-color 0.2s ease;
}

.sql-result-header:hover {
  background-color: var(--bg-highlight);
}

.sql-result-header span {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.sql-result-content {
  background-color: var(--bg-primary);
}

.result-success {
  padding: 15px;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 15px;
  margin-bottom: 12px;
}

.success-badge {
  background-color: rgba(40, 167, 69, 0.2);
  color: #28a745;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.execution-time,
.affected-rows {
  font-size: 12px;
  color: var(--text-secondary);
}

.result-preview {
  margin-top: 10px;
}

.result-table {
  width: 100%;
  font-size: 12px;
  border-collapse: collapse;
}

.result-table th,
.result-table td {
  padding: 6px 8px;
  text-align: left;
  border-bottom: 1px solid var(--border-primary);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-table th {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  font-weight: 500;
}

.result-table td {
  color: var(--text-secondary);
}

.more-results {
  padding: 8px;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
  background-color: var(--bg-secondary);
  border-radius: 4px;
  margin-top: 8px;
}

.result-error {
  padding: 15px;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: #dc3545;
  font-size: 13px;
  font-weight: 500;
}

.error-message {
  background-color: var(--bg-secondary);
  border: 1px solid rgba(220, 53, 69, 0.3);
  border-radius: 4px;
  padding: 10px;
  color: #dc3545;
  font-size: 12px;
  margin: 0;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
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