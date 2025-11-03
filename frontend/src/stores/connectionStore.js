import { reactive } from 'vue'
import { connectionApi, sqlApi } from '../services/api'
import { sessionStorage } from '../utils/storage'

export const connectionStore = reactive({
  connections: [],
  sessions: new Map(), // sessionId -> connectionInfo

  // 加载连接列表
  loadConnections() {
    const saved = localStorage.getItem('db_connections')
    if (saved) {
      this.connections = JSON.parse(saved)
    }
  },

  // 保存连接到localStorage
  saveConnection(connection) {
    const connections = this.connections
    const existingIndex = connections.findIndex(c => c.id === connection.id)

    if (existingIndex >= 0) {
      connections[existingIndex] = connection
    } else {
      if (!connection.id) {
        connection.id = Date.now().toString()
      }
      connections.push(connection)
    }

    this.connections = connections
    localStorage.setItem('db_connections', JSON.stringify(connections))
    return connection
  },

  // 删除连接
  deleteConnection(id) {
    this.connections = this.connections.filter(c => c.id !== id)
    localStorage.setItem('db_connections', JSON.stringify(this.connections))
  },

  // 建立连接
  async connect(connectionInfo) {
    const response = await connectionApi.connect(connectionInfo)
    const sessionId = response.data.sessionId

    const sessionData = {
      sessionId,
      connectionInfo: { ...connectionInfo },
      databases: [],
      currentDatabase: connectionInfo.database || null,
      tables: {}, // database -> [tables]
      tableSchemas: {}, // database.table -> {columns: [], comment: '', columnCount: 0}
      views: {}, // database -> [views]
      procedures: {}, // database -> [procedures]
      functions: {}, // database -> [functions]
      expandedNodes: [] // 记录展开的节点
    }

    this.sessions.set(sessionId, sessionData)
    sessionStorage.save('sessionId', { sessionId })

    // 加载数据库列表
    await this.loadDatabases(sessionId)

    // 如果有指定数据库，加载表列表和结构
    if (connectionInfo.database) {
      await this.loadTables(sessionId, connectionInfo.database)
      // 异步加载所有表结构（用于智能提示）
      this.loadAllTableSchemas(sessionId, connectionInfo.database)
    }

    return sessionId
  },

  // 断开连接
  async disconnect(sessionId) {
    try {
      await connectionApi.disconnect(sessionId)
    } finally {
      this.sessions.delete(sessionId)
      sessionStorage.remove(sessionId)
    }
  },

  // 加载数据库列表
  async loadDatabases(sessionId) {
    try {
      const response = await sqlApi.getDatabases(sessionId)
      const sessionData = this.sessions.get(sessionId)
      if (sessionData) {
        sessionData.databases = response.data.databases
      }
    } catch (error) {
      console.error('加载数据库失败', error)
    }
  },

  // 加载表列表
  async loadTables(sessionId, databaseName) {
    try {
      const response = await sqlApi.getTables(sessionId, databaseName)
      const sessionData = this.sessions.get(sessionId)
      if (sessionData) {
        sessionData.tables[databaseName] = response.data.tables
        // 触发响应式更新
        this.refresh()
      }
    } catch (error) {
      console.error('加载表失败', error)
    }
  },

  // 加载表结构（包括字段信息）
  async loadTableSchema(sessionId, databaseName, tableName) {
    try {
      const sessionData = this.sessions.get(sessionId)
      if (!sessionData) return

      const schemaKey = `${databaseName}.${tableName}`

      // 检查是否已经缓存
      if (sessionData.tableSchemas[schemaKey]) {
        return sessionData.tableSchemas[schemaKey]
      }

      // 获取表结构
      const response = await sqlApi.getTableSchema(sessionId, tableName)
      if (response.data) {
        const schemaInfo = {
          tableName,
          columns: response.data.columns || [],
          comment: response.data.comment || '',
          columnCount: response.data.columnCount || 0
        }

        // 缓存表结构
        sessionData.tableSchemas[schemaKey] = schemaInfo
        this.refresh()

        return schemaInfo
      }
    } catch (error) {
      console.error(`加载表 ${tableName} 结构失败`, error)
    }
  },

  // 批量加载所有表结构（用于智能提示）
  async loadAllTableSchemas(sessionId, databaseName) {
    try {
      const sessionData = this.sessions.get(sessionId)
      if (!sessionData || !sessionData.tables[databaseName]) {
        return
      }

      const tables = sessionData.tables[databaseName]
      const promises = tables.map(tableName =>
        this.loadTableSchema(sessionId, databaseName, tableName)
      )

      await Promise.allSettled(promises)
      console.log(`已加载 ${databaseName} 数据库的所有表结构`)
    } catch (error) {
      console.error('批量加载表结构失败', error)
    }
  },

  // 获取表结构（优先从缓存）
  getTableSchema(sessionId, databaseName, tableName) {
    const sessionData = this.sessions.get(sessionId)
    if (!sessionData) return null

    const schemaKey = `${databaseName}.${tableName}`
    return sessionData.tableSchemas[schemaKey] || null
  },

  // 加载视图列表
  async loadViews(sessionId, databaseName) {
    try {
      const response = await sqlApi.getViews(sessionId, databaseName)
      const sessionData = this.sessions.get(sessionId)
      if (sessionData) {
        sessionData.views[databaseName] = response.data.views
        this.refresh()
      }
    } catch (error) {
      console.error('加载视图失败', error)
    }
  },

  // 加载存储过程列表
  async loadProcedures(sessionId, databaseName) {
    try {
      const response = await sqlApi.getProcedures(sessionId, databaseName)
      const sessionData = this.sessions.get(sessionId)
      if (sessionData) {
        sessionData.procedures[databaseName] = response.data.procedures
        this.refresh()
      }
    } catch (error) {
      console.error('加载存储过程失败', error)
    }
  },

  // 加载函数列表
  async loadFunctions(sessionId, databaseName) {
    try {
      const response = await sqlApi.getFunctions(sessionId, databaseName)
      const sessionData = this.sessions.get(sessionId)
      if (sessionData) {
        sessionData.functions[databaseName] = response.data.functions
        this.refresh()
      }
    } catch (error) {
      console.error('加载函数失败', error)
    }
  },

  // 切换数据库
  async switchDatabase(sessionId, databaseName) {
    await sqlApi.switchDatabase(sessionId, databaseName)
    const sessionData = this.sessions.get(sessionId)
    if (sessionData) {
      sessionData.currentDatabase = databaseName
      // 确保加载新数据库的表列表
      if (!sessionData.tables[databaseName]) {
        await this.loadTables(sessionId, databaseName)
        // 加载完表列表后，异步加载所有表结构（用于智能提示）
        // 不等待，避免阻塞切换数据库
        this.loadAllTableSchemas(sessionId, databaseName)
      }
    }
  },

  // 获取会话信息
  getSession(sessionId) {
    return this.sessions.get(sessionId)
  },

  // 展开/折叠节点
  toggleNode(sessionId, nodeType, value) {
    const sessionData = this.sessions.get(sessionId)
    if (!sessionData) return

    const nodeKey = `${nodeType}:${value}`
    const index = sessionData.expandedNodes.indexOf(nodeKey)
    if (index > -1) {
      sessionData.expandedNodes.splice(index, 1)
    } else {
      sessionData.expandedNodes.push(nodeKey)
    }
  },

  // 检查节点是否展开
  isNodeExpanded(sessionId, nodeType, value) {
    const sessionData = this.sessions.get(sessionId)
    if (!sessionData) return false

    const nodeKey = `${nodeType}:${value}`
    return sessionData.expandedNodes.includes(nodeKey)
  },

  // 恢复之前的会话
  async restoreSession(sessionId) {
    const connectionInfo = sessionStorage.get(sessionId)
    if (connectionInfo) {
      await this.connect(connectionInfo)
      return sessionId
    }
    return null
  },

  // 获取所有活跃会话
  getActiveSessions() {
    return Array.from(this.sessions.entries()).map(([sessionId, data]) => ({
      sessionId,
      ...data
    }))
  },

  // 强制刷新（用于触响应式更新）
  refresh() {
    // 触发响应式更新
    this.sessions = new Map(this.sessions)
  }
})