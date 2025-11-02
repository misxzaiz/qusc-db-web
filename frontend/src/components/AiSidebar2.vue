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

    <!-- AI配置选择器 -->
    <div v-if="isExpanded" class="ai-config-bar">
      <select v-model="selectedConfig" @change="onConfigChange" class="compact-select">
        <option v-for="config in aiConfigs" :key="config.id" :value="config.id">
          {{ config.name }}
        </option>
      </select>
    </div>
    <div v-if="isExpanded" class="ai-content">

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

        <!-- 表标签栏 -->
        <div v-if="referencedTables.size > 0" class="table-tags-bar">
          <div
            v-for="[tableName, tableInfo] in referencedTables"
            :key="tableName"
            class="table-tag"
            :class="{ active: tableInfo.active, loading: tableInfo.loading }"
            @click="toggleTableActive(tableName)"
          >
            <span class="tag-icon">
              <font-awesome-icon v-if="tableInfo.loading" icon="spinner" spin />
              <span v-else>{{ tableInfo.active ? '💡' : '🌫️' }}</span>
            </span>
            <span class="tag-name">{{ tableName }}</span>
            <button class="tag-close" @click.stop="removeTableTag(tableName)">
              <font-awesome-icon icon="times" />
            </button>
          </div>
        </div>

        <div class="input-wrapper">
          <div class="textarea-wrapper">
            <textarea
              v-model="inputText"
              :placeholder="selectedRole ? `以${selectedRole.name}身份...` : '输入问题...'"
              rows="2"
              @keydown="handleInputKeydown"
              @input="handleInputChange"
              @click="handleInputClick"
              :disabled="loading || streaming"
              ref="messageInput"
            ></textarea>

            <!-- @表选择器 -->
            <div
              v-if="showTableSelector"
              class="table-selector"
              :style="{
                left: tableSelectorPosition.x + 'px',
                top: tableSelectorPosition.y + 'px'
              }"
            >
              <div class="table-list" ref="tableList">
                <div
                  v-for="table in filteredTables"
                  :key="table"
                  class="table-item"
                  @click="selectTable(table)"
                >
                  <span class="icon">📊</span>
                  <span class="table-name">{{ table }}</span>
                </div>
                <div v-if="filteredTables.length === 0" class="empty">
                  没有找到匹配的表
                </div>
              </div>
            </div>
          </div>

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

          <!-- AI生成模式 -->
          <div class="create-mode-toggle">
            <button
              class="mode-btn"
              :class="{ active: !aiGenerateMode }"
              @click="aiGenerateMode = false"
            >
              手动输入
            </button>
            <button
              class="mode-btn"
              :class="{ active: aiGenerateMode }"
              @click="aiGenerateMode = true"
            >
              AI生成
            </button>
          </div>

          <!-- 手动输入模式 -->
          <div v-if="!aiGenerateMode">
            <input v-model="newRole.description" placeholder="角色描述" class="dialog-input" />
            <textarea v-model="newRole.systemPrompt" placeholder="系统提示词" rows="3" class="dialog-input"></textarea>
          </div>

          <!-- AI生成模式 -->
          <div v-else>
            <!-- 标签选择 -->
            <div class="tag-selector">
              <label class="tag-label">选择角色类型（可选）：</label>
              <div class="tag-list">
                <span
                  v-for="tag in roleTags"
                  :key="tag"
                  class="tag"
                  :class="{ active: selectedTags.includes(tag) }"
                  @click="toggleTag(tag)"
                >
                  {{ tag }}
                </span>
              </div>
            </div>

            <textarea
              v-model="roleDescription"
              placeholder="用一句话描述你想要的角色，例如：一个温柔体贴的小姐姐"
              rows="2"
              class="dialog-input"
            ></textarea>
            <button
              class="btn btn-secondary generate-btn"
              @click="generateRoleWithAI"
              :disabled="!roleDescription.trim() || generatingRole"
            >
              <font-awesome-icon v-if="!generatingRole" icon="magic" />
              <font-awesome-icon v-else icon="spinner" spin />
              {{ generatingRole ? '生成中...' : 'AI生成' }}
            </button>

            <!-- 生成结果 -->
            <div v-if="generatedRole" class="generated-result">
              <div class="result-item">
                <label>角色名称：</label>
                <input v-model="generatedRole.name" class="dialog-input" />
              </div>
              <div class="result-item">
                <label>角色描述：</label>
                <input v-model="generatedRole.description" class="dialog-input" />
              </div>
              <div class="result-item">
                <label>系统提示词：</label>
                <textarea v-model="generatedRole.systemPrompt" rows="3" class="dialog-input"></textarea>
              </div>
              <div class="result-actions">
                <button class="btn btn-small" @click="regenerateRole">重新生成</button>
                <button class="btn btn-small btn-primary" @click="useGeneratedRole">使用此角色</button>
              </div>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn" @click="showCreateRole = false">取消</button>
          <button v-if="!aiGenerateMode" class="btn btn-primary" @click="createRole">创建</button>
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
  name: 'AiSidebar',
  components: {
    MarkdownRenderer
  },
  emits: ['execute-sql', 'toggle'],
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
      eventSource: null,
      // AI生成相关
      aiGenerateMode: false,
      roleDescription: '',
      generatingRole: false,
      generatedRole: null,
      selectedTags: [],
      roleTags: [
        '温柔', '严肃', '活泼', '成熟', '幽默', '冷静',
        '专业', '亲切', '高冷', '可爱', '稳重', '热情',
        '理性', '感性', '细心', '大方', '害羞', '自信'
      ],
      // 角色管理
      defaultRoles: [],
      customRoles: [],
      // @引用相关
      showTableSelector: false,
      tableSelectorPosition: { x: 0, y: 0 },
      tableSearchQuery: '',
      filteredTables: [],
      referencedTables: new Map(), // 表名 -> { active: boolean, visible: boolean, createSql: string, loading: boolean }
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
      // 不再有默认角色
      this.defaultRoles = []

      // 加载自定义角色
      this.loadCustomRoles()

      // 角色列表只包含自定义角色
      this.roles = [...this.customRoles]

      // 恢复选中的角色
      const savedRoleId = localStorage.getItem('selected_ai_role')
      if (savedRoleId) {
        this.selectedRole = this.roles.find(r => r.id === savedRoleId) || null
      }

      // 如果没有角色，显示创建引导
      if (this.roles.length === 0) {
        this.showCreateRole = true
        this.aiGenerateMode = true
      }
    },

    loadHistory() {
      const saved = localStorage.getItem('ai_chat_history')
      if (saved) {
        try {
          const historyByRole = JSON.parse(saved)
          const roleId = this.selectedRole?.id || 'default'
          this.messages = historyByRole[roleId] || []
          // 限制历史长度
          if (this.messages.length > 30) {
            this.messages = this.messages.slice(-30)
          }
        } catch (e) {
          console.error('加载历史失败', e)
          this.messages = []
        }
      }
    },

    saveHistory() {
      const saved = localStorage.getItem('ai_chat_history')
      const historyByRole = saved ? JSON.parse(saved) : {}
      const roleId = this.selectedRole?.id || 'default'
      historyByRole[roleId] = this.messages
      localStorage.setItem('ai_chat_history', JSON.stringify(historyByRole))
    },

    // 加载自定义角色
    loadCustomRoles() {
      const saved = localStorage.getItem('ai_custom_roles')
      if (saved) {
        try {
          this.customRoles = JSON.parse(saved)
        } catch (e) {
          console.error('加载自定义角色失败', e)
          this.customRoles = []
        }
      }
    },

    // 保存自定义角色
    saveCustomRoles() {
      localStorage.setItem('ai_custom_roles', JSON.stringify(this.customRoles))
    },

    onConfigChange() {
      localStorage.setItem('selected_ai_config', this.selectedConfig)
    },

    selectRole(role) {
      // 保存当前角色的历史
      this.saveHistory()

      // 切换角色
      this.selectedRole = role
      localStorage.setItem('selected_ai_role', role.id)

      // 加载新角色的历史
      this.messages = []
      this.loadHistory()
    },

    async createRole() {
      if (!this.newRole.name || !this.newRole.systemPrompt) {
        alert('请填写名称和提示词')
        return
      }

      // 创建自定义角色
      const customRole = {
        ...this.newRole,
        id: 'custom-' + Date.now(),
        isCustom: true
      }

      this.customRoles.push(customRole)
      this.saveCustomRoles()

      // 合并默认角色和自定义角色
      this.roles = [...this.defaultRoles, ...this.customRoles]

      this.showCreateRole = false
      this.newRole = { name: '', avatar: '', description: '', systemPrompt: '' }
    },

    async deleteRole(role) {
      if (!confirm(`删除角色 ${role.name}?`)) return
      if (!role.isCustom) {
        alert('不能删除系统默认角色')
        return
      }

      // 从自定义角色中删除
      this.customRoles = this.customRoles.filter(r => r.id !== role.id)
      this.saveCustomRoles()

      // 更新角色列表
      this.roles = [...this.defaultRoles, ...this.customRoles]

      // 如果删除的是当前角色，清空选择
      if (this.selectedRole?.id === role.id) {
        this.selectedRole = null
        localStorage.removeItem('selected_ai_role')
        this.messages = []
      }
    },

    // AI生成角色
    async generateRoleWithAI() {
      if (!this.roleDescription.trim() || !this.selectedConfig) {
        alert('请输入角色描述并选择AI配置')
        return
      }

      this.generatingRole = true
      this.generatedRole = null

      try {
        let tagText = ''
      if (this.selectedTags.length > 0) {
        tagText = `\n角色特征：${this.selectedTags.join('、')}`
      }

      const prompt = `请根据以下描述生成一个AI角色，返回JSON格式：
描述：${this.roleDescription}${tagText}

请生成包含以下字段的JSON：
{
  "name": "角色名称",
  "description": "角色描述",
  "systemPrompt": "详细的系统提示词",
  "avatar": "合适的emoji表情（2个字符以内）"
}

要求：
1. 角色名称要简洁明了
2. 描述要准确概括角色特点
3. 系统提示词要详细，充分体现角色的性格特征和沟通风格
4. emoji要符合角色特征

只返回JSON，不要其他内容。`

        const response = await fetch('/api/ai/chat/free', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: prompt,
            configId: this.selectedConfig
          })
        })

        if (response.ok) {
          const result = await response.json()
          try {
            // 处理AI返回的内容，去除可能的代码块标记
            let jsonStr = result.response.trim()

            // 去除```json和```标记
            if (jsonStr.startsWith('```json')) {
              jsonStr = jsonStr.substring(7)
            }
            if (jsonStr.endsWith('```')) {
              jsonStr = jsonStr.substring(0, jsonStr.length - 3)
            }

            // 去除可能的换行符
            jsonStr = jsonStr.trim()

            const generated = JSON.parse(jsonStr)
            this.generatedRole = {
              name: generated.name || '',
              description: generated.description || '',
              systemPrompt: generated.systemPrompt || '',
              avatar: generated.avatar || '🤖'
            }
          } catch (e) {
            console.error('解析AI生成结果失败', e)
            console.error('原始响应:', result.response)
            alert('AI生成结果格式错误，请重试')
          }
        }
      } catch (error) {
        console.error('生成角色失败', error)
        alert('生成角色失败，请检查网络连接')
      } finally {
        this.generatingRole = false
      }
    },

    // 重新生成角色
    regenerateRole() {
      this.generateRoleWithAI()
    },

    // 使用生成的角色
    useGeneratedRole() {
      if (!this.generatedRole) return

      this.newRole = { ...this.generatedRole }
      this.generatedRole = null
      this.roleDescription = ''
      this.selectedTags = []
      this.aiGenerateMode = false
    },

    // 切换标签
    toggleTag(tag) {
      const index = this.selectedTags.indexOf(tag)
      if (index > -1) {
        this.selectedTags.splice(index, 1)
      } else {
        this.selectedTags.push(tag)
      }
    },

    // 处理输入键盘事件
    handleInputKeydown(e) {
      // Shift+Enter 换行，不发送
      if (e.key === 'Enter' && e.shiftKey) {
        return
      }

      // Enter 发送消息
      if (e.key === 'Enter' && !this.showTableSelector) {
        e.preventDefault()
        this.sendMessage()
        return
      }

      // Escape 关闭表选择器
      if (e.key === 'Escape') {
        this.hideTableSelector()
        return
      }

      // 表选择器导航
      if (this.showTableSelector) {
        if (e.key === 'Enter') {
          e.preventDefault()
          // 选择第一个匹配的表
          if (this.filteredTables.length > 0) {
            this.selectTable(this.filteredTables[0])
          }
        } else if (e.key === 'ArrowDown') {
          e.preventDefault()
          // 可以在这里添加上下导航逻辑
        } else if (e.key === 'ArrowUp') {
          e.preventDefault()
          // 可以在这里添加上下导航逻辑
        }
      }
    },

    // 处理输入变化
    async handleInputChange(e) {
      const text = e.target.value
      const cursorPos = e.target.selectionStart

      // 查找@符号
      const atIndex = text.lastIndexOf('@', cursorPos)
      if (atIndex === -1 || this.loading || this.streaming) {
        this.hideTableSelector()
        return
      }

      // 提取@后的文本作为搜索查询
      const searchQuery = text.substring(atIndex + 1, cursorPos)
      if (searchQuery.includes(' ')) {
        this.hideTableSelector()
        return
      }

      // 显示表选择器并过滤
      await this.showTableSelectorAt(atIndex, searchQuery)
    },

    // 处理输入点击
    handleInputClick(e) {
      const text = e.target.value
      const cursorPos = e.target.selectionStart

      const atIndex = text.lastIndexOf('@', cursorPos)
      if (atIndex === -1) {
        this.hideTableSelector()
        return
      }

      this.showTableSelectorAt(atIndex)
    },

    // 在指定位置显示表选择器
    async showTableSelectorAt(atIndex, searchQuery = '') {
      const textarea = this.$refs.messageInput
      const rect = textarea.getBoundingClientRect()
      const lineHeight = 20 // 估算行高

      // 计算光标位置
      const lines = textarea.value.substring(0, atIndex).split('\n')
      const line = lines.length - 1
      const charInLine = lines[lines.length - 1].length

      this.tableSelectorPosition = {
        x: rect.left + charInLine * 8 + 10, // 估算字符宽度
        y: rect.top + (line + 1) * lineHeight + 5
      }

      this.showTableSelector = true

      // 加载表列表
      await this.loadTables()

      // 过滤表列表
      if (searchQuery) {
        this.filteredTables = this.filteredTables.filter(table =>
          table.toLowerCase().includes(searchQuery.toLowerCase())
        )
      }
    },

    // 隐藏表选择器
    hideTableSelector() {
      this.showTableSelector = false
      this.tableSearchQuery = ''
      this.filteredTables = []
    },

    // 加载表列表
    async loadTables() {
      // 获取当前活动的tab
      const sqlEditor = this.$parent
      if (!sqlEditor || !sqlEditor.currentTab || !sqlEditor.currentTab.sessionId) {
        return
      }

      const { sessionId, database } = sqlEditor.currentTab
      if (!sessionId || !database) {
        return
      }

      try {
        const response = await fetch(`/api/sql/tables/${sessionId}?database=${database}`)
        const data = await response.json()
        this.filteredTables = data.tables || []
      } catch (error) {
        console.error('加载表列表失败', error)
      }
    },

    // 选择表
    async selectTable(table) {
      // 创建表标签（默认激活）
      this.createTableTag(table, true)

      // 替换@引用为表名
      const text = this.inputText
      const cursorPos = this.$refs.messageInput.selectionStart

      // 找到@符号的位置
      const atPos = text.lastIndexOf('@', cursorPos)
      if (atPos !== -1) {
        const before = text.substring(0, atPos)
        const after = text.substring(cursorPos)
        this.inputText = before + `\`${table}\`` + after

        // 设置光标位置
        this.$nextTick(() => {
          const newPos = before.length + `\`${table}\``.length
          this.$refs.messageInput.selectionStart = newPos
          this.$refs.messageInput.selectionEnd = newPos
        })
      }

      // 隐藏选择器
      this.hideTableSelector()
    },

    // 加载表结构
    async loadTableSchema(tableName) {
      // 获取当前活动的tab
      const sqlEditor = this.$parent
      if (!sqlEditor || !sqlEditor.currentTab || !sqlEditor.currentTab.sessionId) {
        return
      }

      const { sessionId, database } = sqlEditor.currentTab
      if (!sessionId || !database) {
        return
      }

      try {
        const response = await fetch(`/api/sql/table/${sessionId}/${database}/${tableName}/create`)
        const data = await response.json()

        if (this.referencedTables.has(tableName)) {
          const tableInfo = this.referencedTables.get(tableName)
          tableInfo.createSql = data.createSql
          tableInfo.loading = false
        }
      } catch (error) {
        console.error(`加载表${tableName}结构失败`, error)
        if (this.referencedTables.has(tableName)) {
          const tableInfo = this.referencedTables.get(tableName)
          tableInfo.loading = false
        }
      }
    },

    // 创建表标签
    createTableTag(tableName, active = true) {
      if (!this.referencedTables.has(tableName)) {
        this.referencedTables.set(tableName, {
          active: active,
          visible: true,
          createSql: '',
          loading: false
        })
        // 加载表结构
        this.loadTableSchema(tableName)
      }
    },

    // 切换表标签激活状态
    toggleTableActive(tableName) {
      if (this.referencedTables.has(tableName)) {
        const tableInfo = this.referencedTables.get(tableName)
        tableInfo.active = !tableInfo.active
      }
    },

    // 移除表标签
    removeTableTag(tableName) {
      this.referencedTables.delete(tableName)
    },

    // 提取消息中的@表名
    extractAtTables(text) {
      const regex = /@(\w+)/g
      const tables = []
      let match
      while ((match = regex.exec(text)) !== null) {
        tables.push(match[1])
      }
      return tables
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
        // 准备历史记录和系统提示词
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole ? this.selectedRole.systemPrompt : null

        // 收集所有激活状态的表结构
        const tableContexts = []
        this.referencedTables.forEach((tableInfo, tableName) => {
          if (tableInfo.active && tableInfo.createSql) {
            tableContexts.push({
              table: tableName,
              createSql: tableInfo.createSql
            })
          }
        })

        // 准备请求数据
        const requestData = {
          message,
          configId: this.selectedConfig,
          systemPrompt,
          history,
          tableContexts
        }

        console.log('发起流式请求:', requestData)
        console.log('历史记录条数:', history.length)
        console.log('使用角色:', this.selectedRole?.name)
        console.log('表上下文:', tableContexts)

        if (this.eventSource) this.eventSource.close()

        // 使用fetch处理流式响应
        this.streaming = true
        fetch('/api/ai/chat/stream', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'text/event-stream',
            'Cache-Control': 'no-cache'
          },
          body: JSON.stringify(requestData)
        }).then(response => {
          if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`)
          }

          const reader = response.body.getReader()
          const decoder = new TextDecoder()
          let buffer = ''

          function processText(text) {
            buffer += text
            const lines = buffer.split('\n')
            buffer = lines.pop() || '' // 保留最后一个不完整的行

            for (const line of lines) {
              if (line.startsWith('data: ')) {
                const data = line.slice(6)
                if (data === '[DONE]') {
                  this.finishStream()
                  return
                }
                try {
                  const parsed = JSON.parse(data)
                  if (parsed.content) {
                    this.streamContent += parsed.content
                    this.scrollToBottom()
                  }
                } catch (e) {
                  console.error('解析数据失败', e, data)
                }
              }
            }
          }

          function read() {
            reader.read().then(({ done, value }) => {
              if (done) {
                if (buffer) {
                  processText.call(this, buffer + '\n')
                }
                this.finishStream()
                return
              }

              const text = decoder.decode(value, { stream: true })
              processText.call(this, text)
              read.call(this)
            }).catch(error => {
              console.error('流读取错误', error)
              this.addError('连接中断')
              this.streaming = false
            })
          }

          read.call(this)
        }).catch(error => {
          console.error('流式请求失败', error)
          this.addError('请求失败')
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
        // 准备历史记录和系统提示词
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole ? this.selectedRole.systemPrompt : null

        // 收集所有激活状态的表结构
        const tableContexts = []
        this.referencedTables.forEach((tableInfo, tableName) => {
          if (tableInfo.active && tableInfo.createSql) {
            tableContexts.push({
              table: tableName,
              createSql: tableInfo.createSql
            })
          }
        })

        const response = await fetch('/api/ai/chat/free', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message,
            configId: this.selectedConfig,
            systemPrompt,
            history,
            tableContexts
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

    // 获取过滤后的历史记录
    getFilteredHistory() {
      // 获取最近的有效对话（不包括错误消息）
      const validMessages = this.messages.filter(msg =>
        msg.role === 'user' ||
        (msg.role === 'assistant' && !msg.content.includes('❌ 错误：'))
      )

      // 转换格式并限制数量
      return validMessages.slice(-15).map(msg => ({
        role: msg.role === 'user' ? 'user' : 'assistant',
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

/* AI配置栏 */
.ai-config-bar {
  padding: 4px 10px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  height: 32px;
}

.compact-select {
  width: 100%;
  padding: 2px 6px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 3px;
  color: var(--text-primary);
  font-size: 11px;
  height: 24px;
}

.compact-select:focus {
  outline: none;
  border-color: var(--accent-primary);
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
  width: 100%;
}

.textarea-wrapper {
  flex: 1;
  position: relative;
}

.input-wrapper textarea {
  width: 100%;
  padding: 6px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 13px;
  resize: none;
  font-family: inherit;
  box-sizing: border-box;
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

/* AI生成模式样式 */
.create-mode-toggle {
  display: flex;
  margin-bottom: 10px;
  background-color: var(--bg-secondary);
  border-radius: var(--radius-sm);
  padding: 2px;
}

.mode-btn {
  flex: 1;
  padding: 6px;
  background: none;
  border: none;
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 12px;
  transition: var(--transition-fast);
}

.mode-btn.active {
  background-color: var(--accent-primary);
  color: white;
}

.generate-btn {
  width: 100%;
  margin-top: 8px;
  margin-bottom: 10px;
}

.generated-result {
  margin-top: 15px;
  padding: 10px;
  background-color: var(--bg-secondary);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-primary);
}

.result-item {
  margin-bottom: 10px;
}

.result-item label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.result-item .dialog-input {
  margin-bottom: 0;
}

.result-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn-small {
  padding: 4px 8px;
  font-size: 11px;
}

.btn-secondary {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  border-color: var(--border-primary);
}

.btn-secondary:hover {
  background-color: var(--bg-highlight);
}

/* 标签选择器样式 */
.tag-selector {
  margin-bottom: 10px;
}

.tag-label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.tag {
  padding: 4px 10px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 11px;
  transition: var(--transition-fast);
  user-select: none;
}

.tag:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.tag.active {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

/* 无角色提示样式 */
.no-role-hint {
  padding: 8px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: var(--warning-bg);
  border-bottom: 1px solid var(--border-primary);
}

.hint-icon {
  font-size: 16px;
}

.hint-text {
  flex: 1;
  font-size: 12px;
  color: var(--warning);
}

/* 表标签栏样式 */
.table-tags-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  padding: 0 4px;
}

.table-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  user-select: none;
}

.table-tag:hover {
  background: var(--bg-highlight);
}

.table-tag.active {
  background: #e3f2fd;
  border-color: #1976d2;
  color: #1976d2;
}

.table-tag.loading {
  opacity: 0.7;
}

.tag-icon {
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
}

.tag-name {
  font-weight: 500;
}

.tag-close {
  background: none;
  border: none;
  color: inherit;
  cursor: pointer;
  padding: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.tag-close:hover {
  opacity: 1;
}

/* 引用选择器样式 */
.table-selector {
  position: fixed;
  background: white;
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  max-height: 180px;
  overflow-y: auto;
  z-index: 1000;
  min-width: 180px;
  font-size: 13px;
}

.table-selector .table-list {
  max-height: 180px;
  overflow-y: auto;
}

.table-selector .table-item {
  padding: 6px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: background-color 0.15s;
}

.table-selector .table-item:hover {
  background: var(--bg-highlight);
}

.table-selector .table-item .icon {
  font-size: 12px;
  opacity: 0.6;
}

.table-selector .empty {
  padding: 12px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 12px;
}
</style>