<template>
  <div class="sql-confirm-overlay" @click.self="$emit('cancel')">
    <div class="sql-confirm-dialog">
      <!-- 头部 -->
      <div class="sql-confirm-header">
        <font-awesome-icon :icon="getRiskIcon()" :style="{ color: getRiskColor() }" />
        <h3>{{ getRiskTitle() }}</h3>
      </div>

      <!-- 内容 -->
      <div class="sql-confirm-body">
        <!-- SQL预览 -->
        <div class="sql-preview">
          <h4>即将执行的SQL：</h4>
          <pre>{{ formattedSql }}</pre>
        </div>

        <!-- 风险警告 -->
        <div class="sql-risk-warning" :class="riskLevel.toLowerCase()">
          <div class="warning-item">
            <font-awesome-icon icon="exclamation-triangle" />
            <span>{{ getRiskMessage() }}</span>
          </div>

          <!-- 影响行数预估 -->
          <div v-if="estimatedRows !== null" class="impact-estimate">
            <font-awesome-icon icon="info-circle" />
            <span>预计影响行数：{{ formatNumber(estimatedRows) }}</span>
          </div>

          <!-- 表信息提示 -->
          <div v-if="tableInfo" class="table-info">
            <font-awesome-icon icon="table" />
            <span>操作表：{{ tableInfo.name }} ({{ formatNumber(tableInfo.rows) }} 行)</span>
          </div>
        </div>

        <!-- 高危操作需要输入确认 -->
        <div v-if="requireTextConfirm" class="text-confirmation">
          <p>请输入 <code>{{ confirmText }}</code> 以确认操作：</p>
          <input
            v-model="userConfirmText"
            type="text"
            :placeholder="`输入 ${confirmText}`"
            @keyup.enter="confirmIfValid"
            ref="confirmInput"
          />
        </div>

        <!-- 二次确认复选框 -->
        <div v-if="requireDoubleConfirm" class="double-confirmation">
          <label class="checkbox">
            <input v-model="doubleConfirm" type="checkbox" />
            <span>我理解此操作不可逆，将继续执行</span>
          </label>
        </div>

        <!-- 执行选项 -->
        <div v-if="riskLevel !== 'LOW'" class="execution-options">
          <label class="checkbox">
            <input v-model="addToHistory" type="checkbox" checked />
            <span>添加到查询历史</span>
          </label>
          <label class="checkbox" v-if="isTransactionActive">
            <input v-model="commitAfter" type="checkbox" />
            <span>执行后自动提交事务</span>
          </label>
        </div>
      </div>

      <!-- 底部按钮 -->
      <div class="sql-confirm-footer">
        <button class="sql-confirm-btn sql-confirm-btn-cancel" @click="$emit('cancel')">
          <font-awesome-icon icon="times" />
          取消
        </button>
        <button
          class="sql-confirm-btn sql-confirm-btn-execute"
          :class="riskLevel.toLowerCase()"
          :disabled="!canConfirm"
          @click="confirm"
        >
          <font-awesome-icon :icon="getConfirmIcon()" />
          {{ getConfirmButtonText() }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SqlConfirmDialog',
  props: {
    sql: {
      type: String,
      required: true
    },
    riskLevel: {
      type: String,
      default: 'LOW',
      validator: value => ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].includes(value)
    },
    currentSessionId: {
      type: String,
      default: ''
    },
    currentDatabase: {
      type: String,
      default: ''
    },
    isTransactionActive: {
      type: Boolean,
      default: false
    }
  },
  emits: ['confirm', 'cancel'],
  data() {
    return {
      userConfirmText: '',
      doubleConfirm: false,
      addToHistory: true,
      commitAfter: false,
      estimatedRows: null,
      tableInfo: null,
      isLoading: false
    }
  },
  computed: {
    formattedSql() {
      // 简单格式化SQL
      return this.sql
        .replace(/\s+/g, ' ')
        .replace(/,/g, ',\n  ')
        .replace(/\bFROM\b/gi, '\nFROM')
        .replace(/\bWHERE\b/gi, '\nWHERE')
        .replace(/\bGROUP BY\b/gi, '\nGROUP BY')
        .replace(/\bORDER BY\b/gi, '\nORDER BY')
        .replace(/\bHAVING\b/gi, '\nHAVING')
        .trim()
    },

    requireTextConfirm() {
      return this.riskLevel === 'CRITICAL'
    },

    requireDoubleConfirm() {
      return this.riskLevel === 'CRITICAL' || this.riskLevel === 'HIGH'
    },

    confirmText() {
      if (this.riskLevel === 'CRITICAL') {
        if (this.sql.toUpperCase().includes('DELETE')) return 'DELETE'
        if (this.sql.toUpperCase().includes('DROP')) return 'DROP'
        if (this.sql.toUpperCase().includes('TRUNCATE')) return 'TRUNCATE'
        return 'CONFIRM'
      }
      return ''
    },

    canConfirm() {
      if (this.isLoading) return false
      if (this.riskLevel === 'LOW') return true
      if (this.requireTextConfirm && this.userConfirmText !== this.confirmText) return false
      if (this.requireDoubleConfirm && !this.doubleConfirm) return false
      return true
    }
  },
  mounted() {
    this.analyzeSql()
    if (this.requireTextConfirm) {
      this.$nextTick(() => {
        this.$refs.confirmInput?.focus()
      })
    }
  },
  methods: {
    getRiskIcon() {
      switch (this.riskLevel) {
        case 'LOW': return 'check-circle'
        case 'MEDIUM': return 'exclamation-triangle'
        case 'HIGH': return 'exclamation-circle'
        case 'CRITICAL': return 'radiation'
        default: return 'info-circle'
      }
    },

    getRiskColor() {
      switch (this.riskLevel) {
        case 'LOW': return '#28a745'
        case 'MEDIUM': return '#ffc107'
        case 'HIGH': return '#dc3545'
        case 'CRITICAL': return '#721c24'
        default: return '#007bff'
      }
    },

    getRiskTitle() {
      switch (this.riskLevel) {
        case 'LOW': return '安全确认'
        case 'MEDIUM': return '数据修改确认'
        case 'HIGH': return '结构变更确认'
        case 'CRITICAL': return '危险操作警告'
        default: return '执行确认'
      }
    },

    getRiskMessage() {
      switch (this.riskLevel) {
        case 'LOW': return '这是一个安全的只读操作'
        case 'MEDIUM': return '此操作将修改数据，请确认无误后执行'
        case 'HIGH': return '此操作将变更表结构，请确保已备份重要数据'
        case 'CRITICAL': return '警告：此操作不可逆，将永久删除数据或表结构！'
        default: return '请确认执行此操作'
      }
    },

    getConfirmIcon() {
      return this.riskLevel === 'CRITICAL' ? 'radiation' : 'play'
    },

    getConfirmButtonText() {
      switch (this.riskLevel) {
        case 'LOW': return '执行'
        case 'MEDIUM': return '确认执行'
        case 'HIGH': return '确认变更'
        case 'CRITICAL': return '强制执行'
        default: return '执行'
      }
    },

    formatNumber(num) {
      if (num === null || num === undefined) return '未知'
      if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
      if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
      return num.toString()
    },

    async analyzeSql() {
      // 对于UPDATE/DELETE，尝试预估影响行数
      if (this.sql.match(/UPDATE|DELETE/i)) {
        this.isLoading = true
        try {
          this.estimatedRows = await this.estimateImpact()
        } catch (e) {
          console.error('预估影响行数失败:', e)
        }
        this.isLoading = false
      }

      // 获取表信息
      this.tableInfo = await this.getTableInfo()
    },

    async estimateImpact() {
      // 这里应该调用dry run API来预估影响行数
      // 暂时返回模拟数据
      return Math.floor(Math.random() * 10000)
    },

    async getTableInfo() {
      // 这里应该调用API获取表信息
      // 暂时返回模拟数据
      const tableName = this.extractTableName()
      if (tableName) {
        return {
          name: tableName,
          rows: Math.floor(Math.random() * 100000)
        }
      }
      return null
    },

    extractTableName() {
      // 简单提取表名
      const patterns = [
        /FROM\s+([`\"]?\w+[`\"]?)/i,
        /UPDATE\s+([`\"]?\w+[`\"]?)/i,
        /INSERT\s+INTO\s+([`\"]?\w+[`\"]?)/i,
        /CREATE\s+TABLE\s+([`\"]?\w+[`\"]?)/i,
        /DROP\s+TABLE\s+([`\"]?\w+[`\"]?)/i,
        /TRUNCATE\s+TABLE\s+([`\"]?\w+[`\"]?)/i
      ]

      for (const pattern of patterns) {
        const match = this.sql.match(pattern)
        if (match) {
          return match[1].replace(/[`"]/g, '')
        }
      }

      return null
    },

    confirm() {
      this.$emit('confirm', {
        sql: this.sql,
        options: {
          addToHistory: this.addToHistory,
          commitAfter: this.commitAfter,
          riskLevel: this.riskLevel
        }
      })
    },

    confirmIfValid() {
      if (this.canConfirm) {
        this.confirm()
      }
    }
  }
}
</script>

<style scoped>
.sql-confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.sql-confirm-dialog {
  background-color: var(--bg-primary);
  border-radius: 8px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.sql-confirm-header {
  padding: 20px;
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  gap: 12px;
}

.sql-confirm-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
  font-weight: 600;
}

.sql-confirm-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.sql-preview {
  margin-bottom: 20px;
}

.sql-preview h4 {
  margin: 0 0 10px 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
}

.sql-preview pre {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.5;
  font-family: var(--font-family-mono, monospace);
}

.sql-risk-warning {
  padding: 12px;
  border-radius: 4px;
  margin-bottom: 15px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sql-risk-warning.low {
  background-color: rgba(40, 167, 69, 0.1);
  border: 1px solid rgba(40, 167, 69, 0.3);
  color: #28a745;
}

.sql-risk-warning.medium {
  background-color: rgba(255, 193, 7, 0.1);
  border: 1px solid rgba(255, 193, 7, 0.3);
  color: #856404;
}

.sql-risk-warning.high {
  background-color: rgba(220, 53, 69, 0.1);
  border: 1px solid rgba(220, 53, 69, 0.3);
  color: #721c24;
}

.sql-risk-warning.critical {
  background-color: rgba(114, 28, 36, 0.1);
  border: 1px solid rgba(114, 28, 36, 0.3);
  color: #721c24;
}

.warning-item,
.impact-estimate,
.table-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.text-confirmation {
  margin-bottom: 15px;
  padding: 15px;
  background-color: var(--bg-secondary);
  border-radius: 4px;
  border: 1px solid var(--border-primary);
}

.text-confirmation p {
  margin: 0 0 10px 0;
  color: var(--text-primary);
  font-size: 14px;
}

.text-confirmation code {
  background-color: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: var(--font-family-mono, monospace);
  color: var(--accent-primary);
}

.text-confirmation input {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-input);
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  color: var(--text-primary);
  font-size: 14px;
  font-family: var(--font-family-mono, monospace);
}

.text-confirmation input:focus {
  outline: none;
  border-color: var(--primary-color);
}

.double-confirmation,
.execution-options {
  margin-bottom: 15px;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.checkbox input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.checkbox span {
  user-select: none;
}

.sql-confirm-footer {
  padding: 20px;
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.sql-confirm-btn {
  padding: 8px 20px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.sql-confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.sql-confirm-btn-cancel {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-primary);
}

.sql-confirm-btn-cancel:hover:not(:disabled) {
  background-color: var(--bg-highlight);
}

.sql-confirm-btn-execute {
  background-color: var(--primary-color);
  color: white;
}

.sql-confirm-btn-execute:hover:not(:disabled) {
  background-color: var(--primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.3);
}

.sql-confirm-btn-execute.medium {
  background-color: #ffc107;
  color: #212529;
}

.sql-confirm-btn-execute.medium:hover:not(:disabled) {
  background-color: #e0a800;
  box-shadow: 0 2px 8px rgba(255, 193, 7, 0.3);
}

.sql-confirm-btn-execute.high {
  background-color: #dc3545;
}

.sql-confirm-btn-execute.high:hover:not(:disabled) {
  background-color: #c82333;
  box-shadow: 0 2px 8px rgba(220, 53, 69, 0.3);
}

.sql-confirm-btn-execute.critical {
  background-color: #721c24;
}

.sql-confirm-btn-execute.critical:hover:not(:disabled) {
  background-color: #5a1320;
  box-shadow: 0 2px 8px rgba(114, 28, 36, 0.3);
}
</style>