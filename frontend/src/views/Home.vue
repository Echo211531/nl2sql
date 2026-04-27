<template>
  <div class="home-container">
    <div class="header">
      <div class="logo">
        <span class="logo-text">NL2SQL</span>
        <span class="logo-subtitle">自然语言转SQL智能助手</span>
      </div>
      <el-button type="primary" @click="goSettings">
        <el-icon><Setting /></el-icon>
        数据源设置
      </el-button>
    </div>

    <div class="chat-container">
      <div class="messages" ref="messagesRef">
        <div v-if="chatStore.messages.length === 0" class="empty-state">
          <el-icon :size="64" color="#409EFF"><ChatDotRound /></el-icon>
          <p>请输入您的问题，我将帮您生成SQL查询语句</p>
          <p class="hint">例如：查询所有用户信息、统计订单总额等</p>
        </div>

        <div v-for="msg in chatStore.messages" :key="msg.id" class="message" :class="msg.type">
          <div v-if="msg.type === 'user'" class="user-message">
            <div class="avatar user-avatar">用户</div>
            <div class="content">{{ msg.content }}</div>
          </div>

          <div v-else-if="msg.type === 'sql'" class="sql-message">
            <div class="avatar bot-avatar">AI</div>
            <div class="content">
              <div class="sql-header">生成的SQL语句：</div>
              <pre class="sql-code">{{ msg.content }}</pre>
            </div>
          </div>

          <div v-else-if="msg.type === 'result'" class="result-message">
            <div class="result-header">
              <el-icon><Document /></el-icon>
              查询结果（共 {{ msg.data?.length || 0 }} 条记录）
            </div>
            <el-table :data="msg.data" stripe border max-height="400" size="small">
              <el-table-column v-for="col in msg.columns" :key="col" :prop="col" :label="col" />
            </el-table>
          </div>

          <div v-else-if="msg.type === 'error'" class="error-message">
            <el-icon><WarningFilled /></el-icon>
            {{ msg.content }}
          </div>
        </div>

        <div v-if="chatStore.loading" class="loading">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          正在生成SQL...
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input
        v-model="inputText"
        placeholder="请输入您的查询问题..."
        :disabled="chatStore.loading"
        @keyup.enter="sendMessage"
        size="large"
      >
        <template #append>
          <el-button :icon="chatStore.loading ? '' : 'Promotion'" @click="sendMessage" :loading="chatStore.loading">
            发送
          </el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useChatStore } from '../stores'
import { queryApi } from '../api'
import { Setting, ChatDotRound, Document, WarningFilled, Loading } from '@element-plus/icons-vue'

const router = useRouter()
const chatStore = useChatStore()
const inputText = ref('')
const messagesRef = ref<HTMLElement | null>(null)
let messageId = 0

const goSettings = () => {
  router.push('/settings')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const sendMessage = async () => {
  if (!inputText.value.trim() || chatStore.loading) return

  const question = inputText.value.trim()
  inputText.value = ''

  chatStore.addMessage({
    id: ++messageId,
    type: 'user',
    content: question
  })
  scrollToBottom()

  chatStore.loading = true
  try {
    const res = await queryApi.query(question)
    const data = res.data

    if (data.success) {
      chatStore.addMessage({
        id: ++messageId,
        type: 'sql',
        content: data.sql
      })
      scrollToBottom()

      if (data.data && data.data.length > 0) {
        chatStore.addMessage({
          id: ++messageId,
          type: 'result',
          content: '',
          data: data.data,
          columns: data.columns
        })
      } else {
        chatStore.addMessage({
          id: ++messageId,
          type: 'result',
          content: '查询结果为空',
          data: [],
          columns: data.columns || []
        })
      }
    } else {
      chatStore.addMessage({
        id: ++messageId,
        type: 'error',
        content: data.errorMessage || '查询失败'
      })
    }
  } catch (e: unknown) {
    chatStore.addMessage({
      id: ++messageId,
      type: 'error',
      content: '请求失败：' + (e instanceof Error ? e.message : '未知错误')
    })
  } finally {
    chatStore.loading = false
    scrollToBottom()
  }
}
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.logo-text {
  font-size: 24px;
  font-weight: bold;
}

.logo-subtitle {
  font-size: 14px;
  margin-left: 12px;
  opacity: 0.9;
}

.chat-container {
  flex: 1;
  overflow: hidden;
  padding: 16px;
}

.messages {
  height: 100%;
  overflow-y: auto;
  background: white;
  border-radius: 8px;
  padding: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-state .hint {
  font-size: 12px;
  margin-top: 8px;
}

.message {
  margin-bottom: 16px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.user-message .content {
  background: #409EFF;
  color: white;
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 70%;
}

.sql-message {
  display: flex;
  gap: 8px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
}

.user-avatar {
  background: #409EFF;
  color: white;
}

.bot-avatar {
  background: #67C23A;
  color: white;
}

.sql-message .content {
  flex: 1;
  background: #f4f4f5;
  padding: 12px;
  border-radius: 8px;
}

.sql-header {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.sql-code {
  background: #2d2d2d;
  color: #f8f8f2;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Consolas', monospace;
  white-space: pre-wrap;
  overflow-x: auto;
}

.result-message {
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #606266;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fef0f0;
  color: #f56c6c;
  padding: 12px;
  border-radius: 8px;
}

.loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  padding: 8px;
}

.input-area {
  padding: 16px 24px;
  background: white;
  border-top: 1px solid #e4e7ed;
}
</style>