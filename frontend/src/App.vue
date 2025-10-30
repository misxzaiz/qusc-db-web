<template>
  <div class="app">
    <header class="header">
      <div class="header-left">
        <h1 class="logo">DB Admin</h1>
        <nav class="nav">
          <router-link to="/connections" class="nav-link">连接管理</router-link>
          <router-link to="/editor" class="nav-link">SQL编辑器</router-link>
        </nav>
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
  height: 48px;
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-xl);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 30px;
}

.logo {
  font-size: 18px;
  font-weight: 600;
  color: var(--accent-primary);
}

.nav {
  display: flex;
  gap: 20px;
}

.nav-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 14px;
  padding: 5px 10px;
  border-radius: var(--radius-sm);
  transition: var(--transition-fast);
}

.nav-link:hover {
  background-color: var(--bg-quaternary);
  color: var(--text-primary);
}

.nav-link.router-link-active {
  color: var(--accent-primary);
  background-color: var(--bg-quaternary);
}

.main {
  height: calc(100vh - 48px);
  overflow: hidden;
}

.app {
  height: 100vh;
  overflow: hidden;
}
</style>