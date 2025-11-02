<template>
  <div class="markdown-renderer">
    <div ref="contentContainer" v-html="renderedContent"></div>
    <span v-if="streaming" class="streaming-cursor">|</span>

    <!-- SQL确认对话框 -->
    <SqlConfirmDialog
      v-if="showConfirmDialog"
      :sql="sqlToExecute"
      :risk-level="sqlRiskLevel"
      @confirm="handleSqlConfirm"
      @cancel="handleSqlCancel"
    />
  </div>
</template>

<script>
import { marked } from 'marked'
import hljs from 'highlight.js'
import SqlConfirmDialog from './SqlConfirmDialog.vue'

// SQL风险等级定义
const SQL_RISK_LEVELS = {
  LOW: {
    keywords: ['SELECT', 'SHOW', 'DESCRIBE', 'EXPLAIN', 'WITH'],
    color: '#28a745',
    confirmRequired: false,
    message: '安全操作'
  },
  MEDIUM: {
    keywords: ['INSERT', 'UPDATE', 'DELETE', 'REPLACE'],
    color: '#ffc107',
    confirmRequired: true,
    message: '将修改数据'
  },
  HIGH: {
    keywords: ['CREATE', 'ALTER', 'DROP', 'TRUNCATE'],
    color: '#dc3545',
    confirmRequired: true,
    message: '将变更表结构'
  },
  CRITICAL: {
    patterns: [/DROP\s+DATABASE/i, /DROP\s+TABLE\s+(?!IF\s+EXISTS)/i, /DELETE\s+FROM\s+\w+\s+WHERE\s+1\s*=\s*1/i],
    color: '#721c24',
    confirmRequired: true,
    doubleConfirm: true,
    message: '危险操作！'
  }
}

export default {
  name: 'MarkdownRenderer',
  components: {
    SqlConfirmDialog
  },
  props: {
    content: {
      type: String,
      default: ''
    },
    streaming: {
      type: Boolean,
      default: false
    },
    allowSqlExecution: {
      type: Boolean,
      default: true
    }
  },
  emits: ['execute-sql', 'copy-sql', 'open-in-new-tab', 'execute-batch-sql', 'batch-sql-execute', 'batch-sql-cancelled', 'batch-sql-completed'],
  data() {
    return {
      showConfirmDialog: false,
      sqlToExecute: '',
      sqlRiskLevel: 'LOW',
      uniqueId: Math.random().toString(36).substr(2, 9),
      tempConfirmCallback: null
    }
  },
  computed: {
    renderedContent() {
      if (!this.content) return ''

      // 处理不完整的Markdown语法
      let processedContent = this.content

      // 处理未闭合的代码块
      const codeBlockCount = (processedContent.match(/```/g) || []).length
      if (codeBlockCount % 2 !== 0) {
        processedContent += '\n```'
      }

      // 处理未闭合的行内代码
      const inlineCodeCount = (processedContent.match(/`/g) || []).length
      if (inlineCodeCount % 2 !== 0) {
        processedContent += '`'
      }

      // 处理未闭合的粗体
      const boldCount = (processedContent.match(/\*\*/g) || []).length
      if (boldCount % 2 !== 0) {
        processedContent += '**'
      }

      // 处理未闭合的斜体
      const italicCount = (processedContent.match(/(?<!\*)\*(?!\*)/g) || []).length
      if (italicCount % 2 !== 0) {
        processedContent += '*'
      }

      // 预处理SQL代码块，为没有语言标识的SQL代码块添加标识
      processedContent = this.preprocessSqlBlocks(processedContent)

      // 配置 marked
      marked.setOptions({
        highlight: (code, lang) => {
          let highlightedCode = code
          let actualLang = lang

          // 如果没有语言标识，尝试自动检测
          if (!lang) {
            const detected = this.detectSqlLanguage(code)
            if (detected) {
              actualLang = detected
            }
          }

          if (actualLang && hljs.getLanguage(actualLang)) {
            try {
              highlightedCode = hljs.highlight(code, { language: actualLang }).value
            } catch (err) {
              console.error('Highlight error:', err)
              highlightedCode = hljs.highlightAuto(code).value
            }
          } else {
            highlightedCode = hljs.highlightAuto(code).value
          }

          // SQL代码块添加特殊标识，以便后续处理
          let codeClass = `hljs ${actualLang || 'language-unknown'}`

          if (this.allowSqlExecution && (this.isSqlLanguage(actualLang) || this.isSqlContent(code))) {
            // 为SQL代码块添加特殊class
            codeClass = `hljs sql sql-code-block`
          }

          return `<pre><code class="${codeClass}">${highlightedCode}</code></pre>`
        },
        breaks: true,
        gfm: true
      })

      const rendered = marked(processedContent)

      // 在下一个tick设置事件监听
      this.$nextTick(() => {
        this.setupEventListeners()
      })

      return rendered
    }
  },
  methods: {
    isSqlLanguage(lang) {
      if (!lang) return false
      const sqlLanguages = ['sql', 'mysql', 'postgresql', 'postgres', 'plsql', 'tsql', 'mariadb']
      return sqlLanguages.includes(lang.toLowerCase())
    },

    detectSqlLanguage(code) {
      // 检测代码是否是SQL
      const trimmedCode = code.trim()

      // SQL关键字检测
      const sqlKeywords = [
        'SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'ALTER', 'DROP',
        'WITH', 'MERGE', 'TRUNCATE', 'BEGIN', 'COMMIT', 'ROLLBACK'
      ]

      const firstWord = trimmedCode.split(/\s+/)[0]?.toUpperCase()
      if (sqlKeywords.includes(firstWord)) {
        return 'sql'
      }

      return null
    },

    isSqlContent(code) {
      // 检测内容是否是SQL语句
      const trimmedCode = code.trim().toUpperCase()

      // 检查是否包含SQL关键字
      const sqlPatterns = [
        /^SELECT\s+/i,
        /^INSERT\s+/i,
        /^UPDATE\s+/i,
        /^DELETE\s+/i,
        /^CREATE\s+/i,
        /^ALTER\s+/i,
        /^DROP\s+/i,
        /^WITH\s+/i,
        /^TRUNCATE\s+/i,
        /\bFROM\s+\w+/i,
        /\bWHERE\s+/i,
        /\bJOIN\s+/i
      ]

      return sqlPatterns.some(pattern => pattern.test(trimmedCode))
    },

    preprocessSqlBlocks(content) {
      // 预处理代码块，为没有语言标识的SQL代码块添加标识
      return content.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
        // 如果已经有语言标识，直接返回
        if (lang) return match

        // 检测是否是SQL代码
        if (this.isSqlContent(code)) {
          return '```sql\n' + code + '```'
        }

        return match
      })
    },

    
    analyzeSqlRisk(sql) {
      const normalizedSql = sql.toUpperCase().trim()

      // 检查极危险操作
      for (const [level, config] of Object.entries(SQL_RISK_LEVELS)) {
        if (config.patterns) {
          for (const pattern of config.patterns) {
            if (pattern.test(sql)) {
              return level
            }
          }
        }

        if (config.keywords) {
          for (const keyword of config.keywords) {
            if (normalizedSql.startsWith(keyword)) {
              return level
            }
          }
        }
      }

      return 'LOW'
    },

    setupEventListeners() {
      const container = this.$refs.contentContainer
      if (!container) return

      // 为SQL代码块添加操作按钮
      this.addSqlButtonsToCodeBlocks(container)

      // 移除旧的事件监听器
      container.removeEventListener('click', this.handleSqlButtonClick)

      // 添加新的事件监听器
      container.addEventListener('click', this.handleSqlButtonClick)
    },

    addSqlButtonsToCodeBlocks(container) {
      // 查找所有代码块
      const allCodeElements = container.querySelectorAll('pre code')

      allCodeElements.forEach((codeElement) => {
        // 检查是否已经添加了按钮
        if (codeElement.closest('.sql-code-block-wrapper')) return

        const preElement = codeElement.parentElement
        if (!preElement) return

        // 获取代码内容
        const code = codeElement.textContent || codeElement.innerText

        // 检查是否是SQL
        const isSql = this.isSqlContent(code)
        if (!isSql) return

        // 创建唯一的ID
        const codeId = `sql-${this.uniqueId}-${Math.random().toString(36).substr(2, 9)}`

        // 创建包装器
        const wrapper = document.createElement('div')
        wrapper.className = 'sql-code-block-wrapper'
        wrapper.setAttribute('data-code-id', codeId)

        // 在pre元素前插入包装器
        preElement.parentNode.insertBefore(wrapper, preElement)

        // 将pre元素移入包装器
        wrapper.appendChild(preElement)

        // 创建操作按钮
        const actionsDiv = document.createElement('div')
        actionsDiv.className = 'sql-actions'
        actionsDiv.innerHTML = `
          <button class="sql-btn" data-action="copy" data-code-id="${codeId}" title="复制">
            <i class="fas fa-copy"></i>
          </button>
          <button class="sql-btn" data-action="execute" data-code-id="${codeId}" title="执行">
            <i class="fas fa-play"></i>
          </button>
        `

        // 在pre元素前插入按钮
        wrapper.insertBefore(actionsDiv, preElement)

        // 存储SQL代码
        wrapper.setAttribute('data-sql', encodeURIComponent(code))
      })
    },

    handleSqlButtonClick(event) {
      const target = event.target
      const button = target.closest('.sql-btn')

      if (!button) return

      const action = button.dataset.action
      const codeId = button.dataset.codeId

      if (!action || !codeId) return

      const element = document.querySelector(`[data-code-id="${codeId}"]`)
      if (!element) return

      const sql = decodeURIComponent(element.dataset.sql)

      switch (action) {
        case 'copy':
          this.copySql(sql)
          break
        case 'execute':
          this.executeSql(sql)
          break
        case 'new-tab':
          this.openSqlInNewTab(sql)
          break
      }
    },

    copySql(sql) {
      navigator.clipboard.writeText(sql).then(() => {
        this.$emit('copy-sql', sql)
        this.showToast('SQL已复制到剪贴板', 'success')
      }).catch(err => {
        console.error('复制失败:', err)
        this.showToast('复制失败', 'error')
      })
    },

    executeSql(sql) {
      // 检测是否包含多条SQL
      const sqlStatements = this.splitSqlStatements(sql)

      if (sqlStatements.length > 1) {
        // 多条SQL，逐条执行
        this.executeMultipleSql(sqlStatements)
      } else {
        // 单条SQL，正常执行
        const riskLevel = this.analyzeSqlRisk(sql)

        if (riskLevel === 'LOW') {
          // 低风险直接执行
          this.$emit('execute-sql', sql)
        } else {
          // 中高风险需要确认
          this.sqlToExecute = sql
          this.sqlRiskLevel = riskLevel
          this.showConfirmDialog = true
        }
      }
    },

    async executeMultipleSql(sqlStatements) {
      // 发出批量执行事件，创建新Tab
      const batchId = `batch-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      this.$emit('execute-batch-sql', {
        id: batchId,
        sqlList: sqlStatements,
        total: sqlStatements.length
      })

      this.showToast(`开始在新的Tab中执行${sqlStatements.length}条SQL语句`, 'info')

      // 逐条执行SQL
      for (let i = 0; i < sqlStatements.length; i++) {
        const sql = sqlStatements[i]

        try {
          // 分析每条SQL的风险
          const riskLevel = this.analyzeSqlRisk(sql)

          if (riskLevel !== 'LOW') {
            // 中高风险需要确认
            const confirmed = await this.confirmSqlExecution(sql, riskLevel, `第${i + 1}/${sqlStatements.length}条SQL`)
            if (!confirmed) {
              this.showToast(`第${i + 1}条SQL执行已取消`, 'info')
              // 通知取消执行
              this.$emit('batch-sql-cancelled', { batchId, index: i })
              break
            }
          }

          // 发出单条执行事件到新Tab
          this.$emit('batch-sql-execute', {
            batchId,
            index: i,
            sql,
            total: sqlStatements.length
          })

          // 添加延迟避免过快执行
          if (i < sqlStatements.length - 1) {
            await new Promise(resolve => setTimeout(resolve, 500))
          }
        } catch (error) {
          console.error(`第${i + 1}条SQL执行失败:`, error)
          // 继续执行下一条，而不是中断
        }
      }

      // 通知批量执行完成
      this.$emit('batch-sql-completed', { batchId })
    },

    confirmSqlExecution(sql, riskLevel, prefix = '') {
      return new Promise((resolve) => {
        this.sqlToExecute = sql
        this.sqlRiskLevel = riskLevel
        this.showConfirmDialog = true

        // 临时保存确认回调
        this.tempConfirmCallback = (confirmed) => {
          this.tempConfirmCallback = null
          resolve(confirmed)
        }
      })
    },

    splitSqlStatements(sql) {
      // 分割SQL语句，以分号结尾的为完整语句
      const statements = []
      const lines = sql.split('\n')
      let currentStatement = ''

      for (const line of lines) {
        const trimmedLine = line.trim()

        // 跳过空行和注释
        if (!trimmedLine || trimmedLine.startsWith('--')) {
          continue
        }

        currentStatement += line + '\n'

        // 如果行以分号结尾，说明是一个完整的SQL语句
        if (trimmedLine.endsWith(';')) {
          statements.push(currentStatement.trim())
          currentStatement = ''
        }
      }

      // 如果还有未添加的内容（不以分号结尾的情况）
      if (currentStatement.trim()) {
        statements.push(currentStatement.trim())
      }

      return statements.filter(s => s)
    },

    openSqlInNewTab(sql) {
      this.$emit('open-in-new-tab', sql)
    },

    showToast(message, type = 'info') {
      // 创建临时提示
      const toast = document.createElement('div')
      toast.className = `sql-toast sql-toast-${type}`
      toast.textContent = message
      toast.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 10px 20px;
        background: ${type === 'success' ? '#28a745' : type === 'error' ? '#dc3545' : '#007bff'};
        color: white;
        border-radius: 4px;
        z-index: 10000;
        animation: slideIn 0.3s ease;
      `

      document.body.appendChild(toast)
      setTimeout(() => {
        toast.remove()
      }, 3000)
    },

    handleSqlConfirm() {
      this.$emit('execute-sql', this.sqlToExecute)

      // 如果有临时回调（用于批量执行确认），调用它
      if (this.tempConfirmCallback) {
        this.tempConfirmCallback(true)
      }

      this.showConfirmDialog = false
      this.sqlToExecute = ''
      this.sqlRiskLevel = 'LOW'
    },

    handleSqlCancel() {
      // 如果有临时回调（用于批量执行确认），调用它
      if (this.tempConfirmCallback) {
        this.tempConfirmCallback(false)
      }

      this.showConfirmDialog = false
      this.sqlToExecute = ''
      this.sqlRiskLevel = 'LOW'
    }
  },
  mounted() {
    // 组件挂载后设置事件监听器
    this.$nextTick(() => {
      this.setupEventListeners()
    })
  },
  beforeUnmount() {
    // 清理事件监听器
    const container = this.$refs.contentContainer
    if (container) {
      container.removeEventListener('click', this.handleSqlButtonClick)
    }
  }
}
</script>

<style scoped>
.markdown-renderer {
  color: var(--text-primary);
  line-height: 1.6;
}

.streaming-cursor {
  color: var(--accent-primary);
  font-weight: bold;
  animation: blink 1s infinite;
  margin-left: 2px;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* Markdown 样式 */
.markdown-renderer :deep(h1),
.markdown-renderer :deep(h2),
.markdown-renderer :deep(h3),
.markdown-renderer :deep(h4),
.markdown-renderer :deep(h5),
.markdown-renderer :deep(h6) {
  color: var(--text-primary);
  margin-top: 1.5em;
  margin-bottom: 0.5em;
  font-weight: 600;
}

.markdown-renderer :deep(h1) { font-size: 1.8em; }
.markdown-renderer :deep(h2) { font-size: 1.5em; }
.markdown-renderer :deep(h3) { font-size: 1.3em; }
.markdown-renderer :deep(h4) { font-size: 1.1em; }

.markdown-renderer :deep(p) {
  margin: 0.5em 0;
}

.markdown-renderer :deep(ul),
.markdown-renderer :deep(ol) {
  margin: 0.5em 0;
  padding-left: 2em;
}

.markdown-renderer :deep(li) {
  margin: 0.2em 0;
}

.markdown-renderer :deep(blockquote) {
  margin: 1em 0;
  padding: 0.5em 1em;
  border-left: 4px solid var(--accent-primary);
  background-color: var(--bg-tertiary);
  color: var(--text-secondary);
}

.markdown-renderer :deep(code) {
  background-color: var(--bg-tertiary);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: var(--font-family-mono);
  font-size: 0.9em;
  color: var(--accent-primary);
}

.markdown-renderer :deep(pre) {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  padding: 1em;
  overflow-x: auto;
  margin: 1em 0;
}

.markdown-renderer :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--text-primary);
}

.markdown-renderer :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 1em 0;
}

.markdown-renderer :deep(th),
.markdown-renderer :deep(td) {
  border: 1px solid var(--border-primary);
  padding: 0.5em 1em;
  text-align: left;
}

.markdown-renderer :deep(th) {
  background-color: var(--bg-tertiary);
  font-weight: 600;
}

.markdown-renderer :deep(tr:nth-child(even)) {
  background-color: var(--bg-secondary);
}

.markdown-renderer :deep(a) {
  color: var(--accent-primary);
  text-decoration: none;
}

.markdown-renderer :deep(a:hover) {
  text-decoration: underline;
}

.markdown-renderer :deep(hr) {
  border: none;
  border-top: 1px solid var(--border-primary);
  margin: 2em 0;
}

/* Highlight.js 主题适配 */
.markdown-renderer :deep(.hljs) {
  background-color: var(--bg-secondary) !important;
  color: var(--text-primary) !important;
}

.markdown-renderer :deep(.hljs-comment),
.markdown-renderer :deep(.hljs-quote) {
  color: var(--text-tertiary) !important;
  font-style: italic;
}

.markdown-renderer :deep(.hljs-keyword),
.markdown-renderer :deep(.hljs-selector-tag),
.markdown-renderer :deep(.hljs-subst) {
  color: var(--accent-primary) !important;
  font-weight: bold;
}

.markdown-renderer :deep(.hljs-number),
.markdown-renderer :deep(.hljs-literal),
.markdown-renderer :deep(.hljs-variable),
.markdown-renderer :deep(.hljs-template-variable),
.markdown-renderer :deep(.hljs-tag .hljs-attr) {
  color: #d19a66 !important;
}

.markdown-renderer :deep(.hljs-string),
.markdown-renderer :deep(.hljs-doctag) {
  color: #98c379 !important;
}

.markdown-renderer :deep(.hljs-title),
.markdown-renderer :deep(.hljs-section),
.markdown-renderer :deep(.hljs-selector-id) {
  color: #61afef !important;
  font-weight: bold;
}

.markdown-renderer :deep(.hljs-type),
.markdown-renderer :deep(.hljs-class .hljs-title) {
  color: #e5c07b !important;
}

.markdown-renderer :deep(.hljs-tag),
.markdown-renderer :deep(.hljs-name),
.markdown-renderer :deep(.hljs-attribute) {
  color: var(--text-secondary) !important;
  font-weight: normal;
}

.markdown-renderer :deep(.hljs-regexp),
.markdown-renderer :deep(.hljs-link) {
  color: #56b6c2 !important;
}

.markdown-renderer :deep(.hljs-symbol),
.markdown-renderer :deep(.hljs-bullet) {
  color: #c678dd !important;
}

.markdown-renderer :deep(.hljs-built_in),
.markdown-renderer :deep(.hljs-builtin-name) {
  color: #e6c07b !important;
}

.markdown-renderer :deep(.hljs-meta) {
  color: #abb2bf !important;
}

.markdown-renderer :deep(.hljs-deletion) {
  background-color: #ffeef0 !important;
}

.markdown-renderer :deep(.hljs-addition) {
  background-color: #f0fff4 !important;
}

.markdown-renderer :deep(.hljs-emphasis) {
  font-style: italic;
}

.markdown-renderer :deep(.hljs-strong) {
  font-weight: bold;
}

/* SQL代码块样式 */
.markdown-renderer :deep(.sql-code-block-wrapper) {
  position: relative;
  margin: 1em 0;
  border: 1px solid var(--border-primary);
  border-radius: 4px;
  background-color: var(--bg-secondary);
}

.markdown-renderer :deep(.sql-actions) {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
  z-index: 10;
}

.markdown-renderer :deep(.sql-btn) {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 4px;
  background-color: #007bff;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.2s ease;
}

.markdown-renderer :deep(.sql-btn:hover) {
  background-color: #0056b3;
  transform: translateY(-1px);
}

.markdown-renderer :deep(.sql-btn i) {
  pointer-events: none;
}

/* SQL代码块pre样式调整 */
.markdown-renderer :deep(.sql-code-block-wrapper pre) {
  margin: 0;
  background-color: transparent;
  border: none;
  padding: 2em 1em 1em 1em; /* 为按钮留出空间 */
  overflow-x: auto;
}

/* Toast动画 */
@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

/* 确认对话框样式 */
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
}

.sql-risk-warning {
  padding: 12px;
  border-radius: 4px;
  margin-bottom: 15px;
}

.sql-risk-warning.low {
  background-color: rgba(40, 167, 69, 0.1);
  border: 1px solid rgba(40, 167, 69, 0.3);
  color: #28a745;
}

.sql-risk-warning.medium {
  background-color: rgba(255, 193, 7, 0.1);
  border: 1px solid rgba(255, 193, 7, 0.3);
  color: #ffc107;
}

.sql-risk-warning.high {
  background-color: rgba(220, 53, 69, 0.1);
  border: 1px solid rgba(220, 53, 69, 0.3);
  color: #dc3545;
}

.sql-risk-warning.critical {
  background-color: rgba(114, 28, 36, 0.1);
  border: 1px solid rgba(114, 28, 36, 0.3);
  color: #721c24;
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
}

.sql-confirm-btn-cancel {
  background-color: var(--bg-secondary);
  color: var(--text-primary);
  border: 1px solid var(--border-primary);
}

.sql-confirm-btn-cancel:hover {
  background-color: var(--bg-highlight);
}

.sql-confirm-btn-execute {
  background-color: var(--primary-color);
  color: white;
}

.sql-confirm-btn-execute:hover {
  background-color: var(--primary-hover);
}

.sql-confirm-btn-execute.medium {
  background-color: #ffc107;
}

.sql-confirm-btn-execute.high {
  background-color: #dc3545;
}

.sql-confirm-btn-execute.critical {
  background-color: #721c24;
}
</style>