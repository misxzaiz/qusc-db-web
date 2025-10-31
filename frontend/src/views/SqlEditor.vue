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
      <div class="editor-section">
        <div class="editor-toolbar">
          <div class="toolbar-left">
            <button class="btn btn-primary btn-compact" @click="executeSql" :disabled="!currentSession || !sqlText.trim()">
              <font-awesome-icon icon="play" />
              <span class="btn-text">执行</span>
            </button>
            <button class="btn btn-secondary btn-compact" @click="clearEditor">
              <font-awesome-icon icon="times" />
              <span class="btn-text">清空</span>
            </button>
            <button class="btn btn-secondary btn-compact" @click="formatSql">
              <font-awesome-icon icon="code" />
              <span class="btn-text">格式</span>
            </button>
          </div>
          <div class="toolbar-right">
            <div v-if="currentSession" class="connection-info">
              <span class="info-item">
                <span class="label">连接:</span>
                <span class="value">{{ currentSession.connectionInfo.name }}</span>
              </span>
              <span class="info-item">
                <span class="label">数据库:</span>
                <span class="value">{{ currentDatabase || '未选择' }}</span>
              </span>
            </div>
            <span v-if="queryResult" class="result-info">
              {{ queryResult.data ? `${queryResult.data.length} 行` : `${queryResult.affectedRows} 行受影响` }}
            </span>
          </div>
        </div>

        <div class="editor-wrapper">
          <SqlCodeEditor
            ref="codeEditor"
            v-model="sqlText"
            @execute="executeSql"
          />
        </div>
      </div>

      <div v-if="error" class="error-section">
        <h4>
          <font-awesome-icon icon="exclamation-triangle" />
          错误信息
        </h4>
        <pre class="error-message">{{ error }}</pre>
      </div>

      <div v-if="queryResult" class="result-section">
        <div class="result-header">
          <h4>查询结果</h4>
          <div class="result-actions">
            <button class="btn btn-small" @click="exportResult">
              <font-awesome-icon icon="download" />
              导出
            </button>
          </div>
        </div>
        <div class="result-table-wrapper">
          <table v-if="queryResult.data && queryResult.data.length > 0" class="result-table">
            <thead>
              <tr>
                <th v-for="column in queryResult.columns" :key="column" @click="sortResult(column)">
                  {{ column }}
                  <font-awesome-icon
                    :icon="sortColumn === column ? (sortOrder === 'asc' ? 'sort-up' : 'sort-down') : 'sort'"
                    class="sort-icon"
                  />
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, index) in paginatedData" :key="index">
                <td v-for="column in queryResult.columns" :key="column">
                  {{ formatCellValue(row[column]) }}
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else-if="queryResult.affectedRows !== undefined" class="update-result">
            <font-awesome-icon icon="check" class="success-icon" />
            执行成功，{{ queryResult.affectedRows }} 行受影响
          </div>
          <div v-else class="empty-result">
            查询无结果
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="queryResult.data && queryResult.data.length > pageSize" class="pagination">
          <button
            class="btn btn-small"
            @click="prevPage"
            :disabled="currentPage === 1"
          >
            <font-awesome-icon icon="angle-left" />
            上一页
          </button>
          <span class="page-info">
            第 {{ currentPage }} 页，共 {{ totalPages }} 页
          </span>
          <button
            class="btn btn-small"
            @click="nextPage"
            :disabled="currentPage === totalPages"
          >
            下一页
            <font-awesome-icon icon="angle-right" />
          </button>
        </div>
      </div>
    </div>

    <!-- 右侧AI边栏 -->
    <AiSidebar @use-sql="onUseAiSql" @resize="handleAiResize" />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
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
    // 响应式数据
    const sqlText = ref('')
    const currentSession = ref(null)
    const currentDatabase = ref(null)
    const queryResult = ref(null)
    const error = ref(null)
    const currentPage = ref(1)
    const pageSize = ref(100)
    const sortColumn = ref('')
    const sortOrder = ref('asc')

    // 侧边栏宽度
    const sidebarWidth = ref(40)
    const aiWidth = ref(40)

    // QueryHistory组件引用
    const queryHistoryRef = ref(null)

    // 计算属性
    const totalPages = computed(() => {
      if (!queryResult.value?.data) return 0
      return Math.ceil(queryResult.value.data.length / pageSize.value)
    })

    const paginatedData = computed(() => {
      if (!queryResult.value?.data) return []
      const start = (currentPage.value - 1) * pageSize.value
      const end = start + pageSize.value
      return queryResult.value.data.slice(start, end)
    })

    // 方法
    const executeSql = async () => {
      if (!currentSession.value || !sqlText.value.trim()) return

      error.value = null
      queryResult.value = null

      try {
        const sqlTextClean = sqlText.value.trim()
        const response = await fetch('/api/sql/execute', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            sessionId: currentSession.value.sessionId,
            sql: sqlTextClean
          })
        })

        const result = await response.json()
        if (result.error) {
          error.value = result.error
        } else {
          queryResult.value = result
          currentPage.value = 1

          // 保存到查询历史
          if (queryHistoryRef.value) {
            queryHistoryRef.value.addToHistory(sqlTextClean, result)
          }

          // 如果是USE语句，更新当前数据库
          if (sqlTextClean.toLowerCase().startsWith('use ')) {
            currentDatabase.value = sqlTextClean.substring(4).trim().replace(/[;`"'"]/g, '')
          }
        }
      } catch (e) {
        error.value = '执行失败: ' + e.message
      }
    }

    const clearEditor = () => {
      sqlText.value = ''
      error.value = null
    }

    const formatSql = () => {
      // 简单的SQL格式化
      let formatted = sqlText.value
        .replace(/\s+/g, ' ')
        .replace(/,/g, ',\n  ')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .trim()
      sqlText.value = formatted
    }

    const formatCellValue = (value) => {
      if (value === null) return 'NULL'
      if (value === undefined) return ''
      return String(value)
    }

    const sortResult = (column) => {
      if (sortColumn.value === column) {
        sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
      } else {
        sortColumn.value = column
        sortOrder.value = 'asc'
      }

      queryResult.value.data.sort((a, b) => {
        const aVal = a[column]
        const bVal = b[column]

        if (aVal === null) return 1
        if (bVal === null) return -1

        let comparison = 0
        if (aVal > bVal) comparison = 1
        if (aVal < bVal) comparison = -1

        return sortOrder.value === 'asc' ? comparison : -comparison
      })
    }

    const prevPage = () => {
      if (currentPage.value > 1) {
        currentPage.value--
      }
    }

    const nextPage = () => {
      if (currentPage.value < totalPages.value) {
        currentPage.value++
      }
    }

    const exportResult = () => {
      if (!queryResult.value?.data) return

      const csv = [
        queryResult.value.columns.join(','),
        ...queryResult.value.data.map(row =>
          queryResult.value.columns.map(col => {
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
      sqlText.value = sql
    }

    const handleSidebarResize = (width) => {
      sidebarWidth.value = width
    }

    const handleAiResize = (width) => {
      aiWidth.value = width
    }

    // 查询历史处理方法
    const onHistorySelect = (sql) => {
      sqlText.value = sql
    }

    const onHistoryCopy = (sql) => {
      navigator.clipboard.writeText(sql)
    }

    const onHistoryExecute = async (sql) => {
      sqlText.value = sql
      await executeSql()
    }

    const onHistoryClear = () => {
      // 可以在这里添加清空后的处理逻辑
      console.log('查询历史已清空')
    }

    const onQueryHistoryReady = (ref) => {
      queryHistoryRef.value = ref
    }

    // 键盘快捷键
    const handleKeydown = (e) => {
      if (e.ctrlKey && e.key === 'Enter') {
        executeSql()
      }
      if (e.key === 'F5') {
        e.preventDefault()
        executeSql()
      }
    }

    // 生命周期
    onMounted(() => {
      window.addEventListener('keydown', handleKeydown)
    })

    onUnmounted(() => {
      window.removeEventListener('keydown', handleKeydown)
    })

    // 监听连接状态
    watch(() => connectionStore.getActiveSessions(), (sessions) => {
      if (sessions.length > 0 && !currentSession.value) {
        currentSession.value = sessions[0]
        currentDatabase.value = sessions[0].currentDatabase
      }
    }, { immediate: true })

    // 返回所有需要在模板中使用的数据和方法
    return {
      sqlText,
      currentSession,
      currentDatabase,
      queryResult,
      error,
      currentPage,
      totalPages,
      paginatedData,
      sortColumn,
      sortOrder,
      sidebarWidth,
      aiWidth,
      queryHistoryRef,
      executeSql,
      clearEditor,
      formatSql,
      formatCellValue,
      sortResult,
      prevPage,
      nextPage,
      exportResult,
      handleSidebarResize,
      handleAiResize,
      onHistorySelect,
      onHistoryCopy,
      onHistoryExecute,
      onHistoryClear,
      onQueryHistoryReady,
      onUseAiSql
    }
  }
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
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

.editor-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  flex-shrink: 0;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.connection-info {
  display: flex;
  gap: 12px;
}

.info-item {
  display: flex;
  gap: 4px;
  font-size: 12px;
}

.info-item .label {
  color: var(--text-tertiary);
}

.info-item .value {
  color: var(--text-primary);
  font-weight: 500;
}

.result-info {
  color: var(--text-secondary);
  font-size: 12px;
}

.editor-wrapper {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

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

.result-section {
  background-color: var(--bg-secondary);
  border-top: 1px solid var(--border-primary);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
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
}

.result-actions {
  display: flex;
  gap: 5px;
}

.result-table-wrapper {
  flex: 1;
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

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 15px;
  padding: 10px;
  background-color: var(--bg-tertiary);
  border-top: 1px solid var(--border-primary);
}

.page-info {
  color: var(--text-secondary);
  font-size: 13px;
}

/* 紧凑按钮样式 */
.btn-compact {
  padding: 4px 8px !important;
  font-size: 12px !important;
}

.btn-compact .btn-text {
  margin-left: 4px;
}

.btn-compact svg {
  width: 12px !important;
  height: 12px !important;
}
</style>