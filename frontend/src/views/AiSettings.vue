<template>
  <div class="ai-settings">
    <div class="header">
      <h1>AI服务设置</h1>
      <p>配置AI服务以使用SQL生成、解释和优化功能</p>
    </div>

    <div class="content">
      <!-- 添加新配置 -->
      <div class="section">
        <h2>添加AI配置</h2>
        <div class="config-form">
          <div class="form-row">
            <div class="form-group">
              <label>配置名称</label>
              <input v-model="formData.name" type="text" placeholder="例如：DeepSeek Chat" />
            </div>
            <div class="form-group">
              <label>提供商</label>
              <select v-model="formData.provider" @change="onProviderChange">
                <option value="">选择提供商</option>
                <option value="deepseek">DeepSeek</option>
                                <option value="Iflow">心流</option>
                <option value="openai">OpenAI</option>
                <option value="custom">自定义</option>
              </select>
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>API Key</label>
              <input v-model="formData.apiKey" type="password" placeholder="输入API密钥" />
            </div>
            <div class="form-group">
              <label>模型</label>
              <div class="model-input-group">
                <select
                  v-model="selectedModel"
                  @change="onModelSelect"
                  :disabled="!formData.provider"
                  class="model-select"
                >
                  <option value="">选择预设模型</option>
                  <option v-for="model in availableModels" :key="model" :value="model">
                    {{ model }}
                  </option>
                  <option value="custom">-- 自定义模型 --</option>
                </select>
                <input
                  v-model="formData.model"
                  type="text"
                  placeholder="或输入自定义模型名称"
                  class="model-input"
                />
              </div>
              <small class="help-text">
                可以从下拉列表选择，或直接输入模型名称（如：gpt-4-turbo、claude-3-opus等）
              </small>
            </div>
          </div>

          <div v-if="formData.provider === 'custom'" class="form-group">
            <label>API地址</label>
            <input v-model="formData.baseUrl" type="text" placeholder="https://api.example.com/v1" />
          </div>

          <div class="form-row">
            <div class="form-group">
              <label>温度 (0-1)</label>
              <input v-model.number="formData.temperature" type="number" min="0" max="1" step="0.1" />
            </div>
            <div class="form-group">
              <label>最大Token数</label>
              <input v-model.number="formData.maxTokens" type="number" min="100" max="8000" step="100" />
            </div>
          </div>

          <div class="form-actions">
            <button class="btn btn-secondary" @click="resetForm">重置</button>
            <button class="btn btn-primary" @click="testConfig" :disabled="!canTest">
              测试连接
            </button>
            <button class="btn btn-primary" @click="saveConfig" :disabled="!canSave">
              {{ editingConfig ? '更新' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 已保存的配置 -->
      <div class="section">
        <h2>已保存的配置</h2>
        <div v-if="configs.length === 0" class="empty-state">
          <p>暂无AI配置</p>
        </div>
        <div v-else class="config-list">
          <div v-for="config in configs" :key="config.id" class="config-card">
            <div class="config-header">
              <h3>{{ config.name }}</h3>
              <div class="config-actions">
                <button class="btn btn-small" @click="editConfig(config)">编辑</button>
                <button class="btn btn-small" @click="testConfigById(config.id)">测试</button>
                <button class="btn btn-small btn-danger" @click="deleteConfig(config.id)">删除</button>
              </div>
            </div>
            <div class="config-body">
              <div class="config-info">
                <div class="info-item">
                  <span class="label">提供商:</span>
                  <span class="value">{{ getProviderName(config.provider) }}</span>
                </div>
                <div class="info-item">
                  <span class="label">模型:</span>
                  <span class="value">{{ config.model }}</span>
                </div>
                <div class="info-item">
                  <span class="label">API地址:</span>
                  <span class="value">{{ config.baseUrl }}</span>
                </div>
                <div class="info-item">
                  <span class="label">状态:</span>
                  <span class="value" :class="{ enabled: config.enabled }">
                    {{ config.enabled ? '启用' : '禁用' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 使用说明 -->
      <div class="section">
        <h2>使用说明</h2>
        <div class="usage-guide">
          <h3>支持的AI提供商</h3>
          <ul>
            <li><strong>DeepSeek</strong> - 需要API Key，地址: https://api.deepseek.com</li>
                        <li><strong>心流</strong> - 需要API Key，地址: https://apis.iflow.cn/v1</li>
            <li><strong>OpenAI</strong> - 需要API Key，地址: https://api.openai.com/v1</li>
            <li><strong>自定义</strong> - 支持任何OpenAI兼容的API</li>
          </ul>

          <h3>模型配置</h3>
          <ul>
            <li>支持从预设列表选择模型</li>
            <li>支持手动输入任意模型名称</li>
            <li>常见模型示例：gpt-4、gpt-4-turbo、claude-3-opus、claude-3-sonnet、gemini-pro等</li>
            <li>只要API兼容OpenAI格式，都可以使用</li>
          </ul>

          <h3>API Key 获取</h3>
          <ul>
            <li><strong>DeepSeek</strong>: 访问 <a href="https://platform.deepseek.com" target="_blank">https://platform.deepseek.com</a> 注册并获取API Key</li>
                        <li><strong>心流</strong>: 访问 <a href="https://www.iflow.cn/" target="_blank">https://www.iflow.cn/</a> 注册并获取API Key</li>
            <li><strong>OpenAI</strong>: 访问 <a href="https://platform.openai.com" target="_blank">https://platform.openai.com</a> 创建API Key</li>
          </ul>

          <h3>常见错误</h3>
          <ul>
            <li><strong>401 Unauthorized</strong>: API Key错误或未配置</li>
            <li><strong>403 Forbidden</strong>: 权限不足或配额用完</li>
            <li><strong>429 Too Many Requests</strong>: 调用频率超限</li>
            <li><strong>Connection Error</strong>: 网络问题或API地址错误</li>
          </ul>

          <h3>功能说明</h3>
          <ul>
            <li><strong>SQL生成</strong> - 根据自然语言描述生成SQL语句</li>
            <li><strong>SQL解释</strong> - 解释SQL语句的作用和执行逻辑</li>
            <li><strong>SQL优化</strong> - 提供SQL性能优化建议</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { aiApi } from '../services/aiApi'

export default {
  name: 'AiSettings',

  data() {
    return {
      configs: [],
      providers: {},
      editingConfig: null,
      selectedModel: '',
      formData: {
        name: '',
        provider: '',
        apiKey: '',
        baseUrl: '',
        model: '',
        temperature: 0.7,
        maxTokens: 4000,
        enabled: true
      },
      testing: false
    }
  },

  computed: {
    availableModels() {
      if (!this.formData.provider || !this.providers[this.formData.provider]) {
        return []
      }
      return this.providers[this.formData.provider].models || []
    },

    canTest() {
      return this.formData.provider && this.formData.apiKey && this.formData.model
    },

    canSave() {
      return this.formData.name && this.formData.provider && this.formData.apiKey && this.formData.model
    }
  },

  mounted() {
    this.loadConfigs()
    this.loadProviders()
  },

  methods: {
    async loadConfigs() {
      try {
        const response = await aiApi.getConfigs()
        this.configs = response.data

        // 如果localStorage中有配置，同步到后端
        if (this.configs.length > 0) {
          aiApi.syncConfigsToBackend().then(() => {
            console.log('AI配置已同步到后端')
          }).catch(error => {
            console.warn('同步AI配置到后端时出现错误:', error)
          })
        }
      } catch (error) {
        console.error('加载配置失败', error)
      }
    },

    async loadProviders() {
      try {
        const response = await aiApi.getProviders()
        this.providers = response.data
      } catch (error) {
        console.error('加载提供商失败', error)
      }
    },

    onProviderChange() {
      const provider = this.providers[this.formData.provider]
      if (provider) {
        // 自动填入baseUrl，确保使用正确的API地址
        this.formData.baseUrl = provider.baseUrl
        this.formData.model = provider.defaultModel || ''
        this.selectedModel = provider.defaultModel || ''

              } else {
        this.selectedModel = ''
        this.formData.baseUrl = ''
      }
    },

    onModelSelect() {
      if (this.selectedModel === 'custom') {
        // 选择自定义时，清空模型输入框让用户自行输入
        this.formData.model = ''
      } else {
        // 选择预设模型时，更新模型输入框
        this.formData.model = this.selectedModel
      }
    },

    async testConfig() {
      this.testing = true
      try {
        await aiApi.saveConfig({ ...this.formData, id: 'test' })
        const response = await aiApi.testConfig('test')
        alert(response.data.success ? '连接成功！' : '连接失败')
      } catch (error) {
        alert('测试失败: ' + (error.response?.data?.error || error.message))
      } finally {
        this.testing = false
      }
    },

    async testConfigById(id) {
      try {
        const response = await aiApi.testConfig(id)
        alert(response.data.success ? '连接成功！' : '连接失败')
      } catch (error) {
        alert('测试失败: ' + (error.response?.data?.error || error.message))
      }
    },

    async saveConfig() {
      try {
        if (this.editingConfig) {
          await aiApi.saveConfig({ ...this.formData, id: this.editingConfig.id })
        } else {
          await aiApi.saveConfig(this.formData)
        }
        alert(this.editingConfig ? '更新成功' : '保存成功')
        this.resetForm()
        this.loadConfigs()
      } catch (error) {
        alert('保存失败: ' + (error.response?.data?.error || error.message))
      }
    },

    editConfig(config) {
      this.editingConfig = config
      this.formData = { ...config }
      // 检查是否是预设模型，如果是则设置selectedModel
      const provider = this.providers[config.provider]
      if (provider && provider.models && provider.models.includes(config.model)) {
        this.selectedModel = config.model
      } else {
        this.selectedModel = 'custom'
      }
    },

    async deleteConfig(id) {
      if (!confirm('确定要删除这个配置吗？')) return

      try {
        await aiApi.deleteConfig(id)
        alert('删除成功')
        this.loadConfigs()
      } catch (error) {
        alert('删除失败: ' + (error.response?.data?.error || error.message))
      }
    },

    resetForm() {
      this.editingConfig = null
      this.selectedModel = ''
      this.formData = {
        name: '',
        provider: '',
        apiKey: '',
        baseUrl: '',
        model: '',
        temperature: 0.7,
        maxTokens: 4000,
        enabled: true
      }
    },

    getProviderName(provider) {
      const names = {
        deepseek: 'DeepSeek',
        Iflow: '心流',
        openai: 'OpenAI',
        custom: '自定义'
      }
      return names[provider] || provider
    }
  }
}
</script>

<style scoped>
@import '../styles/theme.css';

.ai-settings {
  height: 100%;
  overflow-y: auto;
  padding: var(--spacing-xl);
}

.header {
  margin-bottom: var(--spacing-xl);
}

.header h1 {
  margin: 0 0 var(--spacing-sm) 0;
  color: var(--text-primary);
}

.header p {
  margin: 0;
  color: var(--text-secondary);
}

.content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

.section {
  background-color: var(--bg-secondary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
}

.section h2 {
  margin: 0 0 var(--spacing-lg) 0;
  color: var(--text-primary);
  font-size: 18px;
}

.config-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.form-row {
  display: flex;
  gap: var(--spacing-lg);
}

.form-row .form-group {
  flex: 1;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.form-group input,
.form-group select,
.form-group textarea {
  padding: 8px 12px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
}

.form-group input:focus,
.form-group select:focus,
.form-group textarea:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.form-actions {
  display: flex;
  gap: var(--spacing-md);
  justify-content: flex-end;
}

.model-input-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.model-select {
  padding: 8px 12px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
}

.model-input {
  padding: 8px 12px;
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 14px;
  font-family: var(--font-family-mono);
}

.model-input:focus {
  outline: none;
  border-color: var(--accent-primary);
}

.help-text {
  color: var(--text-tertiary);
  font-size: 12px;
  margin-top: 4px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 14px;
  transition: var(--transition-fast);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background-color: var(--btn-primary-bg);
  color: var(--btn-primary-text);
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--btn-primary-hover);
}

.btn-secondary {
  background-color: var(--btn-secondary-bg);
  color: var(--btn-secondary-text);
}

.btn-secondary:hover:not(:disabled) {
  background-color: var(--btn-secondary-hover);
}

.btn-danger {
  background-color: var(--btn-danger-bg);
  color: var(--btn-danger-text);
}

.btn-danger:hover:not(:disabled) {
  background-color: var(--btn-danger-hover);
}

.btn-small {
  padding: 4px 8px;
  font-size: 12px;
}

.empty-state {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-secondary);
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.config-card {
  background-color: var(--bg-primary);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.config-header {
  padding: var(--spacing-lg);
  background-color: var(--bg-tertiary);
  border-bottom: 1px solid var(--border-primary);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}

.config-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.config-body {
  padding: var(--spacing-lg);
}

.config-info {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: var(--spacing-md);
}

.info-item {
  display: flex;
  gap: 8px;
}

.info-item .label {
  color: var(--text-secondary);
  min-width: 80px;
}

.info-item .value {
  color: var(--text-primary);
  font-family: var(--font-family-mono);
}

.info-item .value.enabled {
  color: var(--success);
}

.usage-guide {
  color: var(--text-secondary);
}

.usage-guide h3 {
  color: var(--text-primary);
  margin: var(--spacing-lg) 0 var(--spacing-md) 0;
}

.usage-guide ul {
  margin: 0 0 var(--spacing-lg) 0;
  padding-left: 20px;
}

.usage-guide li {
  margin-bottom: 4px;
}

.usage-guide a {
  color: var(--accent-primary);
  text-decoration: none;
}

.usage-guide a:hover {
  text-decoration: underline;
}

/* API Key输入框样式 */
.form-group input[type="password"] {
  font-family: var(--font-family-mono);
  letter-spacing: 0.5px;
}
</style>