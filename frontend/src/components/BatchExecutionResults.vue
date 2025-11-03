<template>
  <div class="batch-execution-results">
    <!-- 执行进度统计 -->
    <div class="execution-summary">
      <div class="summary-item">
        <span class="label">总条数:</span>
        <span class="value">{{ total }}</span>
      </div>
      <div class="summary-item success">
        <span class="label">成功:</span>
        <span class="value">{{ successCount }}</span>
      </div>
      <div class="summary-item error">
        <span class="label">失败:</span>
        <span class="value">{{ errorCount }}</span>
      </div>
      <div class="summary-item pending" v-if="pendingCount > 0">
        <span class="label">待执行:</span>
        <span class="value">{{ pendingCount }}</span>
      </div>
    </div>

    <!-- 结果Tab导航 -->
    <div v-if="results.length > 0" class="result-tabs-navigation" :class="{ 'not-scrollable': !needScroll }" ref="tabNavigation">
      <div class="result-tab-list" ref="tabList" @scroll="checkScroll" @keydown="handleKeydown" tabindex="0">
        <button
          v-for="(result, index) in results"
          :key="index"
          :ref="el => setTabRef(el, index)"
          class="result-tab-button"
          :class="{
            active: activeResultIndex === index,
            [result.status]: true
          }"
          @click="switchResultTab(index)"
          :title="getTabTooltip(result, index)"
        >
          <span class="tab-status">
            <font-awesome-icon
              v-if="result.status === 'pending'"
              icon="clock"
              class="status-icon pending"
            />
            <font-awesome-icon
              v-else-if="result.status === 'executing'"
              icon="spinner"
              spin
              class="status-icon executing"
            />
            <font-awesome-icon
              v-else-if="result.status === 'success'"
              icon="check-circle"
              class="status-icon success"
            />
            <font-awesome-icon
              v-else-if="result.status === 'error'"
              icon="exclamation-circle"
              class="status-icon error"
            />
            <font-awesome-icon
              v-else-if="result.status === 'cancelled'"
              icon="ban"
              class="status-icon cancelled"
            />
          </span>
          <span class="tab-label">SQL {{ index + 1 }}</span>
          <span v-if="result.executionTime" class="tab-time">{{ result.executionTime }}ms</span>
        </button>
      </div>
    </div>

    <!-- 结果内容 -->
    <div v-if="results.length > 0" class="result-content">
      <div v-if="activeResult" class="result-detail">
        <!-- SQL语句 -->
        <div class="sql-display">
          <h5>SQL语句:</h5>
          <pre>{{ activeResult.sql }}</pre>
        </div>

        <!-- 执行信息 -->
        <div class="execution-info">
          <span class="info-item status" :class="activeResult.status">
            状态: {{ getStatusText(activeResult.status) }}
          </span>
          <span v-if="activeResult.executionTime" class="info-item time">
            执行时间: {{ activeResult.executionTime }}ms
          </span>
        </div>

        <!-- 错误信息 -->
        <div v-if="activeResult.status === 'error'" class="error-content">
          <h5>错误信息:</h5>
          <pre class="error-message">{{ activeResult.error }}</pre>
        </div>

        <!-- 成功结果 -->
        <div v-if="activeResult.status === 'success' && activeResult.result" class="success-content">
          <div v-if="activeResult.result.type === 'select'" class="select-result">
            <h5>查询结果
              <span v-if="activeResult.result.totalCount" class="result-count">
                (共 {{ activeResult.result.totalCount }} 行)
              </span>
            </h5>
            <div class="result-table-wrapper">
              <table class="mini-result-table">
                <thead>
                  <tr>
                    <th v-for="col in activeResult.result.columns.slice(0, 5)" :key="col">
                      {{ col }}
                    </th>
                    <th v-if="activeResult.result.columns.length > 5" class="more-cols">
                      ...
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, idx) in activeResult.result.data.slice(0, 3)" :key="idx">
                    <td v-for="col in activeResult.result.columns.slice(0, 5)" :key="col">
                      {{ formatCellValue(row[col]) }}
                    </td>
                    <td v-if="activeResult.result.columns.length > 5" class="more-data">
                      ...
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="activeResult.result.data.length > 3" class="more-data-text">
                还有 {{ activeResult.result.data.length - 3 }} 行数据...
              </div>
            </div>
          </div>
          <div v-else-if="activeResult.result.type === 'update'" class="update-result">
            <h5>执行成功</h5>
            <p>影响了 {{ activeResult.result.affectedRows }} 行</p>
          </div>
          <div v-else class="other-result">
            <h5>执行成功</h5>
            <p>{{ activeResult.result.message || '操作完成' }}</p>
          </div>
        </div>

        <!-- 取消状态 -->
        <div v-if="activeResult.status === 'cancelled'" class="cancelled-content">
          <p>该SQL已被取消执行</p>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="batch-actions">
      <button class="btn btn-small" @click="exportResults" :disabled="!hasResults">
        <font-awesome-icon icon="download" />
        导出结果
      </button>
    </div>
  </div>
</template>

<script>
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'

export default {
  name: 'BatchExecutionResults',
  props: {
    results: {
      type: Array,
      required: true
    }
  },
  emits: ['export'],
  setup(props, { emit }) {
    const activeResultIndex = ref(0)
    const needScroll = ref(false)
    const tabRefs = ref([])
    let tabListEl = null
    let tabNavigationEl = null

    // Template refs
    const tabList = ref(null)
    const tabNavigation = ref(null)

    // Computed properties
    const total = computed(() => props.results.length)

    const successCount = computed(() => {
      return props.results.filter(r => r.status === 'success').length
    })

    const errorCount = computed(() => {
      return props.results.filter(r => r.status === 'error').length
    })

    const pendingCount = computed(() => {
      return props.results.filter(r => r.status === 'pending' || r.status === 'executing').length
    })

    const hasResults = computed(() => {
      return props.results.some(r => r.status === 'success' && r.result)
    })

    const activeResult = computed(() => {
      return props.results[activeResultIndex.value] || null
    })

    // Methods
    const switchResultTab = (index) => {
      activeResultIndex.value = index
      scrollToTab(index)
    }

    const setTabRef = (el, index) => {
      if (el) {
        tabRefs.value[index] = el
      }
    }

    const scrollToTab = (index) => {
      if (!tabListEl || !needScroll.value) return

      const tab = tabRefs.value[index]
      if (!tab) return

      const tabListRect = tabListEl.getBoundingClientRect()
      const tabRect = tab.getBoundingClientRect()

      // 计算滚动位置，让tab居中显示
      const scrollLeft = tabListEl.scrollLeft + (tabRect.left - tabListRect.left) - (tabListRect.width - tabRect.width) / 2

      tabListEl.scrollTo({
        left: scrollLeft,
        behavior: 'smooth'
      })
    }

    const scrollToActiveTab = () => {
      scrollToTab(activeResultIndex.value)
    }

    const checkNeedScroll = () => {
      if (!tabListEl || !tabNavigationEl) return

      const containerWidth = tabNavigationEl.clientWidth
      const contentWidth = tabListEl.scrollWidth

      needScroll.value = contentWidth > containerWidth
    }

    const checkScroll = () => {
      // 可以在这里添加滚动相关的额外逻辑
    }

    const handleKeydown = (event) => {
      if (props.results.length === 0) return

      let newIndex = activeResultIndex.value

      switch (event.key) {
        case 'ArrowLeft':
          event.preventDefault()
          newIndex = Math.max(0, activeResultIndex.value - 1)
          break
        case 'ArrowRight':
          event.preventDefault()
          newIndex = Math.min(props.results.length - 1, activeResultIndex.value + 1)
          break
        case 'Home':
          event.preventDefault()
          newIndex = 0
          break
        case 'End':
          event.preventDefault()
          newIndex = props.results.length - 1
          break
        default:
          return
      }

      if (newIndex !== activeResultIndex.value) {
        switchResultTab(newIndex)
      }
    }

    const getTabTooltip = (result, index) => {
      const status = getStatusText(result.status)
      const sqlPreview = getSqlPreview(result.sql)
      return `SQL ${index + 1} - ${status}\n${sqlPreview}`
    }

    const getStatusText = (status) => {
      const statusMap = {
        pending: '待执行',
        executing: '执行中',
        success: '成功',
        error: '失败',
        cancelled: '已取消'
      }
      return statusMap[status] || status
    }

    const getSqlPreview = (sql) => {
      if (!sql) return ''
      const preview = sql.split('\n')[0].trim()
      return preview.length > 80 ? preview.substring(0, 80) + '...' : preview
    }

    const formatCellValue = (value) => {
      if (value === null || value === undefined) return 'NULL'
      if (typeof value === 'string') return value
      return String(value)
    }

    const exportResults = () => {
      const successResults = props.results.filter(r => r.status === 'success')
      emit('export', successResults)
    }

    // Lifecycle hooks
    onMounted(() => {
      // 获取DOM元素引用
      tabListEl = tabList.value
      tabNavigationEl = tabNavigation.value

      nextTick(() => {
        checkNeedScroll()
      })

      // 监听窗口大小变化
      window.addEventListener('resize', checkNeedScroll)
    })

    onBeforeUnmount(() => {
      window.removeEventListener('resize', checkNeedScroll)
    })

    // Watchers
    watch(() => props.results, () => {
      nextTick(() => {
        checkNeedScroll()
        scrollToActiveTab()
      })
    }, { deep: true })

    return {
      activeResultIndex,
      needScroll,
      tabList,
      tabNavigation,
      total,
      successCount,
      errorCount,
      pendingCount,
      hasResults,
      activeResult,
      switchResultTab,
      setTabRef,
      scrollToTab,
      scrollToActiveTab,
      checkNeedScroll,
      checkScroll,
      handleKeydown,
      getTabTooltip,
      getStatusText,
      getSqlPreview,
      formatCellValue,
      exportResults
    }
  }
}
</script>

<style scoped>
.batch-execution-results {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: var(--bg-primary);
  border-radius: 8px;
  overflow: hidden;
}

.execution-summary {
  display: flex;
  gap: 20px;
  padding: 15px 20px;
  background-color: var(--bg-secondary);
  border-bottom: 1px solid var(--border-primary);
  flex-shrink: 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 14px;
}

.summary-item .label {
  color: var(--text-secondary);
  font-size: 12px;
}

.summary-item .value {
  font-weight: 600;
  font-size: 18px;
}

.summary-item.success .value {
  color: #28a745;
}

.summary-item.error .value {
  color: #dc3545;
}

.summary-item.pending .value {
  color: #ffc107;
}

/* 结果Tab导航 */
.result-tabs-navigation {
  flex-shrink: 0;
  border-bottom: 1px solid var(--border-primary);
  background-color: var(--bg-tertiary);
  position: relative;
}

.result-tab-list {
  display: flex;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 10px;
  gap: 2px;
  scroll-behavior: smooth;
  /* 隐藏滚动条但保持滚动功能 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
}

.result-tab-list::-webkit-scrollbar {
  display: none; /* Chrome/Safari/Opera */
}

.result-tab-button {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  font-size: 13px;
  border-bottom: 3px solid transparent;
  min-width: 120px;
  max-width: 180px;
  flex-shrink: 0;
  position: relative;
}

/* 添加左右渐变遮罩效果 */
.result-tabs-navigation::before,
.result-tabs-navigation::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 20px;
  pointer-events: none;
  z-index: 1;
  transition: opacity 0.3s ease;
}

.result-tabs-navigation::before {
  left: 0;
  background: linear-gradient(to right, var(--bg-tertiary), transparent);
}

.result-tabs-navigation::after {
  right: 0;
  background: linear-gradient(to left, var(--bg-tertiary), transparent);
}

/* 当不需要滚动时隐藏渐变 */
.result-tabs-navigation.not-scrollable::before,
.result-tabs-navigation.not-scrollable::after {
  opacity: 0;
}

/* 优化tab内容布局 */
.result-tab-button {
  justify-content: center;
}

.result-tab-button .tab-label {
  flex: 1;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-tab-button .tab-time {
  flex-shrink: 0;
  font-size: 10px;
}

.result-tab-button:hover {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
}

.result-tab-button.active {
  background-color: var(--bg-primary);
  color: var(--accent-primary);
  border-bottom-color: var(--accent-primary);
}

.result-tab-button.pending {
  color: #ffc107;
}

.result-tab-button.executing {
  color: #007bff;
}

.result-tab-button.success {
  color: #28a745;
}

.result-tab-button.error {
  color: #dc3545;
}

.result-tab-button.cancelled {
  color: #6c757d;
}

.tab-status {
  display: flex;
  align-items: center;
}

.status-icon {
  font-size: 14px;
}

.tab-label {
  font-weight: 500;
}

.tab-time {
  font-size: 11px;
  color: var(--text-muted);
  background-color: var(--bg-secondary);
  padding: 2px 6px;
  border-radius: var(--radius-xs);
}

/* 结果内容 */
.result-content {
  flex: 1;
  overflow: auto;
  padding: 20px;
}

.result-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* SQL显示 */
.sql-display {
  background-color: var(--bg-secondary);
  border-radius: 6px;
  padding: 15px;
}

.sql-display h5 {
  margin: 0 0 10px 0;
  color: var(--text-primary);
  font-size: 14px;
}

.sql-display pre {
  background-color: var(--bg-tertiary);
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: var(--font-family-mono, monospace);
  font-size: 13px;
  line-height: 1.4;
  color: var(--text-primary);
  margin: 0;
  white-space: pre-wrap;
}

/* 执行信息 */
.execution-info {
  display: flex;
  gap: 20px;
  padding: 10px 0;
}

.info-item {
  font-size: 13px;
}

.info-item.status {
  font-weight: 600;
}

.info-item.status.pending {
  color: #ffc107;
}

.info-item.status.executing {
  color: #007bff;
}

.info-item.status.success {
  color: #28a745;
}

.info-item.status.error {
  color: #dc3545;
}

.info-item.status.cancelled {
  color: #6c757d;
}

.info-item.time {
  color: var(--text-secondary);
}

/* 错误内容 */
.error-content h5 {
  color: #dc3545;
  margin: 0 0 10px 0;
  font-size: 14px;
}

.error-message {
  background-color: #f8d7da;
  color: #721c24;
  padding: 10px;
  border-radius: 4px;
  font-family: var(--font-family-mono, monospace);
  font-size: 13px;
  white-space: pre-wrap;
  margin: 0;
}

/* 成功内容 */
.success-content h5 {
  margin: 0 0 10px 0;
  font-size: 14px;
  color: var(--text-primary);
}

.result-count {
  font-weight: normal;
  color: var(--text-secondary);
  font-size: 13px;
}

.result-table-wrapper {
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  overflow: hidden;
}

.mini-result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.mini-result-table th,
.mini-result-table td {
  padding: 8px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border-primary);
}

.mini-result-table th {
  background-color: var(--bg-secondary);
  font-weight: 600;
}

.mini-result-table .more-cols,
.mini-result-table .more-data {
  color: var(--text-secondary);
  font-style: italic;
}

.more-data-text {
  padding: 10px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 13px;
}

.update-result p,
.other-result p {
  margin: 0;
  color: var(--text-primary);
}

.cancelled-content {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
}

/* 批量操作按钮 */
.batch-actions {
  display: flex;
  gap: 10px;
  padding: 15px 20px;
  background-color: var(--bg-secondary);
  border-top: 1px solid var(--border-primary);
  flex-shrink: 0;
}
</style>