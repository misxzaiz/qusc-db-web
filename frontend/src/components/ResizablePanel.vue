<template>
  <div class="resizable-container" :style="{ flexDirection: direction === 'horizontal' ? 'row' : 'column' }">
    <!-- 第一个面板 -->
    <div class="panel panel-first" :style="firstPanelStyle">
      <slot name="first"></slot>
    </div>

    <!-- 可拖拽分隔条 -->
    <div
      class="resizer"
      :class="resizerClass"
      @mousedown="startResize"
      @touchstart="startResize"
    >
      <div class="resizer-handle"></div>
    </div>

    <!-- 第二个面板 -->
    <div class="panel panel-second" :style="secondPanelStyle">
      <slot name="second"></slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ResizablePanel',

  props: {
    // 方向：horizontal（水平）或 vertical（垂直）
    direction: {
      type: String,
      default: 'horizontal'
    },
    // 第一个面板的默认大小（像素或百分比）
    defaultFirstSize: {
      type: String,
      default: '300px'
    },
    // 最小尺寸
    minFirstSize: {
      type: Number,
      default: 200
    },
    minSecondSize: {
      type: Number,
      default: 200
    },
    // 是否折叠
    firstCollapsed: {
      type: Boolean,
      default: false
    }
  },

  data() {
    return {
      isResizing: false,
      firstSize: this.defaultFirstSize,
      startPos: 0,
      startSize: 0
    }
  },

  computed: {
    firstPanelStyle() {
      if (this.direction === 'horizontal') {
        return {
          width: this.firstCollapsed ? '0px' : this.firstSize,
          minWidth: this.firstCollapsed ? '0px' : `${this.minFirstSize}px`
        }
      } else {
        return {
          height: this.firstCollapsed ? '0px' : this.firstSize,
          minHeight: this.firstCollapsed ? '0px' : `${this.minFirstSize}px`
        }
      }
    },

    secondPanelStyle() {
      if (this.direction === 'horizontal') {
        return {
          width: `calc(100% - ${this.firstCollapsed ? '0px' : this.firstSize} - 4px)`,
          minWidth: `${this.minSecondSize}px`
        }
      } else {
        return {
          height: `calc(100% - ${this.firstCollapsed ? '0px' : this.firstSize} - 4px)`,
          minHeight: `${this.minSecondSize}px`
        }
      }
    },

    resizerClass() {
      return this.direction === 'horizontal' ? 'resizer-vertical' : 'resizer-horizontal'
    }
  },

  mounted() {
    // 监听全局鼠标和触摸事件
    document.addEventListener('mousemove', this.handleResize)
    document.addEventListener('mouseup', this.stopResize)
    document.addEventListener('touchmove', this.handleResize)
    document.addEventListener('touchend', this.stopResize)
  },

  beforeUnmount() {
    // 清理事件监听
    document.removeEventListener('mousemove', this.handleResize)
    document.removeEventListener('mouseup', this.stopResize)
    document.removeEventListener('touchmove', this.handleResize)
    document.removeEventListener('touchend', this.stopResize)
  },

  methods: {
    startResize(e) {
      this.isResizing = true
      this.startPos = this.direction === 'horizontal' ? e.clientX : e.clientY

      // 获取当前第一个面板的实际尺寸
      const firstPanel = this.$el.querySelector('.panel-first')
      this.startSize = this.direction === 'horizontal'
        ? firstPanel.offsetWidth
        : firstPanel.offsetHeight

      // 防止选中文本
      document.body.style.userSelect = 'none'
      document.body.style.cursor = this.direction === 'horizontal' ? 'col-resize' : 'row-resize'

      e.preventDefault()
    },

    handleResize(e) {
      if (!this.isResizing) return

      const currentPos = this.direction === 'horizontal' ? e.clientX : e.clientY
      const delta = currentPos - this.startPos

      // 计算新尺寸
      let newSize = this.startSize + delta

      // 限制最小尺寸
      if (newSize < this.minFirstSize) {
        newSize = this.minFirstSize
      }

      // 限制最大尺寸（确保第二个面板不小于最小尺寸）
      const containerSize = this.direction === 'horizontal'
        ? this.$el.offsetWidth
        : this.$el.offsetHeight
      const maxFirstSize = containerSize - this.minSecondSize - 4 // 4px是分隔条宽度
      if (newSize > maxFirstSize) {
        newSize = maxFirstSize
      }

      this.firstSize = `${newSize}px`

      // 触发尺寸变化事件
      this.$emit('resize', {
        firstSize: newSize,
        secondSize: containerSize - newSize - 4
      })
    },

    stopResize() {
      if (this.isResizing) {
        this.isResizing = false
        document.body.style.userSelect = ''
        document.body.style.cursor = ''

        // 触发停止调整事件
        this.$emit('resize-end', {
          firstSize: this.firstSize
        })
      }
    },

    // 折叠/展开第一个面板
    toggleFirstPanel() {
      this.$emit('update:firstCollapsed', !this.firstCollapsed)
    }
  }
}
</script>

<style scoped>
.resizable-container {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.panel {
  overflow: auto;
  transition: width 0.3s ease, height 0.3s ease;
}

.panel-first {
  flex-shrink: 0;
  position: relative;
}

.panel-second {
  flex: 1;
  min-width: 0;
}

.resizer {
  flex-shrink: 0;
  background-color: var(--bg-tertiary);
  position: relative;
  cursor: col-resize;
  transition: background-color 0.2s;
}

.resizer:hover {
  background-color: var(--accent-primary);
}

.resizer-vertical {
  width: 4px;
  height: 100%;
  cursor: col-resize;
}

.resizer-horizontal {
  height: 4px;
  width: 100%;
  cursor: row-resize;
}

.resizer-handle {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resizer-vertical .resizer-handle {
  width: 20px;
  height: 40px;
}

.resizer-horizontal .resizer-handle {
  width: 40px;
  height: 20px;
}

.resizer-handle::before,
.resizer-handle::after {
  content: '';
  position: absolute;
  background-color: var(--text-tertiary);
  border-radius: 1px;
}

.resizer-vertical .resizer-handle::before,
.resizer-vertical .resizer-handle::after {
  width: 2px;
  height: 12px;
}

.resizer-horizontal .resizer-handle::before,
.resizer-horizontal .resizer-handle::after {
  width: 12px;
  height: 2px;
}

.resizer-vertical .resizer-handle::before {
  left: 4px;
}

.resizer-vertical .resizer-handle::after {
  right: 4px;
}

.resizer-horizontal .resizer-handle::before {
  top: 4px;
}

.resizer-horizontal .resizer-handle::after {
  bottom: 4px;
}

/* 折叠状态样式 */
.panel-first.collapsed {
  width: 0 !important;
  min-width: 0 !important;
  overflow: hidden;
}
</style>