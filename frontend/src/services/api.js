import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000
})

export const connectionApi = {
  connect(data) {
    return api.post('/connections/connect', data)
  },

  test(data) {
    return api.post('/connections/test', data)
  },

  disconnect(sessionId) {
    return api.post(`/connections/${sessionId}/disconnect`)
  },

  getStatus(sessionId) {
    return api.get(`/connections/${sessionId}/status`)
  }
}

export const sqlApi = {
  query(sessionId, sql) {
    return api.post('/sql/query', { sessionId, sql })
  },

  execute(sessionId, sql) {
    return api.post('/sql/execute', { sessionId, sql })
  },

  getTables(sessionId) {
    return api.get(`/sql/tables/${sessionId}`)
  }
}