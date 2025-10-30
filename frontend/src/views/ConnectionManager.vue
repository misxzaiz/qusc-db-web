<template>
  <div class="connection-manager">
    <div class="toolbar">
      <button class="btn btn-primary" @click="showCreateDialog = true">
        新建连接
      </button>
    </div>

    <div class="connection-list">
      <div v-if="connections.length === 0" class="empty-state">
        <p>暂无数据库连接</p>
        <p class="empty-hint">点击"新建连接"添加您的第一个数据库连接</p>
      </div>

      <div v-for="conn in connections" :key="conn.id" class="connection-card">
        <div class="card-header">
          <h3>{{ conn.name }}</h3>
          <div class="card-actions">
            <button class="btn btn-small" @click="testConnection(conn)">测试</button>
            <button class="btn btn-small" @click="openEditor(conn)">打开</button>
            <button class="btn btn-small btn-secondary" @click="editConnection(conn)">编辑</button>
            <button class="btn btn-small btn-danger" @click="deleteConnection(conn.id)">删除</button>
          </div>
        </div>
        <div class="card-body">
          <div class="connection-info">
            <div class="info-item">
              <span class="label">主机:</span>
              <span class="value">{{ conn.host }}:{{ conn.port }}</span>
            </div>
            <div class="info-item">
              <span class="label">数据库:</span>
              <span class="value">{{ conn.database }}</span>
            </div>
            <div class="info-item">
              <span class="label">用户名:</span>
              <span class="value">{{ conn.username }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑连接对话框 -->
    <div v-if="showCreateDialog || editingConnection" class="dialog-overlay" @click="closeDialog">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h2>{{ editingConnection ? '编辑连接' : '新建连接' }}</h2>
          <button class="close-btn" @click="closeDialog">×</button>
        </div>

        <div class="dialog-body">
          <div class="form-group">
            <label>连接名称</label>
            <input v-model="formData.name" type="text" placeholder="输入连接名称" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>主机地址</label>
              <input v-model="formData.host" type="text" placeholder="localhost" />
            </div>
            <div class="form-group">
              <label>端口</label>
              <input v-model.number="formData.port" type="number" placeholder="3306" />
            </div>
          </div>

          <div class="form-group">
            <label>数据库名</label>
            <input v-model="formData.database" type="text" placeholder="数据库名称" />
          </div>

          <div class="form-group">
            <label>用户名</label>
            <input v-model="formData.username" type="text" placeholder="用户名" />
          </div>

          <div class="form-group">
            <label>密码</label>
            <input v-model="formData.password" type="password" placeholder="密码" />
          </div>

          <div class="form-group">
            <label>SSL模式</label>
            <select v-model="formData.sslMode">
              <option value="DISABLED">禁用</option>
              <option value="REQUIRED">必需</option>
            </select>
          </div>
        </div>

        <div class="dialog-footer">
          <button class="btn btn-secondary" @click="closeDialog">取消</button>
          <button class="btn btn-primary" @click="testAndSave">测试并保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { connectionApi } from '../services/api'
import { connectionStorage } from '../utils/storage'

export default {
  name: 'ConnectionManager',
  data() {
    return {
      connections: [],
      showCreateDialog: false,
      editingConnection: null,
      formData: {
        name: '',
        host: 'localhost',
        port: 3306,
        database: '',
        username: '',
        password: '',
        sslMode: 'DISABLED'
      }
    }
  },

  mounted() {
    this.loadConnections()
  },

  methods: {
    loadConnections() {
      this.connections = connectionStorage.getAll()
    },

    async testConnection(connection) {
      try {
        const response = await connectionApi.test(connection)
        if (response.data.success) {
          alert('连接测试成功！')
        } else {
          const errorMsg = response.data.error || '未知错误'
          alert(`连接测试失败！\n错误: ${errorMsg}`)
        }
      } catch (error) {
        const errorMsg = error.response?.data?.error || error.message
        alert('测试失败: ' + errorMsg)
      }
    },

    async testAndSave() {
      if (!this.formData.name || !this.formData.host || !this.formData.database || !this.formData.username) {
        alert('请填写必要字段')
        return
      }

      try {
        // 先测试连接
        const testResponse = await connectionApi.test(this.formData)
        if (!testResponse.data.success) {
          alert('连接测试失败，请检查配置')
          return
        }

        // 保存连接到localStorage
        connectionStorage.save(this.formData)
        alert(this.editingConnection ? '连接更新成功' : '连接创建成功')

        this.closeDialog()
        this.loadConnections()
      } catch (error) {
        alert('保存失败: ' + error.response?.data?.error || error.message)
      }
    },

    editConnection(conn) {
      this.editingConnection = conn
      this.formData = { ...conn }
    },

    deleteConnection(id) {
      if (!confirm('确定要删除这个连接吗？')) {
        return
      }

      connectionStorage.delete(id)
      alert('连接已删除')
      this.loadConnections()
    },

    openEditor(connection) {
      // 保存连接信息到localStorage供编辑器使用
      localStorage.setItem('currentConnection', JSON.stringify(connection))
      this.$router.push('/editor')
    },

    closeDialog() {
      this.showCreateDialog = false
      this.editingConnection = null
      this.formData = {
        name: '',
        host: 'localhost',
        port: 3306,
        database: '',
        username: '',
        password: '',
        sslMode: 'DISABLED'
      }
    }
  }
}
</script>

<style scoped>
@import '../styles/theme.css';

.connection-manager {
  height: 100%;
  padding: var(--spacing-xl);
  overflow-y: auto;
}

.toolbar {
  margin-bottom: var(--spacing-xl);
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
  transition: var(--transition-fast);
  font-family: var(--font-family-base);
}

.btn-primary {
  background-color: var(--btn-primary-bg);
  color: var(--btn-primary-text);
}

.btn-primary:hover {
  background-color: var(--btn-primary-hover);
}

.btn-secondary {
  background-color: var(--btn-secondary-bg);
  color: var(--btn-secondary-text);
}

.btn-secondary:hover {
  background-color: var(--btn-secondary-hover);
}

.btn-danger {
  background-color: var(--btn-danger-bg);
  color: var(--btn-danger-text);
}

.btn-danger:hover {
  background-color: var(--btn-danger-hover);
}

.btn-small {
  padding: 4px 8px;
  font-size: 12px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--text-secondary);
}

.empty-hint {
  margin-top: var(--spacing-sm);
  font-size: 14px;
}

.connection-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: var(--spacing-xl);
}

.connection-card {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: var(--transition-fast);
}

.connection-card:hover {
  border-color: var(--accent-primary);
}

.card-header {
  padding: var(--spacing-lg);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
  color: var(--accent-primary);
  font-size: 16px;
}

.card-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.card-body {
  padding: var(--spacing-lg);
}

.connection-info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.info-item {
  display: flex;
  gap: 10px;
}

.label {
  color: var(--text-secondary);
  min-width: 60px;
}

.value {
  color: var(--text-primary);
  font-family: var(--font-family-mono);
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background-color: var(--bg-tertiary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  width: 500px;
  max-width: 90%;
  box-shadow: var(--shadow-heavy);
}

.dialog-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-header h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-secondary);
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-fast);
}

.close-btn:hover {
  color: var(--text-primary);
}

.dialog-body {
  padding: var(--spacing-xl);
}

.form-group {
  margin-bottom: var(--spacing-lg);
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: var(--text-secondary);
  font-size: 14px;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-highlight);
  border: 1px solid var(--border-secondary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  transition: var(--transition-fast);
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: var(--border-focus);
}

.form-row {
  display: flex;
  gap: var(--spacing-lg);
}

.form-row .form-group {
  flex: 1;
}

.dialog-footer {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
}
</style>