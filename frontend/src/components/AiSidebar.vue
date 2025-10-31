<template>
  <div
    class="ai-sidebar"
    :class="{ collapsed: isCollapsed }"
    :style="{ width: sidebarWidth + 'px' }"
  >
    <!-- 侧边栏头部 -->
    <div class="sidebar-header" @click="toggleCollapse">
      <div class="header-left">
        <font-awesome-icon icon="robot" class="ai-icon" />
      </div>
      <div class="header-actions">
        <select v-model="selectedConfig" class="config-select" v-if="!isCollapsed" @click.stop>
          <option v-for="config in configs" :key="config.id" :value="config">
            {{ config.name }}
          </option>
        </select>
        <font-awesome-icon
          :icon="isCollapsed ? 'angle-left' : 'angle-right'"
          class="toggle-icon"
        />
      </div>
    </div>

    <!-- 侧边栏内容 -->
    <div v-if="!isCollapsed" class="sidebar-content">
      <!-- AI输入区 -->
      <div class="ai-input-section">
        <textarea
          v-model="aiInput"
          placeholder="输入您的需求..."
          class="ai-input"
          @keydown.ctrl.enter="generateSql"
        ></textarea>
        <button
          class="btn btn-primary btn-generate"
          @click="generateSql"
          :disabled="!aiInput.trim() || !selectedConfig || generating"
        >
          <font-awesome-icon icon="paper-plane" />
          生成
        </button>
      </div>

      <!-- 生成结果 -->
      <div v-if="generating" class="ai-loading">
        <font-awesome-icon icon="spinner" spin />
        <span>正在生成...</span>
      </div>

      <div v-if="generatedSql" class="ai-result">
        <div class="result-header">
          <h4>生成的SQL</h4>
          <div class="result-actions">
            <button class="btn btn-small" @click="copySql" title="复制">
              <font-awesome-icon icon="copy" />
            </button>
            <button class="btn btn-small" @click="useSql" title="使用">
              <font-awesome-icon icon="play" />
            </button>
          </div>
        </div>
        <pre class="sql-content">{{ generatedSql }}</pre>
      </div>

      <!-- 聊天历史 -->
      <div v-if="chatHistory.length > 0" class="chat-history">
        <div class="history-header">
          <h4>对话历史</h4>
          <button class="btn btn-small" @click="clearHistory" title="清空">
            <font-awesome-icon icon="trash" />
          </button>
        </div>
        <div class="chat-list">
          <div
            v-for="(chat, index) in chatHistory"
            :key="index"
            class="chat-item"
          >
            <div class="question">{{ chat.question }}</div>
            <div class="answer">{{ chat.answer }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 折叠状态提示 -->
    <div v-if="isCollapsed" class="collapsed-hint">
      <font-awesome-icon icon="robot" />
    </div>
  </div>
</template>

<script>
import { ref, watch } from 'vue'
import { aiApi } from '../services/aiApi'

export default {
  name: 'AiSidebar',

  emits: ['use-sql', 'resize'],

  setup(props, { emit }) {
    const isCollapsed = ref(true)
    const sidebarWidth = ref(40)
    const configs = ref([])
    const selectedConfig = ref(null)
    const aiInput = ref('')
    const generatedSql = ref('')
    const generating = ref(false)
    const chatHistory = ref([])

    // 加载AI配置
    const loadConfigs = async () => {
      try {
        const response = await aiApi.getConfigs()
        configs.value = response.data
        if (configs.value.length > 0) {
          selectedConfig.value = configs.value.find(c => c.enabled) || configs.value[0]
        }
      } catch (error) {
        console.error('加载AI配置失败:', error)
      }
    }

    // 切换折叠状态
    const toggleCollapse = () => {
      isCollapsed.value = !isCollapsed.value
      sidebarWidth.value = isCollapsed.value ? 40 : 350
      emit('resize', sidebarWidth.value)
    }

    // 生成SQL
    const generateSql = async () => {
      if (!aiInput.value.trim() || !selectedConfig.value) return

      generating.value = true
      try {
        const response = await aiApi.generateSql(aiInput.value, selectedConfig.value.id)
        generatedSql.value = response.data.sql

        // 添加到聊天历史
        chatHistory.value.unshift({
          question: aiInput.value,
          answer: generatedSql.value
        })

        // 限制历史记录数量
        if (chatHistory.value.length > 20) {
          chatHistory.value = chatHistory.value.slice(0, 20)
        }
      } catch (error) {
        console.error('生成SQL失败:', error)
      } finally {
        generating.value = false
      }
    }

    // 复制SQL
    const copySql = () => {
      navigator.clipboard.writeText(generatedSql.value)
    }

    // 使用SQL
    const useSql = () => {
      emit('use-sql', generatedSql.value)
    }

    // 清空历史
    const clearHistory = () => {
      chatHistory.value = []
    }

    // 监听配置变化
    watch(selectedConfig, (newConfig) => {
      if (newConfig) {
        localStorage.setItem('selected_ai_config', newConfig.id)
      }
    })

    // 初始化
    loadConfigs()

    // 发出初始宽度
    emit('resize', sidebarWidth.value)

    return {
      isCollapsed,
      sidebarWidth,
      configs,
      selectedConfig,
      aiInput,
      generatedSql,
      generating,
      chatHistory,
      toggleCollapse,
      generateSql,
      copySql,
      useSql,
      clearHistory
    }
  }
}
</script>

<style scoped>
.ai-sidebar {
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-primary);
  display: flex;
  flex-direction: column;
  height: 100vh;
  transition: width 0.3s ease;
  flex-shrink: 0;
  overflow: hidden;
}

.sidebar-header {
  padding: 10px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: var(--bg-tertiary);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-left h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}

.ai-icon {
  color: var(--accent-primary);
  width: 16px;
  height: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-select {
  padding: 4px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.sidebar-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 10px;
  gap: 10px;
  overflow: auto;
}

.ai-input-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-input {
  min-height: 80px;
  padding: 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  resize: none;
  font-family: var(--font-family-mono);
  font-size: 12px;
}

.btn-generate {
  align-self: flex-end;
}

.ai-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  color: var(--text-secondary);
  font-size: 13px;
}

.ai-result {
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.result-header {
  padding: 8px 10px;
  background-color: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
}

.result-actions {
  display: flex;
  gap: 4px;
}

.sql-content {
  padding: 10px;
  background-color: var(--bg-primary);
  font-family: var(--font-family-mono);
  font-size: 12px;
  color: var(--text-primary);
  white-space: pre-wrap;
  margin: 0;
  max-height: 200px;
  overflow: auto;
}

.chat-history {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.history-header {
  padding: 8px 10px;
  background-color: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.history-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
}

.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 5px;
}

.chat-item {
  margin-bottom: 10px;
  padding: 8px;
  background-color: var(--bg-tertiary);
  border-radius: var(--radius-sm);
}

.question {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.answer {
  font-size: 12px;
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  white-space: pre-wrap;
}

.collapsed-hint {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
}

.collapsed-hint svg {
  width: 20px;
  height: 20px;
}

.btn {
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 12px;
  transition: var(--transition-fast);
  display: inline-flex;
  align-items: center;
  gap: 4px;
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

.btn-small {
  padding: 4px 6px;
  font-size: 11px;
}

.btn-icon {
  padding: 6px;
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: var(--transition-fast);
}

.btn-icon:hover {
  background-color: var(--bg-hover);
  color: var(--text-primary);
}

.btn-icon svg {
  width: 12px;
  height: 12px;
}

.toggle-icon {
  color: var(--text-tertiary);
  cursor: pointer;
  padding: 4px;
  transition: var(--transition-fast);
}

.toggle-icon:hover {
  color: var(--text-primary);
}

.sidebar-header {
  cursor: pointer;
}
</style>