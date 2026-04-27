import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

export interface DatasourceConfig {
  name: string
  host: string
  port: number
  database: string
  username: string
  password: string
}

export interface DatasourceResponse {
  success: boolean
  message: string
}

export interface SchemaInfo {
  tableName: string
  tableRemarks: string
  columnCount: number
  columns: ColumnInfo[]
  primaryKeys: string[]
}

export interface ColumnInfo {
  name: string
  type: string
  remarks: string
  nullable: boolean
}

export interface DataQueryResponse {
  success: boolean
  sql: string
  columns: string[]
  data: Record<string, unknown>[]
  errorMessage: string
}

export interface Message {
  id: number
  type: 'user' | 'sql' | 'result' | 'error'
  content: string
  data?: Record<string, unknown>[]
  columns?: string[]
}

export const datasourceApi = {
  getCurrent: () => api.get<DatasourceConfig>('/datasource'),
  save: (config: DatasourceConfig) => api.post<DatasourceResponse>('/datasource', config),
  test: (config: DatasourceConfig) => api.post<DatasourceResponse>('/datasource/test', config),
  delete: () => api.delete<DatasourceResponse>('/datasource'),
  getSchema: () => api.get<SchemaInfo[]>('/datasource/schema'),
  reloadSchema: () => api.post<DatasourceResponse>('/datasource/reload')
}

export const queryApi = {
  query: (question: string) => api.get<DataQueryResponse>('/data/query', { params: { question } })
}

export default api