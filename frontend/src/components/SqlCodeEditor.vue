<template>
  <div class="sql-editor-container">
    <div ref="editorRef" class="editor"></div>
    <!-- @表名自动补全组件 -->
    <AtMention
      v-if="showAtMention"
      :session-id="sessionId"
      :database="database"
      :keyword="atKeyword"
      :position="atPosition"
      @select-table="handleSelectTable"
    />
  </div>
</template>

<script>
import { EditorView } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { sql } from '@codemirror/lang-sql'
import { oneDark } from '@codemirror/theme-one-dark'
import { autocompletion } from '@codemirror/autocomplete'
import { keymap } from '@codemirror/view'
import { defaultKeymap, insertTab } from '@codemirror/commands'
import AtMention from './AtMention.vue'

export default {
  name: 'SqlCodeEditor',
  components: {
    AtMention
  },
  props: {
    modelValue: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '输入SQL语句...'
    },
    sessionId: String,
    database: String
  },
  emits: ['update:modelValue', 'execute'],

  data() {
    return {
      editor: null,
      showAtMention: false,
      atKeyword: '',
      atPosition: { x: 0, y: 0 },
      atRange: null
    }
  },

  mounted() {
    this.initEditor()
  },

  beforeUnmount() {
    if (this.editor) {
      this.editor.destroy()
    }
  },

  watch: {
    modelValue(newValue) {
      if (this.editor && newValue !== this.editor.state.doc.toString()) {
        this.editor.dispatch({
          changes: {
            from: 0,
            to: this.editor.state.doc.length,
            insert: newValue
          }
        })
      }
    }
  },

  methods: {
    initEditor() {
      const customTheme = EditorView.theme({
        '&': {
          fontSize: '14px',
          fontFamily: 'var(--font-family-mono)',
          height: '100%'
        },
        '.cm-content': {
          padding: '12px',
          minHeight: '200px'
        },
        '.cm-focused': {
          outline: 'none'
        },
        '.cm-editor': {
          border: '1px solid var(--border-primary)',
          borderRadius: 'var(--radius-md)',
          backgroundColor: 'var(--bg-primary)'
        },
        '.cm-gutters': {
          backgroundColor: 'var(--bg-secondary)',
          borderRight: '1px solid var(--border-primary)',
          color: 'var(--text-tertiary)'
        },
        '.cm-lineNumbers .cm-gutterElement': {
          color: 'var(--text-tertiary)'
        },
        '.cm-activeLineGutter': {
          backgroundColor: 'var(--bg-highlight)'
        },
        '.cm-activeLine': {
          backgroundColor: 'var(--bg-secondary)'
        },
        '.cm-selectionBackground': {
          backgroundColor: 'var(--accent-primary)',
          opacity: 0.3
        },
        '.cm-selectionMatch': {
          backgroundColor: 'var(--warning-bg)'
        },
        '.cm-cursor': {
          borderLeftColor: 'var(--text-primary)'
        },
        '.cm-panel.cm-autocomplete': {
          backgroundColor: 'var(--bg-tertiary)',
          border: '1px solid var(--border-primary)',
          borderRadius: 'var(--radius-md)',
          boxShadow: 'var(--shadow-medium)'
        },
        '.cm-tooltip-autocomplete ul': {
          fontFamily: 'var(--font-family-mono)',
          fontSize: '13px'
        },
        '.cm-tooltip-autocomplete li': {
          color: 'var(--text-primary)',
          padding: '4px 8px'
        },
        '.cm-tooltip-autocomplete li[aria-selected]': {
          backgroundColor: 'var(--accent-primary)',
          color: 'white'
        }
      })

      const sqlKeywords = [
        'SELECT', 'FROM', 'WHERE', 'INSERT', 'UPDATE', 'DELETE', 'CREATE',
        'ALTER', 'DROP', 'INDEX', 'TABLE', 'DATABASE', 'PRIMARY', 'KEY',
        'FOREIGN', 'REFERENCES', 'JOIN', 'INNER', 'LEFT', 'RIGHT', 'OUTER',
        'GROUP', 'BY', 'ORDER', 'HAVING', 'UNION', 'ALL', 'DISTINCT',
        'COUNT', 'SUM', 'AVG', 'MIN', 'MAX', 'AND', 'OR', 'NOT', 'NULL',
        'IS', 'IN', 'EXISTS', 'BETWEEN', 'LIKE', 'LIMIT', 'OFFSET'
      ]

      const sqlCompletions = (context) => {
        const word = context.matchBefore(/\w*/)
        if (!word || word.from == word.to) return null

        return {
          from: word.from,
          options: sqlKeywords.map(keyword => ({
            label: keyword,
            type: 'keyword',
            apply: keyword.toUpperCase()
          })).concat(this.getTableCompletions()),
          span: /^[\w$]+$/
        }
      }

      const handleAtMention = (view) => {
        const { state, dispatch } = view
        const pos = state.selection.main.head

        // 查找@符号的位置
        const line = state.doc.lineAt(pos)
        let atPos = -1
        let keyword = ''

        for (let i = pos - 1; i >= line.from; i--) {
          const char = state.doc.sliceString(i, i + 1)
          if (char === '@') {
            atPos = i
            keyword = state.doc.sliceString(i + 1, pos)
            break
          }
          // 如果遇到空格或换行，说明不是有效的@引用
          if (/\s/.test(char)) break
        }

        if (atPos >= 0) {
          this.atRange = { from: atPos, to: pos }
          this.atKeyword = keyword

          // 计算弹出位置
          const coords = view.coordsAtPos(pos)
          this.atPosition = {
            x: coords.left,
            y: coords.bottom
          }
          this.showAtMention = true
        } else {
          this.hideAtMention()
        }
      }

      const hideAtMention = () => {
        this.showAtMention = false
        this.atKeyword = ''
        this.atRange = null
      }

      const customKeymap = keymap.of([
        ...defaultKeymap,
        {
          key: 'F5',
          run: () => {
            this.$emit('execute')
            return true
          }
        },
        {
          key: 'Ctrl-Enter',
          run: () => {
            this.$emit('execute')
            return true
          }
        },
        {
          key: 'Escape',
          run: (view) => {
            if (this.showAtMention) {
              hideAtMention()
              return true
            }
            return false
          }
        }
      ])

      // 监听输入变化
      const inputHandler = EditorView.inputHandler.of((view, from, to, text) => {
        // 检查是否输入了@符号
        if (text === '@') {
          setTimeout(() => handleAtMention(view), 0)
        } else if (this.showAtMention) {
          // 如果正在显示@补全，更新关键词
          const pos = view.state.selection.main.head
          const line = view.state.doc.lineAt(pos)
          let atPos = -1

          for (let i = pos - 1; i >= line.from; i--) {
            const char = view.state.doc.sliceString(i, i + 1)
            if (char === '@') {
              atPos = i
              break
            }
            if (/\s/.test(char)) break
          }

          if (atPos >= 0) {
            this.atKeyword = view.state.doc.sliceString(atPos + 1, pos)
          } else {
            hideAtMention()
          }
        }
      })

      const startState = EditorState.create({
        doc: this.modelValue,
        extensions: [
          customTheme,
          sql(),
          autocompletion({
            override: [sqlCompletions]
          }),
          customKeymap,
          inputHandler,
          EditorView.lineWrapping,
          EditorState.tabSize.of(4),
          EditorView.updateListener.of((update) => {
            if (update.docChanged) {
              const newValue = update.state.doc.toString()
              this.$emit('update:modelValue', newValue)
            }
          })
        ]
      })

      this.editor = new EditorView({
        state: startState,
        parent: this.$refs.editorRef
      })

      // 保存hideAtMention方法到实例
      this.hideAtMention = hideAtMention
    },

    getTableCompletions() {
      // 这里可以从父组件获取表名列表
      const tables = this.$parent?.tables || []
      return tables.map(table => ({
        label: table,
        type: 'table',
        apply: table
      }))
    },

    focus() {
      this.editor?.focus()
    },

    insertText(text) {
      const { state } = this.editor
      const transaction = state.update({
        changes: {
          from: state.selection.main.from,
          to: state.selection.main.to,
          insert: text
        },
        selection: {
          anchor: state.selection.main.from + text.length
        }
      })
      this.editor.dispatch(transaction)
    },

    getValue() {
      return this.editor?.state.doc.toString() || ''
    },

    handleSelectTable(table) {
      // 替换@后面的内容为选中的表名
      if (this.atRange && this.editor) {
        const { state } = this.editor
        const transaction = state.update({
          changes: {
            from: this.atRange.from,
            to: this.atRange.to,
            insert: table.name
          },
          selection: {
            anchor: this.atRange.from + table.name.length
          }
        })
        this.editor.dispatch(transaction)
      }
      this.hideAtMention()
    }
  }
}
</script>

<style scoped>
.sql-editor-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.editor {
  flex: 1;
  overflow: hidden;
}

:deep(.cm-editor) {
  height: 100%;
}
</style>