<template>
  <div class="result-tabs">
    <!-- 批量执行结果 -->
    <BatchExecutionResults
      v-if="isBatchExecutionTab"
      :results="batchResults"
      @export="handleBatchExport"
    />

    <!-- 普通Tab导航 -->
    <div v-if="results.length > 0 && !isBatchExecutionTab" class="tab-navigation">
      <div class="tab-list">
        <button
          v-for="(result, index) in results"
          :key="index"
          class="tab-button"
          :class="{
            active: activeTabIndex === index,
            error: result.error,
            'has-data': result.type === 'select' && result.data && result.data.length > 0
          }"
          @click="switchTab(index)"
          :title="getTabTitle(result, index)"
        >
          <span class="tab-icon">
            <font-awesome-icon
              :icon="getTabIcon(result)"
              :class="getTabIconClass(result)"
            />
          </span>
          <span class="tab-label">
            {{ getTabLabel(result, index) }}
          </span>
          <span v-if="result.modified" class="modified-dot">●</span>
          <span class="tab-info" v-if="result.type === 'select'">
            {{ result.data ? result.data.length : 0 }}行
          </span>
        </button>
      </div>
      <div class="tab-actions">
        <button
          v-if="results.length > 1"
          class="tab-action-btn"
          @click="closeAllResults"
          title="关闭所有结果"
        >
          <font-awesome-icon icon="times" />
        </button>
      </div>
    </div>

    <!-- 普通Tab内容 -->
    <div v-if="results.length > 0 && !isBatchExecutionTab" class="tab-content">
      <div v-for="(result, index) in results" :key="index" v-show="activeTabIndex === index" class="tab-pane">
        <!-- SELECT结果表格 -->
        <div v-if="result.type === 'select'" class="select-result">
          <div v-if="result.data && result.data.length > 0" class="result-table-wrapper">
            <!-- 工具栏 -->
            <div class="table-toolbar">
              <div class="toolbar-left">
                <h4 class="result-title">
                  <font-awesome-icon :icon="getResultIcon(result)" />
                  {{ getResultTitle(result, index) }}
                </h4>
                <div class="result-meta">
                  <span v-if="result.executionTime" class="execution-time">
                    <font-awesome-icon icon="clock" />
                    {{ result.executionTime }}ms
                  </span>
                  <span v-if="result.totalCount" class="total-count">
                    <font-awesome-icon icon="table" />
                    总计 {{ result.totalCount }} 行
                  </span>
                  <span class="record-info">
                    显示 {{ result.data.length }} 条
                  </span>
                </div>
              </div>
              <div class="toolbar-right">
                <button
                  class="btn btn-small"
                  @click="refreshData(result, index)"
                  title="刷新数据"
                >
                  <font-awesome-icon icon="refresh" />
                </button>
                <button
                  class="btn btn-small"
                  @click="$emit('analyze', { result, tabIndex: index })"
                  :disabled="result.analyzing"
                >
                  <font-awesome-icon icon="brain" />
                  {{ result.analyzing ? '分析中...' : 'AI解析' }}
                </button>
                <button
                  class="btn btn-small"
                  @click="$emit('export', result)"
                >
                  <font-awesome-icon icon="download" />
                  导出
                </button>
              </div>
            </div>

            <!-- 表格 -->
            <div class="table-container" ref="tableContainer">
              <table class="result-table">
                <thead>
                  <tr>
                    <th
                      v-for="column in result.columns"
                      :key="column"
                      @click="sortColumn(result, column)"
                      :class="{ sorted: result.sortColumn === column }"
                    >
                      <div class="sortable">
                        <span>{{ column }}</span>
                        <font-awesome-icon
                          v-if="result.sortColumn === column"
                          :icon="result.sortOrder === 'asc' ? 'sort-up' : 'sort-down'"
                          class="sort-icon"
                        />
                        <font-awesome-icon
                          v-else
                          icon="sort"
                          class="sort-icon inactive"
                        />
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(row, rowIndex) in getPaginatedData(result)"
                    :key="rowIndex"
                    :class="{ selected: result.selectedRows && result.selectedRows.has(rowIndex) }"
                    @click="toggleRowSelection(result, rowIndex)"
                  >
                    <td v-for="column in result.columns" :key="column" :class="{ 'null-value': row[column] === null }">
                      <div class="cell-wrapper">
                        <span class="cell-content" :title="formatCellValue(row[column])">{{ formatCellValue(row[column]) }}</span>
                        <div class="cell-actions" v-if="row[column] !== null && row[column] !== undefined">
                          <button
                            class="cell-action-btn"
                            @click.stop="copyCellValue(row[column])"
                            title="复制"
                          >
                            <font-awesome-icon icon="copy" />
                          </button>
                          <button
                            class="cell-action-btn expand-btn"
                            @click.stop="expandCellValue(row[column])"
                            title="展开查看"
                            v-if="String(row[column]).length > 50"
                          >
                            <font-awesome-icon icon="expand" />
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- 分页 -->
            <div v-if="needPagination(result)" class="pagination-wrapper">
              <div class="pagination-info">
                <span>第 {{ result.currentPage || 1 }} 页，共 {{ getTotalPages(result) }} 页</span>
              </div>
              <div class="pagination-controls">
                <!-- 加载状态遮罩 -->
                <div v-if="result.loading" class="pagination-loading">
                  <font-awesome-icon icon="spinner" spin />
                  <span>加载中...</span>
                </div>

                <button
                  class="btn btn-small"
                  @click="goToPage(result, 1)"
                  :disabled="result.currentPage === 1 || result.loading"
                >
                  首页
                </button>
                <button
                  class="btn btn-small"
                  @click="goToPage(result, (result.currentPage || 1) - 1)"
                  :disabled="result.currentPage === 1 || result.loading"
                >
                  <font-awesome-icon icon="angle-left" />
                </button>

                <!-- 页码 -->
                <div class="page-numbers">
                  <button
                    v-for="page in getVisiblePages(result)"
                    :key="page"
                    class="btn btn-small"
                    :class="{ active: page === (result.currentPage || 1) }"
                    @click="goToPage(result, page)"
                    :disabled="result.loading"
                  >
                    {{ page }}
                  </button>
                </div>

                <button
                  class="btn btn-small"
                  @click="goToPage(result, (result.currentPage || 1) + 1)"
                  :disabled="result.currentPage === getTotalPages(result) || result.loading"
                >
                  <font-awesome-icon icon="angle-right" />
                </button>
                <button
                  class="btn btn-small"
                  @click="goToPage(result, getTotalPages(result))"
                  :disabled="result.currentPage === getTotalPages(result) || result.loading"
                >
                  末页
                </button>

                <!-- 每页大小 -->
                <select
                  v-model="result.pageSize"
                  @change="changePageSize(result)"
                  class="page-size-select"
                  :disabled="result.loading"
                >
                  <option value="20">20条/页</option>
                  <option value="50">50条/页</option>
                  <option value="100">100条/页</option>
                  <option value="200">200条/页</option>
                </select>
              </div>
            </div>
          </div>
          <div v-else class="empty-result">
            <font-awesome-icon icon="inbox" />
            <span>查询成功，但没有返回数据</span>
          </div>
        </div>

        <!-- UPDATE/INSERT/DELETE结果 -->
        <div v-else-if="result.type === 'update'" class="update-result">
          <div class="update-toolbar">
            <div class="toolbar-left">
              <h4 class="result-title">
                <font-awesome-icon :icon="getResultIcon(result)" />
                {{ getResultTitle(result, index) }}
              </h4>
              <div class="result-meta">
                <span v-if="result.executionTime" class="execution-time">
                  <font-awesome-icon icon="clock" />
                  {{ result.executionTime }}ms
                </span>
                <span class="affected-rows">
                  <font-awesome-icon icon="edit" />
                  {{ result.affectedRows }} 行受影响
                </span>
              </div>
            </div>
            <div class="toolbar-right">
              <button
                class="btn btn-small"
                @click="refreshData(result, index)"
                title="刷新数据"
              >
                <font-awesome-icon icon="refresh" />
              </button>
              <button
                class="btn btn-small"
                @click="$emit('export', result)"
              >
                <font-awesome-icon icon="download" />
                导出
              </button>
            </div>
          </div>
          <div class="success-content">
            <font-awesome-icon icon="check-circle" />
            <span>执行成功</span>
          </div>
        </div>

        <!-- 错误结果 -->
        <div v-else-if="result.error" class="error-result">
          <div class="error-toolbar">
            <div class="toolbar-left">
              <h4 class="result-title">
                <font-awesome-icon :icon="getResultIcon(result)" />
                {{ getResultTitle(result, index) }}
              </h4>
              <div class="result-meta">
                <span v-if="result.executionTime" class="execution-time">
                  <font-awesome-icon icon="clock" />
                  {{ result.executionTime }}ms
                </span>
                <span class="error-info">
                  <font-awesome-icon icon="exclamation-triangle" />
                  执行错误
                </span>
              </div>
            </div>
            <div class="toolbar-right">
              <button
                class="btn btn-small"
                @click="refreshData(result, index)"
                title="刷新数据"
              >
                <font-awesome-icon icon="refresh" />
              </button>
              <button
                class="btn btn-small"
                @click="$emit('export', result)"
              >
                <font-awesome-icon icon="download" />
                导出
              </button>
            </div>
          </div>
          <div class="error-content">
            <pre class="error-message">{{ result.error }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 无结果提示 - 只在非批量执行且没有结果时显示 -->
    <div v-if="!isBatchExecutionTab && results.length === 0" class="no-results">
      <font-awesome-icon icon="inbox" />
      <span>暂无查询结果</span>
      <p class="hint">执行SQL后，结果将在这里显示</p>
    </div>

    <!-- 数据展开弹窗 -->
    <div v-if="showExpandModal" class="modal-overlay" @click="closeExpandModal">
      <div class="modal-container" @click.stop>
        <div class="modal-header">
          <h4>单元格数据</h4>
          <button class="modal-close" @click="closeExpandModal">
            <font-awesome-icon icon="times" />
          </button>
        </div>
        <div class="modal-body">
          <div class="expand-content">
            {{ expandedValue }}
          </div>
          <div class="modal-actions">
            <button class="btn btn-primary" @click="copyCellValue(expandedValue)">
              <font-awesome-icon icon="copy" />
              复制
            </button>
            <button class="btn" @click="closeExpandModal">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue'
import BatchExecutionResults from './BatchExecutionResults.vue'

export default {
  name: 'ResultTabs',

  components: {
    BatchExecutionResults
  },

  props: {
    results: {
      type: Array,
      default: () => []
    },
    isBatchExecution: {
      type: Boolean,
      default: false
    },
    batchResults: {
      type: Array,
      default: () => []
    }
  },

  emits: ['analyze', 'export', 'refresh'],

  setup(props, { emit }) {
    const activeTabIndex = ref(0)
    const tableContainer = ref(null)
    const showExpandModal = ref(false)
    const expandedValue = ref('')

    // 是否为批量执行Tab
    const isBatchExecutionTab = computed(() => {
      return props.isBatchExecution
    })

    // 批量导出处理
    const handleBatchExport = (results) => {
      emit('export', results)
    }

    // Tab切换
    const switchTab = (index) => {
      activeTabIndex.value = index
    }

    // 关闭单个结果
    const closeResult = (index) => {
      props.results.splice(index, 1)
      if (activeTabIndex.value >= props.results.length) {
        activeTabIndex.value = Math.max(0, props.results.length - 1)
      }
    }

    // 关闭所有结果
    const closeAllResults = () => {
      props.results.splice(0)
      activeTabIndex.value = 0
    }

    // 获取Tab标题
    const getTabTitle = (result, index) => {
      if (result.error) return '执行失败'
      if (result.type === 'select') {
        return `查询结果 - ${result.data ? result.data.length : 0}行`
      }
      if (result.type === 'update') {
        return `更新 - ${result.affectedRows}行受影响`
      }
      return `结果 ${index + 1}`
    }

    // 获取Tab标签
    const getTabLabel = (result, index) => {
      if (result.error) return `错误${index + 1}`
      if (result.type === 'select') return `查询${index + 1}`
      if (result.type === 'update') return `更新${index + 1}`
      return `结果${index + 1}`
    }

    // 获取Tab图标
    const getTabIcon = (result) => {
      if (result.error) return 'exclamation-triangle'
      if (result.type === 'select') return 'table'
      if (result.type === 'update') return 'edit'
      return 'file-alt'
    }

    // 获取Tab图标类
    const getTabIconClass = (result) => {
      if (result.error) return 'error'
      if (result.type === 'select') return 'select'
      if (result.type === 'update') return 'update'
      return ''
    }

    // 获取结果图标
    const getResultIcon = (result) => {
      return getTabIcon(result)
    }

    // 获取结果标题
    const getResultTitle = (result, index) => {
      return getTabTitle(result, index)
    }

    // 格式化单元格值
    const formatCellValue = (value) => {
      if (value === null) return 'NULL'
      if (value === undefined) return ''
      if (typeof value === 'object') return JSON.stringify(value)
      return String(value)
    }

    // 排序
    const sortColumn = (result, column) => {
      if (!result.data || result.data.length === 0) return

      // 切换排序方向
      if (result.sortColumn === column) {
        result.sortOrder = result.sortOrder === 'asc' ? 'desc' : 'asc'
      } else {
        result.sortColumn = column
        result.sortOrder = 'asc'
      }

      // 前端排序
      result.data.sort((a, b) => {
        let aVal = a[column]
        let bVal = b[column]

        // 处理null值
        if (aVal === null) aVal = ''
        if (bVal === null) bVal = ''

        // 数字比较
        if (!isNaN(aVal) && !isNaN(bVal)) {
          return result.sortOrder === 'asc' ? aVal - bVal : bVal - aVal
        }

        // 字符串比较
        aVal = String(aVal).toLowerCase()
        bVal = String(bVal).toLowerCase()

        if (result.sortOrder === 'asc') {
          return aVal < bVal ? -1 : aVal > bVal ? 1 : 0
        } else {
          return aVal > bVal ? -1 : aVal < bVal ? 1 : 0
        }
      })

      // 重置到第一页
      result.currentPage = 1
    }

    // 获取当前页数据数量
    const getCurrentPageCount = (result) => {
      return result.data ? result.data.length : 0
    }

    // 分页相关 - 使用后端分页数据
    const needPagination = (result) => {
      // 使用后端返回的总数判断
      return result.totalCount && result.totalCount > (result.pageSize || 20)
    }

    const getTotalPages = (result) => {
      // 使用后端计算的总页数
      if (result.totalPages) return result.totalPages
      const pageSize = result.pageSize || 20
      return Math.ceil((result.totalCount || 0) / pageSize)
    }

    const getPaginatedData = (result) => {
      // 直接使用后端返回的数据，不需要前端分页
      return result.data || []
    }

    const getVisiblePages = (result) => {
      const total = getTotalPages(result)
      const current = result.currentPage || 1
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

    const goToPage = async (result, page) => {
      const total = getTotalPages(result)
      if (page >= 1 && page <= total) {
        // 触发事件，让父组件从后端获取新页数据
        emit('page-change', {
          resultId: result.id,
          page: page,
          pageSize: result.pageSize || 20
        })
      }
    }

    const changePageSize = async (result) => {
      // 触发事件，让父组件从后端获取新数据
      emit('page-change', {
        resultId: result.id,
        page: 1,
        pageSize: result.pageSize || 20
      })
    }

    // 刷新数据
    const refreshData = (result, index) => {
      emit('refresh', { result, tabIndex: index })
    }

    // 行选择
    const toggleRowSelection = (result, rowIndex) => {
      if (!result.selectedRows) {
        result.selectedRows = new Set()
      }

      if (result.selectedRows.has(rowIndex)) {
        result.selectedRows.delete(rowIndex)
      } else {
        result.selectedRows.add(rowIndex)
      }
    }

    // 复制单元格值
    const copyCellValue = async (value) => {
      try {
        const text = formatCellValue(value)
        await navigator.clipboard.writeText(text)
        // 可以添加提示信息
      } catch (err) {
        console.error('复制失败:', err)
      }
    }

    // 展开单元格值
    const expandCellValue = (value) => {
      expandedValue.value = formatCellValue(value)
      showExpandModal.value = true
    }

    // 关闭展开弹窗
    const closeExpandModal = () => {
      showExpandModal.value = false
      expandedValue.value = ''
    }

    return {
      activeTabIndex,
      tableContainer,
      showExpandModal,
      expandedValue,
      isBatchExecutionTab,
      handleBatchExport,
      switchTab,
      closeResult,
      closeAllResults,
      getTabTitle,
      getTabLabel,
      getTabIcon,
      getTabIconClass,
      getResultIcon,
      getResultTitle,
      formatCellValue,
      sortColumn,
      getCurrentPageCount,
      needPagination,
      getTotalPages,
      getPaginatedData,
      getVisiblePages,
      goToPage,
      changePageSize,
      refreshData,
      toggleRowSelection,
      copyCellValue,
      expandCellValue,
      closeExpandModal
    }
  }
}
</script>

<style scoped>
.result-tabs {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

/* Tab导航 */
.tab-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  height: 36px;
  flex-shrink: 0;
}

.tab-list {
  display: flex;
  gap: 2px;
  overflow-x: auto;
  flex: 1;
}

.tab-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  transition: var(--transition-fast);
  white-space: nowrap;
  font-size: 13px;
  position: relative;
}

.tab-button:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.tab-button.active {
  background-color: var(--bg-primary);
  color: var(--accent-primary);
  border-bottom: 2px solid var(--accent-primary);
}

.tab-button.error {
  color: var(--error);
}

.tab-button.error.active {
  color: var(--error);
}

.tab-icon {
  font-size: 12px;
}

.tab-icon.error {
  color: var(--error);
}

.tab-icon.select {
  color: var(--success);
}

.tab-icon.update {
  color: var(--warning);
}

.tab-label {
  font-weight: 500;
}

.modified-dot {
  color: var(--accent-primary);
  font-size: 10px;
}

.tab-info {
  font-size: 11px;
  color: var(--text-muted);
  background-color: var(--bg-secondary);
  padding: 2px 6px;
  border-radius: var(--radius-xs);
}

.tab-actions {
  display: flex;
  gap: 4px;
}

.tab-action-btn {
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: var(--radius-xs);
  transition: var(--transition-fast);
}

.tab-action-btn:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

/* Tab内容 */
.tab-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-height: 100%;
}

.tab-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

/* 执行时间样式 */
.execution-time {
  color: var(--info);
}

.affected-rows {
  color: var(--warning);
}

.total-count {
  color: var(--success);
}

/* 表格工具栏 */
.table-toolbar,
.update-toolbar,
.error-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-xs) var(--spacing-md);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  height: 32px;
  flex-shrink: 0;
  gap: var(--spacing-md);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex: 1;
  min-width: 0;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
}

.result-title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  font-size: 11px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.record-info {
  font-size: 11px;
  color: var(--text-secondary);
}

/* 表格容器 */
.result-table-wrapper {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-container {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--border-primary);
  background-color: var(--bg-primary);
  position: relative;
}

.table-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.table-container::-webkit-scrollbar-track {
  background: var(--bg-tertiary);
}

.table-container::-webkit-scrollbar-thumb {
  background: var(--border-primary);
  border-radius: 4px;
}

.table-container::-webkit-scrollbar-thumb:hover {
  background: var(--text-muted);
}

.result-table {
  width: auto;
  min-width: 100%;
  font-size: 13px;
  border-collapse: collapse;
  table-layout: auto;
}

.result-table th,
.result-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-secondary);
  white-space: nowrap;
  min-width: 120px;
  max-width: 300px;
  position: relative;
}

.result-table th {
  background-color: var(--bg-tertiary);
  font-weight: 600;
  color: var(--text-primary);
  position: sticky;
  top: 0;
  z-index: 10;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}

.result-table th .sortable {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.sort-icon {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
  margin-left: 8px;
}

.sort-icon.inactive {
  opacity: 0.3;
}

.result-table tbody tr:hover {
  background-color: var(--bg-hover);
}

.result-table tbody tr.selected {
  background-color: var(--accent-bg);
}

.result-table td {
  vertical-align: middle;
}

.result-table td .cell-content {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-table tbody tr:nth-child(even) {
  background-color: var(--bg-tertiary);
}

.result-table tbody tr:hover {
  background-color: var(--bg-hover);
}

.result-table tbody tr.selected {
  background-color: var(--accent-primary);
  color: white;
}

.result-table td.null-value {
  color: var(--text-muted);
  font-style: italic;
}

/* 单元格操作 */
.cell-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 24px;
}

.cell-content {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cell-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}

.result-table td:hover .cell-actions {
  opacity: 1;
}

.cell-action-btn {
  padding: 2px 6px;
  border: none;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 2px;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

.cell-action-btn:hover {
  background: var(--accent-primary);
  color: white;
}

.expand-btn {
  margin-left: 2px;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-xs) var(--spacing-md);
  background-color: var(--bg-tertiary);
  border-top: 1px solid var(--border-primary);
  height: 32px;
  flex-shrink: 0;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.page-numbers {
  display: flex;
  gap: 2px;
}

.page-size-select {
  padding: 4px 8px;
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-xs);
  font-size: 12px;
}

/* 其他结果类型 */
.select-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.update-result,
.error-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.success-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  font-size: 16px;
  color: var(--success);
  padding: var(--spacing-xl);
  flex: 1;
}

.error-content {
  flex: 1;
  padding: var(--spacing-md);
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-message {
  background-color: var(--error-bg);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-md);
  color: var(--error);
  font-family: var(--font-family-mono);
  font-size: 12px;
  overflow: auto;
  max-width: 100%;
  max-height: 100%;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-info {
  color: var(--error);
}

.empty-result {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.empty-result svg {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 无结果提示 */
.no-results {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.no-results svg {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  opacity: 0.3;
}

.no-results span {
  font-size: 16px;
  margin-bottom: var(--spacing-sm);
}

.no-results .hint {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

/* 数据展开弹窗 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-container {
  background: var(--bg-primary);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  max-width: 600px;
  width: 90%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-primary);
}

.modal-header h4 {
  margin: 0;
  color: var(--text-primary);
}

.modal-close {
  background: none;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-xs);
}

.modal-close:hover {
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.modal-body {
  padding: var(--spacing-md);
  flex: 1;
  display: flex;
  flex-direction: column;
}

.expand-content {
  background: var(--bg-secondary);
  padding: var(--spacing-md);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-primary);
  font-family: monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  margin-bottom: var(--spacing-md);
  flex: 1;
}

.modal-actions {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}

/* 分页加载状态 */
.pagination-loading {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-primary);
  z-index: 10;
}

.pagination-loading svg {
  font-size: 16px;
  color: var(--accent-primary);
}

.pagination-controls {
  position: relative;
}
</style>