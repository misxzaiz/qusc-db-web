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
    <div class="main-content" :class="{ 'sidebar-expanded': isRightSidebarExpanded }">
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
              <button class="btn btn-primary btn-compact" @click="executeSql(tab)" :disabled="!tab.sessionId || !tab.sqlText.trim() || tab.executing">
                <font-awesome-icon v-if="!tab.executing" icon="play" />
                <font-awesome-icon v-else icon="spinner" spin />
                <span class="btn-text">{{ tab.executing ? '执行中...' : '执行' }}</span>
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
          <div class="editor-section" :style="{ height: editorHeight + 'px' }">
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

          <!-- 拖拽手柄 -->
          <div
            class="resize-handle"
            :class="{ 'resizing': isResizing }"
            @mousedown="startResize"
          ></div>

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
            </div>
          </div>

          <!-- 查询结果 - 使用新的ResultTabs组件 -->
          <div class="results-section">
            <ResultTabs
              :results="tab.results"
              :is-batch-execution="tab.isBatchExecution || false"
              :batch-results="tab.batchResults || []"
              @analyze="handleAnalyzeClick"
              @export="exportResult"
              @refresh="handleResultRefresh"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧边栏 -->
    <RightTabSidebar
      ref="rightSidebarRef"
      :get-current-tab-tables="getCurrentTabTables"
      :get-current-tab-info="getCurrentTabInfo"
      @execute-sql="onExecuteAiSql"
      @open-in-new-tab="onOpenAiSqlInNewTab"
      @execute-batch-sql="onExecuteBatchSql"
      @batch-sql-execute="onBatchSqlExecute"
      @batch-sql-cancelled="onBatchSqlCancelled"
      @batch-sql-completed="onBatchSqlCompleted"
      @resize="handleRightSidebarResize"
    />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { connectionStore } from '../stores/connectionStore'
import SqlCodeEditor from '../components/SqlCodeEditor.vue'
import TabSidebar from '../components/TabSidebar.vue'
import RightTabSidebar from '../components/RightTabSidebar.vue'
import ResultTabs from '../components/ResultTabs.vue'
import { faBrain, faStethoscope } from '@fortawesome/free-solid-svg-icons'
import { library } from '@fortawesome/fontawesome-svg-core'

library.add(faBrain, faStethoscope)

export default {
  name: 'SqlEditor',

  components: {
    SqlCodeEditor,
    TabSidebar,
    RightTabSidebar,
    ResultTabs
  },

  setup() {
    // Tab管理
    const tabs = ref([])
    const activeTabIndex = ref(0)
    let tabIdCounter = 0

    // 连接管理
    const activeSessions = computed(() => connectionStore.getActiveSessions())

    // 引用
    const rightSidebarRef = ref(null)
    const queryHistoryRef = ref(null)

    // 编辑器高度管理
    const editorHeight = ref(300)
    const isResizing = ref(false)
    const startY = ref(0)
    const startHeight = ref(300)
    const containerHeight = ref(0)

    // 创建新Tab
    const createNewTab = (title = '新查询') => {
      const tab = {
        id: ++tabIdCounter,
        title,
        sqlText: '',
        sessionId: '',
        database: '',
        results: [],
        error: null,
        modified: false,
        inTransaction: false,
        executing: false,
        diagnosing: false
      }
      tabs.value.push(tab)
      return tab
    }

    // 初始化
    onMounted(() => {
      loadTabsFromStorage()
      restoreLastConnection()
      loadEditorHeight()

      // 监听连接事件
      window.addEventListener('session-connected', handleSessionConnected)

      // 初始化右侧边栏状态（默认折叠）
      nextTick(() => {
        isRightSidebarExpanded.value = false
      })
    })

    onUnmounted(() => {
      window.removeEventListener('session-connected', handleSessionConnected)
    })

    // 恢复最后的连接
    const restoreLastConnection = async () => {
      try {
        const lastConn = sessionStorage.getItem('last_connection')
        if (lastConn) {
          const { sessionId, database } = JSON.parse(lastConn)
          const session = connectionStore.getSession(sessionId)

          if (session) {
            // 找到第一个Tab并设置连接
            if (tabs.value.length > 0) {
              tabs.value[0].sessionId = sessionId
              tabs.value[0].database = database || session.currentDatabase || ''

              // 确保连接在store中是活跃状态
              if (!connectionStore.getActiveSessions().find(s => s.sessionId === sessionId)) {
                connectionStore.addActiveSession(sessionId, session.connectionInfo)
              }

              // 触发事件通知其他组件
              const event = new CustomEvent('session-connected', {
                detail: {
                  sessionId,
                  database: database || session.currentDatabase,
                  autoConnect: true
                }
              })
              window.dispatchEvent(event)
            }
          }
        }
      } catch (error) {
        console.error('自动连接失败', error)
      }
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
            inTransaction: false,
            executing: false,
            diagnosing: false
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
        saveLastConnection(tab.sessionId, tab.database)
      }
      markTabModified(tab)
    }

    const getTabDatabases = (tab) => {
      const session = connectionStore.getSession(tab.sessionId)
      return session ? session.databases : []
    }

    // 保存最后连接状态
    const saveLastConnection = (sessionId, database) => {
      sessionStorage.setItem('last_connection', JSON.stringify({ sessionId, database }))
    }

    // SQL执行方法 - 优化版本
    const executeSql = async (tab) => {
      if (!tab.sessionId || !tab.sqlText.trim() || tab.executing) return

      tab.error = null
      tab.executing = true

      try {
        // 解析多条SQL
        const sqlStatements = parseSqlStatements(tab.sqlText)

        // 清空之前的结果
        tab.results = []

        for (let i = 0; i < sqlStatements.length; i++) {
          const sql = sqlStatements[i]
          const startTime = Date.now()

          try {
            const result = await executeSingleSql(tab.sessionId, sql)
            result.executionTime = Date.now() - startTime
            result.statementIndex = i + 1
            result.sql = sql

            // 初始化结果属性
            if (!result.pageSize) result.pageSize = 50
            if (!result.currentPage) result.currentPage = 1

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
          } catch (e) {
            const errorResult = {
              error: e.message,
              executionTime: Date.now() - startTime,
              statementIndex: i + 1,
              sql: sql,
              type: 'error'
            }
            tab.results.push(errorResult)
            tab.error = e.message
            break
          }
        }

        // 保存到查询历史
        if (queryHistoryRef.value && sqlStatements.length > 0) {
          queryHistoryRef.value.addToHistory(tab.sqlText.trim(), tab.results[tab.results.length - 1])
        }

        markTabModified(tab)
      } finally {
        tab.executing = false
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

    const executeSingleSql = async (sessionId, sql) => {
      const response = await fetch('/api/sql/execute', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          sessionId,
          sql,
          page: null,  // 不使用后端分页
          pageSize: null
        })
      })

      if (!response.ok) {
        const error = await response.text()
        throw new Error(error || '执行失败')
      }

      const data = await response.json()

      // 判断结果类型
      if (data.error) {
        return {
          type: 'error',
          error: data.error,
          data: null,
          columns: []
        }
      } else if (data.data) {
        // SELECT查询
        const result = {
          type: 'select',
          data: data.data || [],
          columns: data.columns || [],
          totalCount: data.totalCount || data.data.length,
          // 初始化分页属性
          pageSize: 50,
          currentPage: 1,
          searchText: '',
          filteredData: null,
          sortColumn: null,
          sortOrder: 'asc',
          selectedRows: new Set()
        }
        return result
      } else if (data.affectedRows !== undefined) {
        // UPDATE/INSERT/DELETE
        return {
          type: 'update',
          affectedRows: data.affectedRows
        }
      } else {
        // 其他命令
        return {
          type: 'success',
          message: data.message || '执行成功'
        }
      }
    }

    const isTransactionCommand = (sql) => {
      const lowerSql = sql.toLowerCase().trim()
      return lowerSql.startsWith('begin') ||
             lowerSql.startsWith('start transaction') ||
             lowerSql.startsWith('commit') ||
             lowerSql.startsWith('rollback')
    }

    // 事务控制
    const commitTransaction = async (tab) => {
      try {
        await executeSingleSql(tab.sessionId, 'COMMIT')
        tab.inTransaction = false
      } catch (e) {
        tab.error = '提交失败: ' + e.message
      }
    }

    const rollbackTransaction = async (tab) => {
      try {
        await executeSingleSql(tab.sessionId, 'ROLLBACK')
        tab.inTransaction = false
      } catch (e) {
        tab.error = '回滚失败: ' + e.message
      }
    }

    // 其他方法
    const clearEditor = (tab) => {
      tab.sqlText = ''
      tab.error = null
      markTabModified(tab)
    }

    const formatSql = (tab) => {
      // 简单的SQL格式化
      let formatted = tab.sqlText
        .replace(/\s+/g, ' ')
        .replace(/\bSELECT\b/gi, '\nSELECT')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .trim()

      tab.sqlText = formatted
      markTabModified(tab)
    }

    // AI分析
    const handleAnalyzeClick = ({ result, tabIndex }) => {
      const tab = tabs.value[activeTabIndex.value]
      analyzeResult(tab, result)
    }

    const analyzeResult = async (tab, result) => {
      if (!tab.sqlText || !result.data) return

      await nextTick()

      const sampleData = result.data.slice(0, 5)
      const columnsInfo = result.columns.join(', ')

      const analysisMessage = `请帮我分析这个SQL查询结果：

SQL语句：
\`\`\`sql
${tab.sqlText}
\`\`\`

查询结果概览：
- 总行数：${result.totalCount}
- 列信息：${columnsInfo}
- 示例数据：
${JSON.stringify(sampleData, null, 2)}

请分析：
1. 这个查询的主要目的是什么？
2. 查询结果有什么特点或规律？
3. 是否有优化的建议？
4. 数据质量如何？`

      if (rightSidebarRef.value) {
        if (rightSidebarRef.value.ensureAiTabOpen) {
          rightSidebarRef.value.ensureAiTabOpen()
        }
        if (rightSidebarRef.value.sendMessage) {
          rightSidebarRef.value.sendMessage(analysisMessage)
        }
      }

      result.analyzing = false
    }

    // 导出结果
    const exportResult = (result) => {
      if (!result.data || result.data.length === 0) {
        alert('没有数据可导出')
        return
      }

      const csv = [
        result.columns.join(','),
        ...result.data.map(row =>
          result.columns.map(col => {
            const value = row[col]
            return value === null ? '' : String(value).replace(/"/g, '""')
          }).join(',')
        )
      ].join('\n')

      const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `query_result_${Date.now()}.csv`
      link.click()
    }

    // 刷新结果
    const handleResultRefresh = ({ result, tabIndex }) => {
      const tab = tabs.value[activeTabIndex.value]
      if (result.sql) {
        // 重新执行单个SQL
        executeSingleSql(tab.sessionId, result.sql).then(newResult => {
          // 更新结果
          Object.assign(result, newResult)
        }).catch(e => {
          result.error = e.message
        })
      }
    }

    // 错误诊断
    const diagnoseError = (tab) => {
      if (!tab.error) return

      const diagnosisMessage = `请帮我诊断这个SQL错误：

错误信息：
${tab.error}

执行的SQL：
\`\`\`sql
${tab.sqlText}
\`\`\`

请分析：
1. 错误的原因是什么？
2. 如何修复这个错误？
3. 有什么改进建议？`

      if (rightSidebarRef.value) {
        if (rightSidebarRef.value.ensureAiTabOpen) {
          rightSidebarRef.value.ensureAiTabOpen()
        }
        if (rightSidebarRef.value.sendMessage) {
          rightSidebarRef.value.sendMessage(diagnosisMessage)
        }
      }
    }

    // 历史记录相关
    const handleSessionConnected = (event) => {
      const { sessionId, database } = event.detail
      if (tabs.value[activeTabIndex.value]) {
        tabs.value[activeTabIndex.value].sessionId = sessionId
        tabs.value[activeTabIndex.value].database = database || ''
      }
    }

    const onHistorySelect = (historyItem) => {
      const tab = tabs.value[activeTabIndex.value]
      if (tab) {
        tab.sqlText = historyItem.sql
        markTabModified(tab)
      }
    }

    const onHistoryCopy = (historyItem) => {
      navigator.clipboard.writeText(historyItem.sql)
    }

    const onHistoryExecute = (historyItem) => {
      const tab = tabs.value[activeTabIndex.value]
      if (tab) {
        tab.sqlText = historyItem.sql
        executeSql(tab)
      }
    }

    const onHistoryClear = () => {
      // 清空历史记录
    }

    const onQueryHistoryReady = (ref) => {
      queryHistoryRef.value = ref
    }

    const onExecuteAiSql = (sql) => {
      const tab = tabs.value[activeTabIndex.value]
      if (tab) {
        tab.sqlText = sql
        executeSql(tab)
      }
    }

    const onOpenAiSqlInNewTab = (sql) => {
      // 创建新Tab
      const newTab = {
        id: Date.now().toString(),
        title: 'SQL',
        sqlText: sql,
        results: [],
        error: null,
        executing: false,
        modified: false,
        sessionId: tabs.value[activeTabIndex.value]?.sessionId || '',
        database: tabs.value[activeTabIndex.value]?.database || '',
        inTransaction: false
      }

      tabs.value.push(newTab)
      activeTabIndex.value = tabs.value.length - 1

      // 自动执行
      nextTick(() => {
        executeSql(newTab)
      })
    }

    // 批量执行处理
    const onExecuteBatchSql = (batchData) => {
      // 创建批量执行Tab
      const batchTab = {
        id: batchData.id,
        title: `批量执行 (${batchData.total}条)`,
        sqlText: batchData.sqlList.join('\n\n'),
        results: [],
        error: null,
        executing: false,
        modified: false,
        sessionId: tabs.value[activeTabIndex.value]?.sessionId || '',
        database: tabs.value[activeTabIndex.value]?.database || '',
        inTransaction: false,
        isBatchExecution: true,
        batchResults: new Array(batchData.total).fill(null).map(() => ({
          sql: '',
          status: 'pending',
          result: null,
          error: null,
          executionTime: null
        }))
      }

      tabs.value.push(batchTab)
      activeTabIndex.value = tabs.value.length - 1

      // 存储批量执行信息
      batchTab.batchId = batchData.id
      batchTab.totalSql = batchData.total
      batchTab.currentSqlIndex = 0
    }

    // 处理单条批量SQL执行
    const onBatchSqlExecute = async (data) => {
      const tab = tabs.value.find(t => t.batchId === data.batchId)
      if (!tab) return

      const index = data.index
      tab.batchResults[index] = {
        sql: data.sql,
        status: 'executing',
        result: null,
        error: null,
        executionTime: null
      }

      // 执行SQL
      try {
        const startTime = Date.now()
        await executeSqlInTab(tab, data.sql)
        const executionTime = Date.now() - startTime

        tab.batchResults[index] = {
          sql: data.sql,
          status: 'success',
          result: tab.results[tab.results.length - 1] || null,
          error: null,
          executionTime
        }
      } catch (error) {
        tab.batchResults[index] = {
          sql: data.sql,
          status: 'error',
          result: null,
          error: error.message || '执行失败',
          executionTime: null
        }
      }
    }

    // 批量执行取消
    const onBatchSqlCancelled = (data) => {
      const tab = tabs.value.find(t => t.batchId === data.batchId)
      if (!tab) return

      // 标记后续SQL为已取消
      for (let i = data.index; i < tab.batchResults.length; i++) {
        tab.batchResults[i] = {
          ...tab.batchResults[i],
          status: 'cancelled'
        }
      }
    }

    // 批量执行完成
    const onBatchSqlCompleted = (data) => {
      const tab = tabs.value.find(t => t.batchId === data.batchId)
      if (!tab) return

      // 更新Tab标题，显示执行统计
      const successCount = tab.batchResults.filter(r => r.status === 'success').length
      const errorCount = tab.batchResults.filter(r => r.status === 'error').length
      tab.title = `批量执行 (成功: ${successCount}, 失败: ${errorCount})`
    }

    // 在Tab中执行单条SQL（用于批量执行）
    const executeSqlInTab = async (tab, sql) => {
      if (!tab.sessionId || !sql.trim()) {
        throw new Error('会话ID或SQL不能为空')
      }

      const result = await executeSingleSql(tab.sessionId, sql)

      // 将结果添加到Tab的结果列表
      tab.results.push({
        ...result,
        sql: sql,
        timestamp: new Date()
      })

      return result
    }

    const handleSidebarResize = (width) => {
      // 处理侧边栏大小调整
    }

    const isRightSidebarExpanded = ref(false)

    const handleRightSidebarResize = (width) => {
      // 处理右侧边栏大小调整
      isRightSidebarExpanded.value = width > 40
    }

    // 获取当前Tab的表列表
    const getCurrentTabTables = () => {
      const tab = tabs.value[activeTabIndex.value]
      if (!tab || !tab.sessionId || !tab.database) {
        return []
      }

      const session = connectionStore.getSession(tab.sessionId)
      if (session && session.tables[tab.database]) {
        return session.tables[tab.database]
      }

      return []
    }

    // 获取当前Tab的会话信息
    const getCurrentTabInfo = () => {
      const tab = tabs.value[activeTabIndex.value]
      if (!tab) {
        return null
      }
      return {
        sessionId: tab.sessionId,
        database: tab.database
      }
    }

    // 拖拽调整高度相关方法
    const startResize = (e) => {
      isResizing.value = true
      startY.value = e.clientY
      startHeight.value = editorHeight.value

      // 获取容器高度
      const container = document.querySelector('.tab-content')
      if (container) {
        containerHeight.value = container.clientHeight
      }

      // 添加全局事件监听
      document.addEventListener('mousemove', handleResize)
      document.addEventListener('mouseup', stopResize)

      // 防止选中文本
      e.preventDefault()
      document.body.style.cursor = 'ns-resize'
      document.body.style.userSelect = 'none'
    }

    const handleResize = (e) => {
      if (!isResizing.value) return

      const deltaY = e.clientY - startY.value
      let newHeight = startHeight.value + deltaY

      // 限制高度范围（最小100px，最大70%容器高度）
      if (containerHeight.value > 0) {
        newHeight = Math.max(100, Math.min(newHeight, containerHeight.value * 0.7))
      }

      editorHeight.value = Math.round(newHeight)

      // 保存到localStorage
      localStorage.setItem('editor-height', editorHeight.value.toString())
    }

    const stopResize = () => {
      isResizing.value = false
      document.removeEventListener('mousemove', handleResize)
      document.removeEventListener('mouseup', stopResize)

      // 恢复样式
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    // 从localStorage恢复高度
    const loadEditorHeight = () => {
      const saved = localStorage.getItem('editor-height')
      if (saved) {
        const height = parseInt(saved, 10)
        if (!isNaN(height) && height >= 100 && height <= 1000) {
          editorHeight.value = height
        }
      }
    }

    return {
      tabs,
      activeTabIndex,
      activeSessions,
      rightSidebarRef,
      isRightSidebarExpanded,
      editorHeight,
      isResizing,
      getCurrentTabTables,
      getCurrentTabInfo,
      switchTab,
      closeTab,
      newTab,
      executeSql,
      clearEditor,
      formatSql,
      onTabConnectionChange,
      onTabDatabaseChange,
      getTabDatabases,
      handleAnalyzeClick,
      exportResult,
      handleResultRefresh,
      diagnoseError,
      onHistorySelect,
      onHistoryCopy,
      onHistoryExecute,
      onHistoryClear,
      onQueryHistoryReady,
      onExecuteAiSql,
      onOpenAiSqlInNewTab,
      onExecuteBatchSql,
      onBatchSqlExecute,
      onBatchSqlCancelled,
      onBatchSqlCompleted,
      handleSidebarResize,
      handleRightSidebarResize,
      commitTransaction,
      rollbackTransaction,
      startResize,
      handleResize,
      stopResize,
      loadEditorHeight
    }
  }
}
</script>

<style scoped>
@import '../styles/theme.css';

.app-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 为右侧边栏预留空间 */
  padding-right: 40px; /* 默认右侧边栏宽度 */
  transition: padding-right 0.3s ease;
}

/* 当右侧边栏展开时，增加padding */
.main-content.sidebar-expanded {
  padding-right: 400px;
}

/* Tab栏 */
.tab-bar {
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  padding: 0 var(--spacing-sm);
  height: 40px;
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  align-items: center;
  gap: 2px;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  cursor: pointer;
  color: var(--text-secondary);
  transition: var(--transition-fast);
  position: relative;
}

.tab-item:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.tab-item.active {
  background-color: var(--bg-primary);
  color: var(--accent-primary);
  border-bottom: 2px solid var(--accent-primary);
}

.tab-title {
  font-size: 13px;
  font-weight: 500;
}

.modified-indicator {
  color: var(--accent-primary);
  font-size: 8px;
}

.tab-close {
  padding: 2px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-xs);
  opacity: 0.7;
  transition: var(--transition-fast);
}

.tab-close:hover {
  opacity: 1;
  background-color: var(--bg-hover);
}

.tab-new {
  padding: 6px 10px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: var(--transition-fast);
}

.tab-new:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

/* Tab内容 */
.tab-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tab-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 工具栏 */
.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
  gap: var(--spacing-md);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.connection-select,
.database-select {
  padding: 4px 8px;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-size: 13px;
}

.transaction-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 4px 8px;
  background-color: var(--warning-bg);
  border: 1px solid var(--warning-border);
  border-radius: var(--radius-sm);
  color: var(--warning);
  font-size: 12px;
}

.warning-icon {
  color: var(--warning);
}

.btn {
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-xs);
  cursor: pointer;
  font-size: 13px;
  transition: var(--transition-fast);
  display: flex;
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

.btn-danger {
  background-color: var(--btn-danger-bg);
  color: var(--btn-danger-text);
}

.btn-danger:hover:not(:disabled) {
  background-color: var(--btn-danger-hover);
}

.btn-compact {
  padding: 4px 8px;
}

.btn-icon {
  padding: 4px;
}

.btn-text {
  font-size: 12px;
}

/* 编辑器区域 */
.editor-section {
  flex: none;  /* 不使用flex-grow */
  overflow: hidden;  /* 防止溢出 */
  min-height: 100px;  /* 最小高度 */
  max-height: 70vh;  /* 最大高度 */
  display: flex;
  flex-direction: column;
}

.editor-wrapper {
  flex: 1;
  border-bottom: 1px solid var(--border-primary);
  overflow: auto;  /* 内容滚动 */
}

/* 拖拽手柄 */
.resize-handle {
  height: 4px;
  background: var(--border-primary);
  cursor: ns-resize;
  flex: none;
  position: relative;
  transition: background-color 0.2s;
}

.resize-handle:hover {
  background: var(--primary-color);
  height: 6px;
}

.resize-handle.resizing {
  background: var(--primary-color);
  height: 6px;
}

.resize-handle::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 30px;
  height: 2px;
  background: var(--text-tertiary);
  border-radius: 1px;
  opacity: 0.5;
}

.resize-handle:hover::before {
  opacity: 1;
}

/* 结果区域 */
.results-section {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 错误信息 */
.error-section {
  padding: var(--spacing-md);
  background-color: var(--error-bg);
  border-bottom: 1px solid var(--error-border);
  flex-shrink: 0;
}

.error-section h4 {
  margin: 0 0 var(--spacing-sm) 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--error);
  font-size: 14px;
}

.error-message {
  background-color: var(--bg-primary);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-xs);
  padding: var(--spacing-sm);
  margin: 0 0 var(--spacing-sm) 0;
  color: var(--error);
  font-family: var(--font-family-mono);
  font-size: 12px;
  overflow: auto;
  max-height: 150px;
}

.error-diagnosis {
  display: flex;
  gap: var(--spacing-sm);
}

.ai-diagnose-btn {
  background-color: var(--info-bg);
  color: var(--info);
  border: 1px solid var(--info-border);
}

.ai-diagnose-btn:hover:not(:disabled) {
  background-color: var(--info);
  color: white;
}
</style>