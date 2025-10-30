const STORAGE_KEY = 'db_connections'

export const connectionStorage = {
  getAll() {
    const data = localStorage.getItem(STORAGE_KEY)
    return data ? JSON.parse(data) : []
  },

  save(connection) {
    const connections = this.getAll()
    const existingIndex = connections.findIndex(c => c.id === connection.id)

    if (existingIndex >= 0) {
      connections[existingIndex] = connection
    } else {
      connection.id = Date.now().toString()
      connections.push(connection)
    }

    localStorage.setItem(STORAGE_KEY, JSON.stringify(connections))
    return connection
  },

  delete(id) {
    const connections = this.getAll()
    const filtered = connections.filter(c => c.id !== id)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered))
  },

  getById(id) {
    const connections = this.getAll()
    return connections.find(c => c.id === id)
  }
}

export const sessionStorage = {
  save(sessionId, connectionInfo) {
    localStorage.setItem(`session_${sessionId}`, JSON.stringify(connectionInfo))
  },

  get(sessionId) {
    const data = localStorage.getItem(`session_${sessionId}`)
    return data ? JSON.parse(data) : null
  },

  remove(sessionId) {
    localStorage.removeItem(`session_${sessionId}`)
  }
}