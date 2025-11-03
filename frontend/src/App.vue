<template>
  <div class="app">
    <header class="header">
      <div class="header-left">
        <h1 class="logo">
          <font-awesome-icon icon="database" />
          DB Admin
        </h1>
        <nav class="nav">
          <router-link to="/connections" class="nav-link">
            <font-awesome-icon icon="plug" />
            连接管理
          </router-link>
          <router-link to="/editor" class="nav-link">
            <font-awesome-icon icon="code" />
            SQL编辑器
          </router-link>
          <router-link to="/ai-settings" class="nav-link">
            <font-awesome-icon icon="gear" />
            AI设置
          </router-link>
                  </nav>
      </div>
      <div class="header-right">
        <span class="version">v1.0.0</span>
      </div>
    </header>

    <main class="main">
      <router-view />
    </main>
  </div>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
@import './styles/theme.css';

.header {
  height: 56px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-xl);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  position: relative;
  z-index: 200; /* 确保header在右侧边栏之上 */
}

.header-left {
  display: flex;
  align-items: center;
  gap: 40px;
}

.logo {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--accent-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo svg {
  width: 24px;
  height: 24px;
}

.nav {
  display: flex;
  gap: 5px;
}

.nav-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  transition: var(--transition-fast);
  display: flex;
  align-items: center;
  gap: 6px;
}

.nav-link:hover {
  background-color: var(--bg-quaternary);
  color: var(--text-primary);
}

.nav-link.router-link-active {
  color: var(--accent-primary);
  background-color: var(--bg-quaternary);
  font-weight: var(--font-weight-semibold);
}

.nav-link svg {
  width: 14px;
  height: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.version {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--text-tertiary);
  padding: 4px 8px;
  background-color: var(--bg-quaternary);
  border-radius: var(--radius-sm);
}

.main {
  flex: 1;
  overflow: hidden;
}

.app {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>