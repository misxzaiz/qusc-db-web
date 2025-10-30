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
.connection-manager {
  height: 100%;
  padding: 20px;
  overflow-y: auto;
}

.toolbar {
  margin-bottom: 20px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #0e639c;
  color: white;
}

.btn-primary:hover {
  background-color: #1177bb;
}

.btn-secondary {
  background-color: #3c3c3c;
  color: #d4d4d4;
}

.btn-secondary:hover {
  background-color: #464647;
}

.btn-danger {
  background-color: #c41e3a;
  color: white;
}

.btn-danger:hover {
  background-color: #f14c4c;
}

.btn-small {
  padding: 4px 8px;
  font-size: 12px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #969696;
}

.empty-hint {
  margin-top: 10px;
  font-size: 14px;
}

.connection-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 20px;
}

.connection-card {
  background-color: #252526;
  border: 1px solid #3e3e42;
  border-radius: 6px;
  overflow: hidden;
}

.connection-card:hover {
  border-color: #569cd6;
}

.card-header {
  padding: 16px;
  background-color: #2d2d30;
  border-bottom: 1px solid #3e3e42;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  margin: 0;
  color: #569cd6;
  font-size: 16px;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.card-body {
  padding: 16px;
}

.connection-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-item {
  display: flex;
  gap: 10px;
}

.label {
  color: #969696;
  min-width: 60px;
}

.value {
  color: #d4d4d4;
  font-family: 'Consolas', monospace;
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
  background-color: #2d2d30;
  border: 1px solid #3e3e42;
  border-radius: 6px;
  width: 500px;
  max-width: 90%;
}

.dialog-header {
  padding: 16px;
  border-bottom: 1px solid #3e3e42;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-header h2 {
  margin: 0;
  color: #d4d4d4;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  color: #969696;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #d4d4d4;
}

.dialog-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: #969696;
  font-size: 14px;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  background-color: #3c3c3c;
  border: 1px solid #5a5a5a;
  border-radius: 4px;
  color: #d4d4d4;
  font-size: 14px;
}

.form-group input:focus,
.form-group select:focus {
  outline: none;
  border-color: #569cd6;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .form-group {
  flex: 1;
}

.dialog-footer {
  padding: 16px;
  border-top: 1px solid #3e3e42;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>