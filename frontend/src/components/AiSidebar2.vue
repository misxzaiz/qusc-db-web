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
      customRoles: []
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
        // 准备历史记录和系统提示词
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole ? this.selectedRole.systemPrompt : null

        let url = `/api/ai/chat/stream?message=${encodeURIComponent(message)}`
        if (this.selectedConfig) url += `&configId=${this.selectedConfig}`
        if (systemPrompt) url += `&systemPrompt=${encodeURIComponent(systemPrompt)}`
        if (history.length > 0) url += `&history=${encodeURIComponent(JSON.stringify(history))}`

        console.log('发起流式请求:', url)
        console.log('历史记录条数:', history.length)
        console.log('使用角色:', this.selectedRole?.name)

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
        // 准备历史记录和系统提示词
        const history = this.getFilteredHistory()
        const systemPrompt = this.selectedRole ? this.selectedRole.systemPrompt : null

        const response = await fetch('/api/ai/chat/free', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message,
            configId: this.selectedConfig,
            systemPrompt,
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
</style>