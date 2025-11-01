<template>
  <div class="at-mention" v-if="showSuggestions" ref="suggestionsRef">
    <div class="suggestions-popup" :style="{ left: position.x + 'px', top: position.y + 'px' }">
      <div class="suggestions-header">
        <font-awesome-icon icon="table" />
        选择表名
      </div>
      <div class="suggestions-list" ref="listRef">
        <div
          v-for="(table, index) in filteredTables"
          :key="table.name"
          :class="['suggestion-item', { active: index === selectedIndex }]"
          @click="selectTable(table)"
          @mouseenter="selectedIndex = index"
        >
          <div class="table-info">
            <span class="table-name">{{ table.name }}</span>
            <span class="table-detail">{{ table.columnCount }} 列</span>
            <span v-if="table.comment" class="table-comment">{{ table.comment }}</span>
          </div>
        </div>
      </div>
      <div v-if="loading" class="loading">
        <font-awesome-icon icon="spinner" spin />
        加载中...
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

export default {
  name: 'AtMention',
  props: {
    sessionId: String,
    database: String,
    keyword: String,
    position: {
      type: Object,
      default: () => ({ x: 0, y: 0 })
    },
    triggerChar: {
      type: String,
      default: '@'
    }
  },
  emits: ['select-table'],

  setup(props, { emit }) {
    const showSuggestions = ref(false)
    const loading = ref(false)
    const selectedIndex = ref(0)
    const tables = ref([])
    const listRef = ref(null)
    const suggestionsRef = ref(null)

    // 过滤表名
    const filteredTables = computed(() => {
      if (!props.keyword) return tables.value

      const search = props.keyword.toLowerCase()
      return tables.value.filter(table =>
        table.name.toLowerCase().includes(search)
      )
    })

    // 加载表列表
    const loadTables = async () => {
      if (!props.sessionId || !props.database) return

      loading.value = true
      try {
        const response = await fetch(`/api/sql/tables/${props.sessionId}?database=${props.database}`)
        const data = await response.json()

        if (data.tables) {
          // 获取每个表的详细信息
          tables.value = await Promise.all(
            data.tables.map(async tableName => {
              try {
                const schemaResponse = await fetch(`/api/sql/table-schema/${props.sessionId}?tableName=${tableName}`)
                const schema = await schemaResponse.json()
                return {
                  name: tableName,
                  columnCount: schema.columnCount || 0,
                  comment: schema.comment || ''
                }
              } catch (e) {
                return {
                  name: tableName,
                  columnCount: 0,
                  comment: ''
                }
              }
            })
          )
        }
      } catch (error) {
        console.error('加载表列表失败:', error)
      } finally {
        loading.value = false
      }
    }

    // 显示建议
    const show = () => {
      showSuggestions.value = true
      selectedIndex.value = 0
      loadTables()
    }

    // 隐藏建议
    const hide = () => {
      showSuggestions.value = false
    }

    // 选择表
    const selectTable = (table) => {
      emit('select-table', table)
      hide()
    }

    // 键盘导航
    const handleKeydown = (e) => {
      if (!showSuggestions.value) return

      switch (e.key) {
        case 'ArrowDown':
          e.preventDefault()
          selectedIndex.value = Math.min(selectedIndex.value + 1, filteredTables.value.length - 1)
          scrollToSelected()
          break
        case 'ArrowUp':
          e.preventDefault()
          selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
          scrollToSelected()
          break
        case 'Enter':
        case 'Tab':
          e.preventDefault()
          if (filteredTables.value[selectedIndex.value]) {
            selectTable(filteredTables.value[selectedIndex.value])
          }
          break
        case 'Escape':
          e.preventDefault()
          hide()
          break
      }
    }

    // 滚动到选中项
    const scrollToSelected = () => {
      if (listRef.value && listRef.value.children[selectedIndex.value]) {
        listRef.value.children[selectedIndex.value].scrollIntoView({
          block: 'nearest'
        })
      }
    }

    // 点击外部关闭
    const handleClickOutside = (e) => {
      if (suggestionsRef.value && !suggestionsRef.value.contains(e.target)) {
        hide()
      }
    }

    // 监听键盘事件
    onMounted(() => {
      document.addEventListener('keydown', handleKeydown)
      document.addEventListener('click', handleClickOutside)
    })

    onUnmounted(() => {
      document.removeEventListener('keydown', handleKeydown)
      document.removeEventListener('click', handleClickOutside)
    })

    // 监听关键词变化
    watch(() => props.keyword, () => {
      selectedIndex.value = 0
    })

    return {
      showSuggestions,
      loading,
      selectedIndex,
      filteredTables,
      listRef,
      suggestionsRef,
      selectTable,
      show,
      hide
    }
  }
}
</script>

<style scoped>
.at-mention {
  position: fixed;
  z-index: 1000;
}

.suggestions-popup {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-height: 300px;
  overflow: hidden;
}

.suggestions-header {
  padding: 8px 12px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 6px;
}

.suggestions-list {
  max-height: 250px;
  overflow-y: auto;
}

.suggestion-item {
  padding: 8px 12px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid var(--border-secondary);
}

.suggestion-item:hover,
.suggestion-item.active {
  background-color: var(--bg-highlight);
}

.table-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.table-name {
  font-weight: 500;
  color: var(--text-primary);
  font-family: var(--font-family-mono);
  font-size: 13px;
}

.table-detail {
  font-size: 11px;
  color: var(--text-tertiary);
}

.table-comment {
  font-size: 11px;
  color: var(--text-secondary);
  font-style: italic;
}

.loading {
  padding: 20px;
  text-align: center;
  color: var(--text-secondary);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>