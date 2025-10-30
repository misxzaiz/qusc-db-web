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
.header {
  height: 48px;
  background-color: #2d2d30;
  border-bottom: 1px solid #3e3e42;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 30px;
}

.logo {
  font-size: 18px;
  font-weight: 600;
  color: #569cd6;
}

.nav {
  display: flex;
  gap: 20px;
}

.nav-link {
  color: #969696;
  text-decoration: none;
  font-size: 14px;
  padding: 5px 10px;
  border-radius: 3px;
  transition: all 0.2s;
}

.nav-link:hover {
  background-color: #3e3e42;
  color: #d4d4d4;
}

.nav-link.router-link-active {
  color: #569cd6;
  background-color: #3e3e42;
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