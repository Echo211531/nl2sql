import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Message, DatasourceConfig, SchemaInfo } from '../api'
import { datasourceApi } from '../api'

export const useChatStore = defineStore('chat', () => {
  const messages = ref<Message[]>([])
  const loading = ref(false)

  const addMessage = (msg: Message) => {
    messages.value.push(msg)
  }

  const clearMessages = () => {
    messages.value = []
  }

  return { messages, loading, addMessage, clearMessages }
})

export const useDatasourceStore = defineStore('datasource', () => {
  const currentConfig = ref<DatasourceConfig | null>(null)
  const schemaInfo = ref<SchemaInfo[]>([])
  const loading = ref(false)

  const loadCurrentConfig = async () => {
    try {
      const res = await datasourceApi.getCurrent()
      currentConfig.value = res.data
    } catch {
      currentConfig.value = null
    }
  }

  const loadSchemaInfo = async () => {
    try {
      const res = await datasourceApi.getSchema()
      schemaInfo.value = res.data
    } catch {
      schemaInfo.value = []
    }
  }

  return { currentConfig, schemaInfo, loading, loadCurrentConfig, loadSchemaInfo }
})