<template>
  <div class="markdown-renderer">
    <div v-html="renderedContent"></div>
    <span v-if="streaming" class="streaming-cursor">|</span>
  </div>
</template>

<script>
import { marked } from 'marked'
import hljs from 'highlight.js'

export default {
  name: 'MarkdownRenderer',
  props: {
    content: {
      type: String,
      default: ''
    },
    streaming: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    renderedContent() {
      if (!this.content) return ''

      // 配置 marked
      marked.setOptions({
        highlight: (code, lang) => {
          if (lang && hljs.getLanguage(lang)) {
            try {
              return hljs.highlight(code, { language: lang }).value
            } catch (err) {
              console.error('Highlight error:', err)
            }
          }
          return hljs.highlightAuto(code).value
        },
        breaks: true,
        gfm: true
      })

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

      return marked(processedContent)
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
</style>