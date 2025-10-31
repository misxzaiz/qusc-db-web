<template>
  <div class="query-history">
    <div v-if="history.length === 0" class="empty-history">
      <font-awesome-icon icon="history" />
      <p>暂无查询历史</p>
    </div>

    <div v-else class="history-list">
      <div
        v-for="(item, index) in history"
        :key="item.id"
        class="history-item"
        :class="{ active: selectedIndex === index }"
        @click="selectHistory(index)"
      >
        <div class="history-header">
          <span class="timestamp">{{ formatTime(item.timestamp) }}</span>
          <div class="history-actions">
            <button
              class="btn btn-icon btn-small"
              @click.stop="copyToEditor(item.sql)"
              title="复制到编辑器"
            >
              <font-awesome-icon icon="copy" />
            </button>
            <button
              class="btn btn-icon btn-small"
              @click.stop="executeHistory(item.sql)"
              title="执行"
            >
              <font-awesome-icon icon="play" />
            </button>
            <button
              class="btn btn-icon btn-small btn-danger"
              @click.stop="deleteHistory(index)"
              title="删除"
            >
              <font-awesome-icon icon="trash" />
            </button>
          </div>
        </div>
        <div class="history-sql">
          <pre>{{ item.sql }}</pre>
        </div>
        <div v-if="item.result" class="history-result">
          <span class="result-info">
            {{ item.result.data ? `${item.result.data.length} 行` : `${item.result.affectedRows} 行受影响` }}
          </span>
          <span v-if="item.result.error" class="error-text">
            错误
          </span>
        </div>
      </div>
    </div>

    <!-- 清空历史按钮 -->
    <div v-if="history.length > 0" class="clear-actions">
      <button class="btn btn-small" @click="clearAllHistory">
        <font-awesome-icon icon="trash" />
        清空历史
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'QueryHistory',

  data() {
    return {
      history: [],
      selectedIndex: -1
    }
  },

  mounted() {
    this.loadHistory()
  },

  methods: {
    loadHistory() {
      try {
        const saved = localStorage.getItem('query_history')
        if (saved) {
          this.history = JSON.parse(saved)
        }
      } catch (error) {
        console.error('加载查询历史失败:', error)
      }
    },

    saveHistory() {
      try {
        localStorage.setItem('query_history', JSON.stringify(this.history))
      } catch (error) {
        console.error('保存查询历史失败:', error)
      }
    },

    addToHistory(sql, result) {
      const historyItem = {
        id: Date.now(),
        sql: sql,
        timestamp: new Date().toISOString(),
        result: result
      }

      this.history.unshift(historyItem)

      // 限制历史记录数量
      if (this.history.length > 100) {
        this.history = this.history.slice(0, 100)
      }

      this.saveHistory()
    },

    selectHistory(index) {
      this.selectedIndex = index
      const item = this.history[index]

      // 发送事件给父组件
      this.$emit('select', item.sql)
    },

    copyToEditor(sql) {
      this.$emit('copy', sql)
    },

    async executeHistory(sql) {
      this.$emit('execute', sql)
    },

    deleteHistory(index) {
      this.history.splice(index, 1)
      if (this.selectedIndex === index) {
        this.selectedIndex = -1
      } else if (this.selectedIndex > index) {
        this.selectedIndex--
      }
      this.saveHistory()
    },

    clearAllHistory() {
      if (!confirm('确定要清空所有查询历史吗？')) return

      this.history = []
      this.selectedIndex = -1
      this.saveHistory()
      this.$emit('clear')
    },

    formatTime(timestamp) {
      const date = new Date(timestamp)
      const now = new Date()
      const diff = now - date

      // 小于1分钟
      if (diff < 60000) {
        return '刚刚'
      }

      // 小于1小时
      if (diff < 3600000) {
        return `${Math.floor(diff / 60000)} 分钟前`
      }

      // 小于24小时
      if (diff < 86400000) {
        return `${Math.floor(diff / 3600000)} 小时前`
      }

      // 超过24小时显示具体时间
      return date.toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
      })
    }
  }
}
</script>

<style scoped>
.query-history {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.empty-history {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  text-align: center;
  padding: var(--spacing-xl);
}

.empty-history svg {
  font-size: 48px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

.empty-history p {
  margin: 0;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

.history-item {
  padding: var(--spacing-sm);
  border-radius: var(--radius-sm);
  margin-bottom: var(--spacing-sm);
  background-color: var(--bg-tertiary);
  cursor: pointer;
  transition: var(--transition-fast);
  border: 1px solid transparent;
}

.history-item:hover {
  background-color: var(--bg-hover);
  border-color: var(--border-secondary);
}

.history-item.active {
  background-color: var(--accent-primary);
  border-color: var(--accent-primary);
  color: white;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-xs);
}

.timestamp {
  font-size: 12px;
  color: var(--text-secondary);
}

.history-item.active .timestamp {
  color: rgba(255, 255, 255, 0.8);
}

.history-actions {
  display: flex;
  gap: var(--spacing-xs);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.history-item:hover .history-actions,
.history-item.active .history-actions {
  opacity: 1;
}

.history-sql {
  font-family: var(--font-family-mono);
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xs);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60px;
  overflow: hidden;
}

.history-item.active .history-sql {
  color: rgba(255, 255, 255, 0.9);
}

.history-result {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 12px;
}

.result-info {
  color: var(--text-tertiary);
}

.history-item.active .result-info {
  color: rgba(255, 255, 255, 0.7);
}

.error-text {
  color: var(--error);
  font-weight: 500;
}

.clear-actions {
  padding: var(--spacing-sm);
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: center;
}

.btn-danger {
  background-color: var(--btn-danger-bg);
  color: var(--btn-danger-text);
}

.btn-danger:hover {
  background-color: var(--btn-danger-hover);
}
</style>