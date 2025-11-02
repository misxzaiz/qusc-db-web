<template>
  <div class="role-manager">
    <div class="role-header">
      <h3>AI角色管理</h3>
      <button class="btn btn-primary btn-small" @click="showCreateDialog = true">
        <font-awesome-icon icon="plus" /> 创建角色
      </button>
    </div>

    <div class="role-list">
      <div
        v-for="role in roles"
        :key="role.id"
        class="role-card"
        :class="{ active: selectedRoleId === role.id }"
        @click="selectRole(role)"
      >
        <div class="role-avatar">{{ role.avatar || '🤖' }}</div>
        <div class="role-info">
          <div class="role-name">{{ role.name }}</div>
          <div class="role-desc">{{ role.description }}</div>
        </div>
        <div class="role-actions" v-if="role.isCustom">
          <button class="btn-icon" @click.stop="editRole(role)" title="编辑">
            <font-awesome-icon icon="edit" />
          </button>
          <button class="btn-icon danger" @click.stop="deleteRole(role)" title="删除">
            <font-awesome-icon icon="trash" />
          </button>
        </div>
      </div>
    </div>

    <!-- 创建/编辑角色对话框 -->
    <div v-if="showCreateDialog || editingRole" class="dialog-overlay" @click="closeDialog">
      <div class="dialog" @click.stop>
        <div class="dialog-header">
          <h3>{{ editingRole ? '编辑角色' : '创建角色' }}</h3>
          <button class="close-btn" @click="closeDialog">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label>角色名称</label>
            <input v-model="roleForm.name" type="text" placeholder="输入角色名称" />
          </div>
          <div class="form-group">
            <label>头像</label>
            <input v-model="roleForm.avatar" type="text" placeholder="输入emoji，如：🧙‍♂️" maxlength="2" />
          </div>
          <div class="form-group">
            <label>描述</label>
            <input v-model="roleForm.description" type="text" placeholder="简短描述角色特点" />
          </div>
          <div class="form-group">
            <label>系统提示词</label>
            <textarea
              v-model="roleForm.systemPrompt"
              placeholder="定义角色的行为和回答风格..."
              rows="4"
            ></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn" @click="closeDialog">取消</button>
          <button class="btn btn-primary" @click="saveRole">
            {{ editingRole ? '保存' : '创建' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'RoleManager',
  props: {
    selectedRoleId: String
  },
  emits: ['select-role'],
  data() {
    return {
      roles: [],
      showCreateDialog: false,
      editingRole: null,
      roleForm: {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      }
    }
  },
  mounted() {
    this.loadRoles()
  },
  methods: {
    async loadRoles() {
      try {
        const response = await fetch('/api/ai/roles')
        this.roles = await response.json()
      } catch (error) {
        console.error('加载角色失败:', error)
      }
    },

    selectRole(role) {
      this.$emit('select-role', role.id)
    },

    editRole(role) {
      this.editingRole = role
      this.roleForm = { ...role }
    },

    async deleteRole(role) {
      if (!confirm(`确定要删除角色 "${role.name}" 吗？`)) return

      try {
        const response = await fetch(`/api/ai/roles/${role.id}`, {
          method: 'DELETE'
        })
        if (response.ok) {
          await this.loadRoles()
          if (this.selectedRoleId === role.id) {
            this.$emit('select-role', null)
          }
        }
      } catch (error) {
        console.error('删除角色失败:', error)
        alert('删除失败')
      }
    },

    async saveRole() {
      if (!this.roleForm.name || !this.roleForm.systemPrompt) {
        alert('请填写角色名称和系统提示词')
        return
      }

      try {
        const url = this.editingRole ? `/api/ai/roles/${this.editingRole.id}` : '/api/ai/roles'
        const method = this.editingRole ? 'PUT' : 'POST'

        const response = await fetch(url, {
          method,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(this.roleForm)
        })

        if (response.ok) {
          await this.loadRoles()
          this.closeDialog()
        } else {
          const error = await response.json()
          alert(error.error || '保存失败')
        }
      } catch (error) {
        console.error('保存角色失败:', error)
        alert('保存失败')
      }
    },

    closeDialog() {
      this.showCreateDialog = false
      this.editingRole = null
      this.roleForm = {
        name: '',
        avatar: '',
        description: '',
        systemPrompt: ''
      }
    }
  }
}
</script>

<style scoped>
.role-manager {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.role-header {
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}

.role-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-sm);
}

.role-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition-fast);
}

.role-card:hover {
  background-color: var(--bg-highlight);
}

.role-card.active {
  border-color: var(--accent-primary);
  background-color: var(--accent-primary-bg);
}

.role-avatar {
  font-size: 32px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: var(--bg-tertiary);
  border-radius: 50%;
  flex-shrink: 0;
}

.role-info {
  flex: 1;
  min-width: 0;
}

.role-name {
  color: var(--text-primary);
  font-weight: 600;
  margin-bottom: 4px;
}

.role-desc {
  color: var(--text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-actions {
  display: flex;
  gap: var(--spacing-xs);
  opacity: 0;
  transition: opacity 0.2s;
}

.role-card:hover .role-actions {
  opacity: 1;
}

.btn-icon.danger {
  color: var(--danger-color);
}

.btn-icon.danger:hover {
  background-color: var(--danger-bg);
}

/* 对话框样式 */
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
  max-height: 90%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-header h3 {
  margin: 0;
  color: var(--text-primary);
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
}

.dialog-body {
  padding: var(--spacing-lg);
  overflow-y: auto;
  flex: 1;
}

.form-group {
  margin-bottom: var(--spacing-md);
}

.form-group label {
  display: block;
  margin-bottom: var(--spacing-xs);
  color: var(--text-primary);
  font-weight: 500;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.form-group textarea {
  resize: vertical;
  min-height: 100px;
}

.dialog-footer {
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-primary);
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.btn {
  padding: 8px 16px;
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  cursor: pointer;
  transition: var(--transition-fast);
}

.btn:hover {
  background-color: var(--bg-highlight);
}

.btn-primary {
  background-color: var(--accent-primary);
  color: white;
  border-color: var(--accent-primary);
}

.btn-primary:hover {
  background-color: var(--accent-primary-hover);
}

.btn-small {
  padding: 4px 8px;
  font-size: 13px;
}
</style>