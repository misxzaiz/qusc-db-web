<template>
  <div class="app-layout">
    <!-- 左侧边栏 -->
    <TabSidebar
      @resize="handleSidebarResize"
      @history-select="onHistorySelect"
      @history-copy="onHistoryCopy"
      @history-execute="onHistoryExecute"
      @history-clear="onHistoryClear"
      @query-history-ready="onQueryHistoryReady"
    />

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- Tab栏 -->
      <div class="tab-bar">
        <div class="tab-list">
          <div
            v-for="(tab, index) in tabs"
            :key="tab.id"
            class="tab-item"
            :class="{ active: activeTabIndex === index }"
            @click="switchTab(index)"
          >
            <span class="tab-title">{{ tab.title }}</span>
            <span v-if="tab.modified" class="modified-indicator">●</span>
            <button class="tab-close" @click.stop="closeTab(index)">
              <font-awesome-icon icon="times" />
            </button>
          </div>
          <button class="btn btn-small tab-new" @click="newTab" title="新建Tab">
            <font-awesome-icon icon="plus" />
          </button>
        </div>
      </div>

      <!-- Tab内容区 -->
      <div class="tab-content">
        <div v-for="(tab, index) in tabs" :key="tab.id" v-show="activeTabIndex === index" class="tab-pane">
          <!-- 连接和数据库选择器 -->
          <div class="tab-toolbar">
            <div class="toolbar-left">
              <select v-model="tab.sessionId" class="connection-select" @change="onTabConnectionChange(tab)">
                <option value="">选择连接</option>
                <option v-for="session in activeSessions" :key="session.sessionId" :value="session.sessionId">
                  {{ session.connectionInfo.name }}
                </option>
              </select>
              <select
                v-model="tab.database"
                class="database-select"
                @change="onTabDatabaseChange(tab)"
                :disabled="!tab.sessionId"
              >
                <option value="">选择数据库</option>
                <option v-for="db in getTabDatabases(tab)" :key="db" :value="db">
                  {{ db }}
                </option>
              </select>
              <!-- 事务状态 -->
              <div v-if="tab.inTransaction" class="transaction-status">
                <font-awesome-icon icon="exclamation-triangle" class="warning-icon" />
                事务中
                <button class="btn btn-small" @click="commitTransaction(tab)">提交</button>
                <button class="btn btn-small btn-danger" @click="rollbackTransaction(tab)">回滚</button>
              </div>
            </div>
            <div class="toolbar-right">
              <button class="btn btn-primary btn-compact" @click="executeSql(tab)" :disabled="!tab.sessionId || !tab.sqlText.trim()">
                <font-awesome-icon icon="play" />
                <span class="btn-text">执行</span>
              </button>
              <button class="btn btn-secondary btn-compact" @click="clearEditor(tab)">
                <font-awesome-icon icon="times" />
                <span class="btn-text">清空</span>
              </button>
              <button class="btn btn-secondary btn-compact" @click="formatSql(tab)">
                <font-awesome-icon icon="code" />
                <span class="btn-text">格式</span>
              </button>
            </div>
          </div>

          <!-- 编辑器 -->
          <div class="editor-section">
            <div class="editor-wrapper">
              <SqlCodeEditor
                :ref="`editor-${tab.id}`"
                v-model="tab.sqlText"
                :session-id="tab.sessionId"
                :database="tab.database"
                @execute="() => executeSql(tab)"
              />
            </div>
          </div>

          <!-- 错误信息 -->
          <div v-if="tab.error" class="error-section">
            <h4>
              <font-awesome-icon icon="exclamation-triangle" />
              错误信息
            </h4>
            <pre class="error-message">{{ tab.error }}</pre>

            <!-- AI错误诊断 -->
            <div class="error-diagnosis">
              <button
                class="btn btn-small ai-diagnose-btn"
                @click="diagnoseError(tab)"
                :disabled="tab.diagnosing"
              >
                <font-awesome-icon icon="stethoscope" />
                {{ tab.diagnosing ? '诊断中...' : 'AI诊断' }}
              </button>

              <div v-if="tab.errorDiagnosis" class="diagnosis-result">
                <div class="diagnosis-header">
                  <h5>
                    <font-awesome-icon icon="brain" />
                    AI诊断结果
                  </h5>
                  <button class="btn btn-small" @click="tab.errorDiagnosis = null">
                    <font-awesome-icon icon="times" />
                  </button>
                </div>
                <div class="diagnosis-content" v-html="formatDiagnosis(tab.errorDiagnosis)"></div>
              </div>
            </div>
          </div>

          <!-- 查询结果 -->
          <div v-if="tab.results.length > 0" class="results-container">
            <div v-for="(result, resultIndex) in tab.results" :key="resultIndex" class="result-section">
              <div class="result-header">
                <h4>
                  查询结果 {{ resultIndex + 1 }}
                  <span v-if="result.executionTime" class="execution-time">({{ result.executionTime }}ms)</span>
                </h4>
                <div class="result-actions">
                  <button
                    v-if="result.type === 'select'"
                    class="btn btn-small ai-analyze-btn"
                    @click="analyzeResult(tab, result)"
                    :disabled="result.analyzing"
                  >
                    <font-awesome-icon icon="brain" />
                    {{ result.analyzing ? '分析中...' : 'AI解析' }}
                  </button>
                  <button class="btn btn-small" @click="exportResult(result)">
                    <font-awesome-icon icon="download" />
                    导出
                  </button>
                </div>
              </div>

              <!-- SELECT结果 -->
              <div v-if="result.type === 'select'" class="result-table-wrapper">
                <table v-if="result.data && result.data.length > 0" class="result-table">
                  <thead>
                    <tr>
                      <th v-for="column in result.columns" :key="column" @click="sortResult(tab, resultIndex, column)">
                        {{ column }}
                        <font-awesome-icon
                          :icon="result.sortColumn === column ? (result.sortOrder === 'asc' ? 'sort-up' : 'sort-down') : 'sort'"
                          class="sort-icon"
                        />
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, rowIndex) in getPaginatedData(result)" :key="rowIndex">
                      <td v-for="column in result.columns" :key="column">
                        {{ formatCellValue(row[column]) }}
                      </td>
                    </tr>
                  </tbody>
                </table>

                <!-- 分页组件 -->
                <div v-if="result.totalCount > result.pageSize" class="pagination-component">
                  <div class="pagination-info">
                    共 {{ result.totalCount }} 条记录
                  </div>
                  <div class="pagination-controls">
                    <button
                      class="btn btn-small"
                      @click="firstPage(tab, resultIndex)"
                      :disabled="result.currentPage === 1"
                    >
                      首页
                    </button>
                    <button
                      class="btn btn-small"
                      @click="prevPage(tab, resultIndex)"
                      :disabled="result.currentPage === 1"
                    >
                      <font-awesome-icon icon="angle-left" />
                      上一页
                    </button>

                    <!-- 页码 -->
                    <div class="page-numbers">
                      <button
                        v-for="page in getVisiblePages(result)"
                        :key="page"
                        class="btn btn-small"
                        :class="{ active: page === result.currentPage }"
                        @click="goToPage(tab, resultIndex, page)"
                      >
                        {{ page }}
                      </button>
                    </div>

                    <button
                      class="btn btn-small"
                      @click="nextPage(tab, resultIndex)"
                      :disabled="result.currentPage === result.totalPages"
                    >
                      下一页
                      <font-awesome-icon icon="angle-right" />
                    </button>
                    <button
                      class="btn btn-small"
                      @click="lastPage(tab, resultIndex)"
                      :disabled="result.currentPage === result.totalPages"
                    >
                      末页
                    </button>

                    <!-- 每页大小 -->
                    <select
                      v-model="result.pageSize"
                      @change="changePageSize(tab, resultIndex)"
                      class="page-size-select"
                    >
                      <option value="50">50条/页</option>
                      <option value="100">100条/页</option>
                      <option value="200">200条/页</option>
                      <option value="500">500条/页</option>
                    </select>

                    <!-- 跳转 -->
                    <div class="page-jump">
                      跳至
                      <input
                        type="number"
                        v-model="result.jumpPage"
                        @keyup.enter="goToPage(tab, resultIndex, result.jumpPage)"
                        min="1"
                        :max="result.totalPages"
                        class="page-input"
                      />
                      页
                    </div>
                  </div>
                </div>
              </div>

              <!-- UPDATE/INSERT/DELETE结果 -->
              <div v-else-if="result.type === 'update'" class="update-result">
                <font-awesome-icon icon="check" class="success-icon" />
                执行成功，{{ result.affectedRows }} 行受影响
              </div>

              <!-- 其他结果 -->
              <div v-else class="empty-result">
                执行成功
              </div>

              <!-- AI分析结果 -->
              <div v-if="result.aiAnalysis" class="ai-analysis-section">
                <div class="analysis-header">
                  <h5>
                    <font-awesome-icon icon="brain" />
                    AI分析结果
                  </h5>
                  <button class="btn btn-small" @click="result.aiAnalysis = null">
                    <font-awesome-icon icon="times" />
                  </button>
                </div>
                <div class="analysis-content" v-html="formatAnalysis(result.aiAnalysis)"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧AI边栏 -->
    <AiSidebar @use-sql="onUseAiSql" @resize="handleAiResize" />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { connectionStore } from '../stores/connectionStore'
import SqlCodeEditor from '../components/SqlCodeEditor.vue'
import TabSidebar from '../components/TabSidebar.vue'
import AiSidebar from '../components/AiSidebar.vue'

export default {
  name: 'SqlEditor',

  components: {
    SqlCodeEditor,
    TabSidebar,
    AiSidebar
  },

  setup() {
    // Tab管理
    const tabs = ref([])
    const activeTabIndex = ref(0)
    let tabIdCounter = 0

    // 侧边栏宽度
    const sidebarWidth = ref(40)
    const aiWidth = ref(40)

    // AI配置
    const selectedAiConfig = ref(null)

    // QueryHistory组件引用
    const queryHistoryRef = ref(null)

    // 计算属性
    const activeSessions = computed(() => {
      return connectionStore.getActiveSessions()
    })

    const currentTab = computed(() => {
      return tabs.value[activeTabIndex.value] || {}
    })

    // Tab管理方法
    const createNewTab = () => {
      const tab = {
        id: ++tabIdCounter,
        title: `查询 ${tabIdCounter}`,
        sqlText: '',
        sessionId: '',
        database: '',
        results: [],
        error: null,
        modified: false,
        inTransaction: false
      }
      tabs.value.push(tab)
      return tab
    }

    const newTab = () => {
      const tab = createNewTab()
      activeTabIndex.value = tabs.value.length - 1
      saveTabsToStorage()
    }

    const closeTab = (index) => {
      if (tabs.value.length === 1) return

      tabs.value.splice(index, 1)
      if (activeTabIndex.value >= tabs.value.length) {
        activeTabIndex.value = tabs.value.length - 1
      }
      saveTabsToStorage()
    }

    const switchTab = (index) => {
      activeTabIndex.value = index
    }

    const markTabModified = (tab) => {
      tab.modified = true
      saveTabsToStorage()
    }

    const saveTabsToStorage = () => {
      const tabsData = tabs.value.map(tab => ({
        title: tab.title,
        sqlText: tab.sqlText,
        sessionId: tab.sessionId,
        database: tab.database
      }))
      sessionStorage.setItem('sql_editor_tabs', JSON.stringify(tabsData))
      sessionStorage.setItem('sql_editor_active_tab', activeTabIndex.value)
    }

    const loadTabsFromStorage = () => {
      const saved = sessionStorage.getItem('sql_editor_tabs')
      const activeTab = sessionStorage.getItem('sql_editor_active_tab')

      if (saved) {
        try {
          const tabsData = JSON.parse(saved)
          tabs.value = tabsData.map(data => ({
            ...data,
            id: ++tabIdCounter,
            results: [],
            error: null,
            modified: false,
            inTransaction: false
          }))
          if (tabs.value.length === 0) {
            createNewTab()
          }
        } catch (e) {
          createNewTab()
        }
      } else {
        createNewTab()
      }

      if (activeTab && !isNaN(parseInt(activeTab))) {
        activeTabIndex.value = parseInt(activeTab)
      }
    }

    // Tab相关事件
    const onTabConnectionChange = (tab) => {
      const session = connectionStore.getSession(tab.sessionId)
      if (session) {
        tab.database = session.currentDatabase || ''
      }
      markTabModified(tab)
    }

    const onTabDatabaseChange = (tab) => {
      if (tab.sessionId && tab.database) {
        connectionStore.switchDatabase(tab.sessionId, tab.database)
      }
      markTabModified(tab)
    }

    const getTabDatabases = (tab) => {
      const session = connectionStore.getSession(tab.sessionId)
      return session ? session.databases : []
    }

    // SQL执行方法
    const executeSql = async (tab) => {
      if (!tab.sessionId || !tab.sqlText.trim()) return

      tab.error = null
      tab.results = []

      try {
        // 解析多条SQL
        const sqlStatements = parseSqlStatements(tab.sqlText)
        const startTime = Date.now()

        for (const sql of sqlStatements) {
          const result = await executeSingleSql(tab.sessionId, sql)
          result.executionTime = Date.now() - startTime
          tab.results.push(result)

          // 如果是错误，停止执行
          if (result.error) {
            tab.error = result.error
            break
          }

          // 检查事务状态
          if (isTransactionCommand(sql)) {
            if (sql.toLowerCase().startsWith('begin') || sql.toLowerCase().startsWith('start transaction')) {
              tab.inTransaction = true
            } else if (sql.toLowerCase().startsWith('commit') || sql.toLowerCase().startsWith('rollback')) {
              tab.inTransaction = false
            }
          }
        }

        // 保存到查询历史
        if (queryHistoryRef.value && sqlStatements.length > 0) {
          queryHistoryRef.value.addToHistory(tab.sqlText.trim(), tab.results[tab.results.length - 1])
        }

        markTabModified(tab)
      } catch (e) {
        tab.error = '执行失败: ' + e.message
      }
    }

    const parseSqlStatements = (sqlText) => {
      // 简单的SQL解析，按分号分割
      const statements = []
      let current = ''
      let inQuotes = false
      let quoteChar = ''

      for (let i = 0; i < sqlText.length; i++) {
        const char = sqlText[i]

        if ((char === "'" || char === '"' || char === '`') && !inQuotes) {
          inQuotes = true
          quoteChar = char
        } else if (char === quoteChar && inQuotes) {
          inQuotes = false
          quoteChar = ''
        } else if (char === ';' && !inQuotes) {
          const statement = current.trim()
          if (statement) {
            statements.push(statement)
          }
          current = ''
          continue
        }

        current += char
      }

      const lastStatement = current.trim()
      if (lastStatement) {
        statements.push(lastStatement)
      }

      return statements
    }

    const executeSingleSql = async (sessionId, sql, page = 1, pageSize = null) => {
      const response = await fetch('/api/sql/execute', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          sessionId,
          sql,
          page,
          pageSize
        })
      })

      const result = await response.json()

      if (result.error) {
        return { error: result.error, type: 'error' }
      }

      // 判断结果类型
      if (result.data) {
        return {
          type: 'select',
          data: result.data,
          columns: result.columns,
          totalCount: result.totalCount || result.data.length,
          currentPage: page,
          pageSize: pageSize || 50,
          totalPages: Math.ceil((result.totalCount || result.data.length) / (pageSize || 50)),
          sortColumn: '',
          sortOrder: 'asc',
          jumpPage: page
        }
      } else if (result.affectedRows !== undefined) {
        return {
          type: 'update',
          affectedRows: result.affectedRows
        }
      } else {
        return {
          type: 'success'
        }
      }
    }

    const isTransactionCommand = (sql) => {
      const lowerSql = sql.trim().toLowerCase()
      return lowerSql.startsWith('begin') ||
             lowerSql.startsWith('start transaction') ||
             lowerSql.startsWith('commit') ||
             lowerSql.startsWith('rollback')
    }

    // 事务方法
    const commitTransaction = async (tab) => {
      await executeSingleSql(tab.sessionId, 'COMMIT')
      tab.inTransaction = false
    }

    const rollbackTransaction = async (tab) => {
      await executeSingleSql(tab.sessionId, 'ROLLBACK')
      tab.inTransaction = false
    }

    // 编辑器方法
    const clearEditor = (tab) => {
      tab.sqlText = ''
      tab.error = null
      tab.results = []
      markTabModified(tab)
    }

    const formatSql = (tab) => {
      // 简单的SQL格式化
      let formatted = tab.sqlText
        .replace(/\s+/g, ' ')
        .replace(/,/g, ',\n  ')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .replace(/\bJOIN\b/gi, '\nJOIN')
        .replace(/\bLEFT JOIN\b/gi, '\nLEFT JOIN')
        .replace(/\bRIGHT JOIN\b/gi, '\nRIGHT JOIN')
        .replace(/\bINNER JOIN\b/gi, '\nINNER JOIN')

      tab.sqlText = formatted
      markTabModified(tab)
    }

    // AI错误诊断
    const diagnoseError = async (tab) => {
      if (!tab.error || !tab.sqlText) return

      tab.diagnosing = true
      try {
        const response = await fetch('/api/ai/analyze-error', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            sql: tab.sqlText,
            error: tab.error,
            configId: selectedAiConfig.value?.id
          })
        })

        const data = await response.json()
        if (data.analysis) {
          tab.errorDiagnosis = data.analysis
        }
      } catch (error) {
        console.error('错误诊断失败:', error)
        alert('诊断失败: ' + (error.response?.data?.error || error.message))
      } finally {
        tab.diagnosing = false
      }
    }

    // AI分析查询结果
    const analyzeResult = async (tab, result) => {
      if (!tab.sqlText || !result.data) return

      result.analyzing = true
      try {
        const response = await fetch('/api/ai/analyze-query-result', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            sql: tab.sqlText,
            result: {
              columns: result.columns,
              data: result.data.slice(0, 10), // 只发送前10行
              totalCount: result.totalCount
            },
            configId: selectedAiConfig.value?.id
          })
        })

        const data = await response.json()
        if (data.analysis) {
          result.aiAnalysis = data.analysis
        }
      } catch (error) {
        console.error('分析失败:', error)
        alert('分析失败: ' + (error.response?.data?.error || error.message))
      } finally {
        result.analyzing = false
      }
    }

    // 格式化AI分析结果
    const formatAnalysis = (analysis) => {
      // 将markdown转换为HTML（简单处理）
      return analysis
        .replace(/\n/g, '<br>')
        .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.*?)\*/g, '<em>$1</em>')
        .replace(/```(.*?)```/gs, '<pre><code>$1</code></pre>')
        .replace(/`(.*?)`/g, '<code>$1</code>')
        .replace(/^#{1,6}\s+(.*?)$/gm, (match, content) => {
          const level = match.match(/^#/)[0].length
          return `<h${level}>${content}</h${level}>`
        })
        .replace(/^\* (.+)$/gm, '<li>$1</li>')
        .replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>')
    }

    // 格式化诊断结果
    const formatDiagnosis = (diagnosis) => {
      return formatAnalysis(diagnosis)
    }

    // 结果处理方法
    const formatCellValue = (value) => {
      if (value === null) return 'NULL'
      if (value === undefined) return ''
      if (value instanceof Date) return value.toISOString()
      return String(value)
    }

    const sortResult = async (tab, resultIndex, column) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select') return

      // 切换排序方向
      if (result.sortColumn === column) {
        result.sortOrder = result.sortOrder === 'asc' ? 'desc' : 'asc'
      } else {
        result.sortColumn = column
        result.sortOrder = 'asc'
      }

      // 重新执行查询（带排序）
      const sql = buildSelectWithSort(tab.sqlText, column, result.sortOrder)
      const newResult = await executeSingleSql(tab.sessionId, sql, result.currentPage, result.pageSize)

      // 更新结果
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const buildSelectWithSort = (originalSql, column, order) => {
      // 简单的ORDER BY添加，实际项目中应该更复杂
      const trimmed = originalSql.trim()
      if (trimmed.toLowerCase().includes('order by')) {
        // 已有ORDER BY，修改它
        return trimmed.replace(/\bORDER BY\s+[^;]+/gi, `ORDER BY ${column} ${order}`)
      } else {
        // 添加ORDER BY
        return `${trimmed} ORDER BY ${column} ${order}`
      }
    }

    // 分页方法
    const getPaginatedData = (result) => {
      if (!result.data) return []
      return result.data
    }

    const getVisiblePages = (result) => {
      const current = result.currentPage
      const total = result.totalPages
      const delta = 2
      const range = []
      const rangeWithDots = []

      for (let i = Math.max(2, current - delta); i <= Math.min(total - 1, current + delta); i++) {
        range.push(i)
      }

      if (current - delta > 2) {
        rangeWithDots.push(1, '...')
      } else {
        rangeWithDots.push(1)
      }

      rangeWithDots.push(...range)

      if (current + delta < total - 1) {
        rangeWithDots.push('...', total)
      } else {
        rangeWithDots.push(total)
      }

      return rangeWithDots.filter(page => page !== '...' || rangeWithDots.length > 1)
    }

    const firstPage = async (tab, resultIndex) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select' || result.currentPage === 1) return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, 1, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const lastPage = async (tab, resultIndex) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select' || result.currentPage === result.totalPages) return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, result.totalPages, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const prevPage = async (tab, resultIndex) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select' || result.currentPage === 1) return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, result.currentPage - 1, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const nextPage = async (tab, resultIndex) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select' || result.currentPage === result.totalPages) return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, result.currentPage + 1, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const goToPage = async (tab, resultIndex, page) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select' || page < 1 || page > result.totalPages) return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, page, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const changePageSize = async (tab, resultIndex) => {
      const result = tab.results[resultIndex]
      if (result.type !== 'select') return

      const newResult = await executeSingleSql(tab.sessionId, tab.sqlText, 1, result.pageSize)
      tab.results[resultIndex] = { ...newResult, sortColumn: result.sortColumn, sortOrder: result.sortOrder }
    }

    const exportResult = (result) => {
      if (!result.data) return

      const csv = [
        result.columns.join(','),
        ...result.data.map(row =>
          result.columns.map(col => {
            const val = row[col]
            return val === null ? '' : `"${String(val).replace(/"/g, '""')}"`
          }).join(',')
        )
      ].join('\n')

      const blob = new Blob([csv], { type: 'text/csv' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `query_result_${Date.now()}.csv`
      a.click()
      URL.revokeObjectURL(url)
    }

    // AI相关方法
    const onUseAiSql = (sql) => {
      const tab = currentTab.value
      if (tab) {
        tab.sqlText = sql
        markTabModified(tab)
      }
    }

    const handleSidebarResize = (width) => {
      sidebarWidth.value = width
    }

    const handleAiResize = (width) => {
      aiWidth.value = width
    }

    // 查询历史处理方法
    const onHistorySelect = (sql) => {
      const tab = currentTab.value
      if (tab) {
        tab.sqlText = sql
        markTabModified(tab)
      }
    }

    const onHistoryCopy = (sql) => {
      navigator.clipboard.writeText(sql)
    }

    const onHistoryExecute = async (sql) => {
      const tab = currentTab.value
      if (tab) {
        tab.sqlText = sql
        await executeSql(tab)
      }
    }

    const onHistoryClear = () => {
      console.log('查询历史已清空')
    }

    const onQueryHistoryReady = (ref) => {
      queryHistoryRef.value = ref
    }

    // 加载AI配置
    const loadAiConfig = async () => {
      try {
        const response = await fetch('/api/ai/configs')
        const configs = await response.json()
        if (configs.length > 0) {
          selectedAiConfig.value = configs.find(c => c.enabled) || configs[0]
        }
      } catch (error) {
        console.error('加载AI配置失败:', error)
      }
    }

    // 键盘快捷键
    const handleKeydown = (e) => {
      if (e.ctrlKey && e.key === 'Enter') {
        executeSql(currentTab.value)
      }
      if (e.key === 'F5') {
        e.preventDefault()
        executeSql(currentTab.value)
      }
      if (e.ctrlKey && e.key === 't') {
        e.preventDefault()
        newTab()
      }
      if (e.ctrlKey && e.key === 'w') {
        e.preventDefault()
        closeTab(activeTabIndex.value)
      }
    }

    // 生命周期
    onMounted(() => {
      window.addEventListener('keydown', handleKeydown)
      connectionStore.loadConnections()
      loadTabsFromStorage()
      loadAiConfig()
    })

    onUnmounted(() => {
      window.removeEventListener('keydown', handleKeydown)
    })

    // 监听Tab变化
    watch(() => currentTab.value.sqlText, () => {
      markTabModified(currentTab.value)
    })

    // 返回所有需要在模板中使用的数据和方法
    return {
      tabs,
      activeTabIndex,
      activeSessions,
      currentTab,
      sidebarWidth,
      aiWidth,
      selectedAiConfig,
      queryHistoryRef,
      newTab,
      closeTab,
      switchTab,
      onTabConnectionChange,
      onTabDatabaseChange,
      getTabDatabases,
      executeSql,
      clearEditor,
      formatSql,
      formatCellValue,
      sortResult,
      getPaginatedData,
      getVisiblePages,
      firstPage,
      lastPage,
      prevPage,
      nextPage,
      goToPage,
      changePageSize,
      commitTransaction,
      rollbackTransaction,
      exportResult,
      diagnoseError,
      analyzeResult,
      formatAnalysis,
      formatDiagnosis,
      onUseAiSql,
      handleSidebarResize,
      handleAiResize,
      onHistorySelect,
      onHistoryCopy,
      onHistoryExecute,
      onHistoryClear,
      onQueryHistoryReady
    }
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

/* Tab样式 */
.tab-bar {
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  align-items: center;
  padding: 0 10px;
  overflow-x: auto;
}

.tab-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  user-select: none;
  white-space: nowrap;
  gap: 5px;
}

.tab-item:hover {
  background-color: var(--bg-hover);
}

.tab-item.active {
  background-color: var(--bg-primary);
  border-bottom-color: var(--accent-primary);
}

.tab-title {
  font-size: 13px;
  color: var(--text-primary);
}

.modified-indicator {
  color: var(--accent-primary);
  font-size: 10px;
}

.tab-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 2px;
  border-radius: 2px;
  font-size: 12px;
}

.tab-close:hover {
  background-color: var(--bg-hover);
  color: var(--text-primary);
}

.tab-new {
  margin-left: 10px;
  padding: 6px 10px;
}

.tab-content {
  flex: 1;
  overflow: hidden;
}

.tab-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* Tab工具栏 */
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  flex-shrink: 0;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.connection-select,
.database-select {
  padding: 4px 8px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  font-size: 12px;
  min-width: 150px;
}

.transaction-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  background-color: var(--warning-bg);
  color: var(--warning);
  border-radius: var(--radius-sm);
  font-size: 12px;
}

.warning-icon {
  color: var(--warning);
}

.btn-danger {
  background-color: var(--error-bg);
  color: var(--error);
}

.btn-danger:hover {
  background-color: var(--error);
  color: white;
}

/* 编辑器部分 */
.editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-wrapper {
  flex: 1;
  overflow: hidden;
}

/* 错误信息 */
.error-section {
  padding: 15px;
  background-color: var(--error-bg);
  border-top: 1px solid var(--error-border);
}

.error-section h4 {
  margin: 0 0 10px 0;
  color: var(--error);
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-message {
  color: var(--text-primary);
  background-color: var(--bg-primary);
  padding: 10px;
  border-radius: var(--radius-sm);
  font-family: var(--font-family-mono);
  white-space: pre-wrap;
  margin: 0;
}

.error-diagnosis {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--border-secondary);
}

.ai-diagnose-btn {
  background-color: var(--warning-bg);
  color: var(--warning);
  border-color: var(--warning);
}

.ai-diagnose-btn:hover {
  background-color: var(--warning);
  color: white;
}

.diagnosis-result {
  margin-top: 10px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.diagnosis-header {
  padding: 8px 12px;
  background-color: var(--bg-tertiary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-primary);
}

.diagnosis-header h5 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.diagnosis-content {
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.diagnosis-content :deep(h1),
.diagnosis-content :deep(h2),
.diagnosis-content :deep(h3),
.diagnosis-content :deep(h4),
.diagnosis-content :deep(h5),
.diagnosis-content :deep(h6) {
  margin: 10px 0 5px 0;
  color: var(--text-primary);
}

.diagnosis-content :deep(code) {
  background-color: var(--bg-tertiary);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: var(--font-family-mono);
  font-size: 12px;
}

.diagnosis-content :deep(pre) {
  background-color: var(--bg-tertiary);
  padding: 10px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  margin: 10px 0;
}

.diagnosis-content :deep(pre code) {
  background: none;
  padding: 0;
}

.diagnosis-content :deep(ul),
.diagnosis-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.diagnosis-content :deep(li) {
  margin: 4px 0;
}

.diagnosis-content :deep(strong) {
  color: var(--accent-primary);
}

.ai-analysis-section {
  margin-top: 10px;
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.analysis-header {
  padding: 8px 12px;
  background-color: var(--bg-quaternary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border-primary);
}

.analysis-header h5 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.analysis-content {
  padding: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.analysis-content :deep(h1),
.analysis-content :deep(h2),
.analysis-content :deep(h3),
.analysis-content :deep(h4),
.analysis-content :deep(h5),
.analysis-content :deep(h6) {
  margin: 10px 0 5px 0;
  color: var(--text-primary);
}

.analysis-content :deep(code) {
  background-color: var(--bg-secondary);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: var(--font-family-mono);
  font-size: 12px;
}

.analysis-content :deep(pre) {
  background-color: var(--bg-secondary);
  padding: 10px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  margin: 10px 0;
}

.analysis-content :deep(pre code) {
  background: none;
  padding: 0;
}

.analysis-content :deep(ul),
.analysis-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.analysis-content :deep(li) {
  margin: 4px 0;
}

.analysis-content :deep(strong) {
  color: var(--accent-primary);
}

.ai-analyze-btn {
  background-color: var(--info-bg);
  color: var(--info);
  border-color: var(--info);
}

.ai-analyze-btn:hover {
  background-color: var(--info);
  color: white;
}

/* 结果容器 */
.results-container {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.result-section {
  background-color: var(--bg-secondary);
  border-top: 1px solid var(--border-primary);
  margin-bottom: 10px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 15px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
}

.result-header h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: 13px;
}

.execution-time {
  font-size: 11px;
  color: var(--text-secondary);
  margin-left: 10px;
}

.result-actions {
  display: flex;
  gap: 5px;
}

.result-table-wrapper {
  overflow: auto;
}

.result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.result-table th,
.result-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.result-table th {
  background-color: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 500;
  cursor: pointer;
  user-select: none;
  position: sticky;
  top: 0;
  z-index: 1;
}

.result-table th:hover {
  background-color: var(--bg-hover);
}

.sort-icon {
  margin-left: 5px;
  opacity: 0.5;
}

.result-table tbody tr:hover {
  background-color: var(--bg-hover);
}

.update-result,
.empty-result {
  padding: 40px;
  text-align: center;
  color: var(--text-secondary);
}

.success-icon {
  color: var(--success);
  margin-right: 8px;
}

/* 分页组件 */
.pagination-component {
  padding: 15px;
  background-color: var(--bg-tertiary);
  border-top: 1px solid var(--border-primary);
}

.pagination-info {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--text-secondary);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.page-numbers {
  display: flex;
  gap: 2px;
}

.page-numbers .btn.active {
  background-color: var(--accent-primary);
  color: white;
}

.page-size-select {
  padding: 4px 8px;
  font-size: 12px;
}

.page-jump {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
}

.page-input {
  width: 50px;
  padding: 4px;
  font-size: 12px;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  background-color: var(--bg-primary);
  color: var(--text-primary);
}

/* 通用按钮样式 */
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

.btn-secondary {
  background-color: var(--btn-secondary-bg);
  color: var(--btn-secondary-text);
}

.btn-secondary:hover:not(:disabled) {
  background-color: var(--btn-secondary-hover);
}

.btn-small {
  padding: 4px 8px;
  font-size: 11px;
}

.btn-compact {
  padding: 4px 8px;
  font-size: 12px;
}

.btn-compact .btn-text {
  margin-left: 4px;
}

.btn-compact svg {
  width: 12px !important;
  height: 12px !important;
}
</style>