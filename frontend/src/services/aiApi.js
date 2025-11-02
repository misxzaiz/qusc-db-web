import axios from 'axios'

const api = axios.create({
  baseURL: '/api/ai',
  timeout: 60000 // AI请求可能需要更长时间
})

// LocalStorage键名
const AI_CONFIGS_KEY = 'ai-configs'

// LocalStorage操作
const storage = {
  getConfigs() {
    try {
      const configs = localStorage.getItem(AI_CONFIGS_KEY)
      return configs ? JSON.parse(configs) : []
    } catch (error) {
      console.error('读取AI配置失败:', error)
      return []
    }
  },

  saveConfigs(configs) {
    try {
      localStorage.setItem(AI_CONFIGS_KEY, JSON.stringify(configs))
      return true
    } catch (error) {
      console.error('保存AI配置失败:', error)
      return false
    }
  },

  addConfig(config) {
    const configs = this.getConfigs()
    // 如果没有ID，生成一个
    if (!config.id) {
      config.id = Date.now().toString()
    }
    // 检查是否已存在，如果存在则更新
    const index = configs.findIndex(c => c.id === config.id)
    if (index >= 0) {
      configs[index] = config
    } else {
      configs.push(config)
    }
    this.saveConfigs(configs)
    return config
  },

  removeConfig(id) {
    const configs = this.getConfigs()
    const filtered = configs.filter(c => c.id !== id)
    this.saveConfigs(filtered)
    return true
  }
}

export const aiApi = {
  // 生成SQL
  generateSql(input, configId) {
    // 先确保配置已同步到后端
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/generate-sql', { input, configId })
    })
  },

  // 解释SQL
  explainSql(sql, configId) {
    // 先确保配置已同步到后端
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/explain-sql', { sql, configId })
    })
  },

  // 优化SQL
  optimizeSql(sql, configId) {
    // 先确保配置已同步到后端
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/optimize-sql', { sql, configId })
    })
  },

  // 生成CRUD
  generateCrud(tableName, columns, configId) {
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/generate-crud', { tableName, columns, configId })
    })
  },

  // 生成测试数据
  generateTestData(tableName, columns, rowCount, configId) {
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/generate-test-data', { tableName, columns, rowCount, configId })
    })
  },

  // 解释执行计划
  explainQueryPlan(sql, explainResult, configId) {
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/explain-query-plan', { sql, explainResult, configId })
    })
  },

  // 获取AI提供商列表
  getProviders() {
    return api.get('/providers')
  },

  // 保存AI配置到localStorage和后端
  saveConfig(config) {
    // 保存到localStorage
    const savedConfig = storage.addConfig(config)

    // 同时保存到后端（用于测试连接）
    return api.post('/config', savedConfig).then(response => {
      return response
    }).catch(error => {
      // 如果后端保存失败，localStorage已经保存了，不影响使用
      console.warn('后端保存配置失败，但本地已保存:', error)
      return { data: { success: true, id: savedConfig.id } }
    })
  },

  // 从localStorage获取所有AI配置
  getConfigs() {
    const configs = storage.getConfigs()
    return Promise.resolve({ data: configs })
  },

  // 删除AI配置
  deleteConfig(id) {
    // 从localStorage删除
    storage.removeConfig(id)

    // 同时从后端删除
    return api.delete(`/config/${id}`).catch(error => {
      console.warn('后端删除配置失败，但本地已删除:', error)
      return { data: { success: true } }
    })
  },

  // 确保配置同步到后端（在使用前调用）
  ensureSyncedToBackend() {
    const configs = storage.getConfigs()
    if (configs.length === 0) {
      return Promise.resolve()
    }

    // 批量同步所有配置到后端
    const promises = configs.map(config =>
      api.post('/config', config).catch(error => {
        console.warn(`同步配置 ${config.id} 失败:`, error)
      })
    )
    return Promise.all(promises)
  },

  // 测试AI配置（需要后端支持）
  testConfig(id) {
    // 先将配置同步到后端进行测试
    const configs = storage.getConfigs()
    const config = configs.find(c => c.id === id)
    if (!config) {
      return Promise.reject(new Error('配置不存在'))
    }

    // 先保存配置到后端，然后测试
    return api.post('/config', config).then(() => {
      return api.post(`/config/${id}/test`)
    })
  },

  // 获取单个配置
  getConfig(id) {
    const configs = storage.getConfigs()
    const config = configs.find(c => c.id === id)
    if (!config) {
      return Promise.reject(new Error('配置不存在'))
    }
    return Promise.resolve({ data: config })
  },

  // 流式聊天
  streamChat(message, configId, systemPrompt = '', history = [], tableContexts = []) {
    // 先确保配置同步到后端
    return this.ensureSyncedToBackend().then(() => {
      // 使用fetch发送POST请求并处理流式响应
      const url = `/api/ai/chat/stream`
      const data = {
        message,
        configId,
        systemPrompt,
        history,
        tableContexts
      }

      return fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(data)
      }).then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()

        // 创建一个模拟EventSource的对象
        const mockEventSource = {
          onmessage: null,
          onerror: null,
          close: () => {
            reader.cancel()
          }
        }

        // 读取流
        const readStream = async () => {
          try {
            while (true) {
              const { done, value } = await reader.read()
              if (done) {
                if (mockEventSource.onmessage) {
                  mockEventSource.onmessage({ data: '[DONE]' })
                }
                break
              }

              const chunk = decoder.decode(value, { stream: true })
              const lines = chunk.split('\n')

              for (const line of lines) {
                if (line.startsWith('data: ')) {
                  const data = line.slice(6)
                  if (data && data !== '[DONE]') {
                    if (mockEventSource.onmessage) {
                      mockEventSource.onmessage({ data })
                    }
                  }
                }
              }
            }
          } catch (error) {
            console.error('Stream error:', error)
            if (mockEventSource.onerror) {
              mockEventSource.onerror(error)
            }
          }
        }

        readStream()
        return mockEventSource
      })
    })
  },

  // 普通聊天
  freeChat(message, configId, systemPrompt = '', history = [], tableContexts = []) {
    return this.ensureSyncedToBackend().then(() => {
      return api.post('/chat/free', {
        message,
        configId,
        systemPrompt,
        history,
        tableContexts
      })
    })
  },

  // 同步配置到后端（可选，用于将所有本地配置同步到后端）
  syncConfigsToBackend() {
    const configs = storage.getConfigs()
    const promises = configs.map(config =>
      api.post('/config', config).catch(error => {
        console.warn(`同步配置 ${config.id} 失败:`, error)
      })
    )
    return Promise.all(promises)
  }
}