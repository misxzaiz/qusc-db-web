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
          @keydown.ctrl.enter="generateContent"
        ></textarea>
        <button
          class="btn btn-primary btn-generate"
          @click="generateContent"
          :disabled="!canGenerate() || !selectedConfig || generating"
        >
          <font-awesome-icon icon="paper-plane" />
          生成
        </button>
      </div>

      <!-- 功能选择 -->
      <div class="ai-functions">
        <button
          v-for="func in aiFunctions"
          :key="func.key"
          :class="['btn', 'function-btn', { active: selectedFunction === func.key }]"
          @click="selectedFunction = func.key"
          :title="func.description"
        >
          <font-awesome-icon :icon="func.icon" />
          {{ func.name }}
        </button>
      </div>

      <!-- 特殊输入 -->
      <div v-if="selectedFunction === 'crud'" class="crud-inputs">
        <input
          v-model="crudInputs.tableName"
          placeholder="表名"
          class="input-field"
        />
        <textarea
          v-model="crudInputs.columns"
          placeholder="表结构（如：id INT PRIMARY KEY, name VARCHAR(100), age INT）"
          class="input-field"
          rows="3"
        ></textarea>
      </div>

      <div v-if="selectedFunction === 'testData'" class="test-inputs">
        <input
          v-model="testInputs.tableName"
          placeholder="表名"
          class="input-field"
        />
        <textarea
          v-model="testInputs.columns"
          placeholder="表结构"
          class="input-field"
          rows="3"
        ></textarea>
        <input
          v-model.number="testInputs.rowCount"
          type="number"
          placeholder="行数（默认10）"
          class="input-field"
          min="1"
          max="1000"
        />
      </div>

      <div v-if="selectedFunction === 'explainPlan'" class="plan-inputs">
        <textarea
          v-model="planInputs.sql"
          placeholder="SQL语句"
          class="input-field"
          rows="3"
        ></textarea>
        <textarea
          v-model="planInputs.explainResult"
          placeholder="EXPLAIN结果"
          class="input-field"
          rows="5"
        ></textarea>
      </div>

      <!-- 生成结果 -->
      <div v-if="generating" class="ai-loading">
        <font-awesome-icon icon="spinner" spin />
        <span>正在生成...</span>
      </div>

      <div v-if="generatedResult" class="ai-result">
        <div class="result-header">
          <h4>{{ getResultTitle() }}</h4>
          <div class="result-actions">
            <button class="btn btn-small" @click="copyResult" title="复制">
              <font-awesome-icon icon="copy" />
            </button>
            <button
              v-if="selectedFunction === 'sql'"
              class="btn btn-small"
              @click="useSql"
              title="使用"
            >
              <font-awesome-icon icon="play" />
            </button>
          </div>
        </div>
        <pre class="sql-content">{{ generatedResult }}</pre>
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
    const generatedResult = ref('')
    const generating = ref(false)
    const chatHistory = ref([])
    const selectedFunction = ref('sql')

    // AI功能列表
    const aiFunctions = ref([
      { key: 'sql', name: '生成SQL', icon: 'code', description: '根据描述生成SQL' },
      { key: 'explain', name: '解释SQL', icon: 'question-circle', description: '解释SQL语句' },
      { key: 'optimize', name: '优化SQL', icon: 'rocket', description: '优化SQL性能' },
      { key: 'crud', name: '生成CRUD', icon: 'tasks', description: '生成增删改查语句' },
      { key: 'testData', name: '测试数据', icon: 'database', description: '生成测试数据' },
      { key: 'explainPlan', name: '执行计划', icon: 'sitemap', description: '分析执行计划' }
    ])

    // CRUD输入
    const crudInputs = ref({
      tableName: '',
      columns: ''
    })

    // 测试数据输入
    const testInputs = ref({
      tableName: '',
      columns: '',
      rowCount: 10
    })

    // 执行计划输入
    const planInputs = ref({
      sql: '',
      explainResult: ''
    })

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

    // 生成内容
    const generateContent = async () => {
      if (!selectedConfig.value) return

      generating.value = true
      try {
        let response

        switch (selectedFunction.value) {
          case 'sql':
            if (!aiInput.value.trim()) return
            response = await aiApi.generateSql(aiInput.value, selectedConfig.value.id)
            generatedResult.value = response.data.sql
            // 添加到聊天历史
            chatHistory.value.unshift({
              question: aiInput.value,
              answer: generatedResult.value
            })
            break

          case 'explain':
            if (!aiInput.value.trim()) return
            response = await aiApi.explainSql(aiInput.value, selectedConfig.value.id)
            generatedResult.value = response.data.explanation
            break

          case 'optimize':
            if (!aiInput.value.trim()) return
            response = await aiApi.optimizeSql(aiInput.value, selectedConfig.value.id)
            generatedResult.value = response.data.optimized
            break

          case 'crud':
            if (!crudInputs.value.tableName || !crudInputs.value.columns) return
            response = await aiApi.generateCrud(
              crudInputs.value.tableName,
              crudInputs.value.columns,
              selectedConfig.value.id
            )
            generatedResult.value = response.data.crud
            break

          case 'testData':
            if (!testInputs.value.tableName || !testInputs.value.columns) return
            response = await aiApi.generateTestData(
              testInputs.value.tableName,
              testInputs.value.columns,
              testInputs.value.rowCount || 10,
              selectedConfig.value.id
            )
            generatedResult.value = response.data.testData
            break

          case 'explainPlan':
            if (!planInputs.value.sql || !planInputs.value.explainResult) return
            response = await aiApi.explainQueryPlan(
              planInputs.value.sql,
              planInputs.value.explainResult,
              selectedConfig.value.id
            )
            generatedResult.value = response.data.explanation
            break
        }

        // 限制历史记录数量
        if (chatHistory.value.length > 20) {
          chatHistory.value = chatHistory.value.slice(0, 20)
        }
      } catch (error) {
        console.error('生成失败:', error)
        alert('生成失败: ' + (error.response?.data?.error || error.message))
      } finally {
        generating.value = false
      }
    }

    // 复制结果
    const copyResult = () => {
      navigator.clipboard.writeText(generatedResult.value)
    }

    // 使用SQL
    const useSql = () => {
      emit('use-sql', generatedResult.value)
    }

    // 获取结果标题
    const getResultTitle = () => {
      const titles = {
        sql: '生成的SQL',
        explain: 'SQL解释',
        optimize: '优化后的SQL',
        crud: 'CRUD语句',
        testData: '测试数据',
        explainPlan: '执行计划分析'
      }
      return titles[selectedFunction.value] || '结果'
    }

    // 检查是否可以生成
    const canGenerate = () => {
      switch (selectedFunction.value) {
        case 'sql':
        case 'explain':
        case 'optimize':
          return aiInput.value.trim()
        case 'crud':
          return crudInputs.value.tableName.trim() && crudInputs.value.columns.trim()
        case 'testData':
          return testInputs.value.tableName.trim() && testInputs.value.columns.trim()
        case 'explainPlan':
          return planInputs.value.sql.trim() && planInputs.value.explainResult.trim()
        default:
          return false
      }
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
      generatedResult,
      generating,
      chatHistory,
      selectedFunction,
      aiFunctions,
      crudInputs,
      testInputs,
      planInputs,
      toggleCollapse,
      generateContent,
      copyResult,
      useSql,
      canGenerate,
      getResultTitle,
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
  height: 100%;
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

.ai-functions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 5px;
  padding: 10px;
  border-bottom: 1px solid var(--border-primary);
}

.function-btn {
  padding: 8px 5px;
  font-size: 11px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}

.function-btn:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.function-btn.active {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.crud-inputs,
.test-inputs,
.plan-inputs {
  padding: 10px;
  border-bottom: 1px solid var(--border-primary);
}

.input-field {
  width: 100%;
  padding: 8px;
  margin-bottom: 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  font-size: 12px;
  resize: vertical;
}

.input-field:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.input-field::placeholder {
  color: var(--text-tertiary);
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