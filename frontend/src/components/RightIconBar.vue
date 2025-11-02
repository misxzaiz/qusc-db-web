<template>
  <div class="right-icon-bar">
    <!-- AI助手图标 -->
    <button
      class="icon-btn"
      :class="{ active: isAiSidebarOpen }"
      @click="toggleAiSidebar"
      title="AI助手"
    >
      <font-awesome-icon icon="comments" />
      <span class="icon-tooltip">AI助手</span>
    </button>

    <!-- 预留其他功能图标位置 -->
    <!--
    <button
      class="icon-btn"
      :class="{ active: isOtherSidebarOpen }"
      @click="toggleOtherSidebar"
      title="其他功能"
    >
      <font-awesome-icon icon="cog" />
      <span class="icon-tooltip">其他功能</span>
    </button>
    -->
  </div>
</template>

<script>
export default {
  name: 'RightIconBar',

  props: {
    isAiSidebarOpen: {
      type: Boolean,
      default: false
    }
  },

  emits: ['toggle-ai'],

  methods: {
    toggleAiSidebar() {
      this.$emit('toggle-ai')
    }
  }
}
</script>

<style scoped>
.right-icon-bar {
  position: fixed;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 999; /* 低于AI侧边栏的z-index */
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  background-color: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  box-shadow: -2px 0 8px rgba(0, 0, 0, 0.1);
}

.icon-btn {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px 0 0 8px;
  transition: all 0.2s ease;
  font-size: 16px;
}

.icon-btn:hover {
  background-color: var(--bg-highlight);
  color: var(--text-primary);
}

.icon-btn.active {
  background-color: var(--primary-color);
  color: white;
}

.icon-tooltip {
  position: absolute;
  right: 100%;
  top: 50%;
  transform: translateY(-50%);
  margin-right: 8px;
  padding: 4px 8px;
  background-color: var(--bg-tooltip);
  color: var(--text-tooltip);
  font-size: 12px;
  white-space: nowrap;
  border-radius: 4px;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s ease;
  pointer-events: none;
}

.icon-btn:hover .icon-tooltip {
  opacity: 1;
  visibility: visible;
}

/* CSS变量定义（如果主题中没有定义的话） */
:root {
  --bg-secondary: #2c3e50;
  --bg-highlight: #34495e;
  --border-color: #34495e;
  --text-secondary: #95a5a6;
  --text-primary: #ecf0f1;
  --primary-color: #3498db;
  --bg-tooltip: #34495e;
  --text-tooltip: #ecf0f1;
}
</style>