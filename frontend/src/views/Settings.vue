<template>
  <div class="settings-container">
    <div class="header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h2>数据源配置</h2>
    </div>

    <div class="content">
      <el-card class="config-card">
        <template #header>
          <div class="card-header">
            <el-icon><Connection /></el-icon>
            MySQL数据源连接配置
          </div>
        </template>

        <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
          <el-form-item label="连接名称" prop="name">
            <el-input v-model="form.name" placeholder="请输入连接名称" />
          </el-form-item>
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="请输入MySQL主机地址" />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="数据库" prop="database">
            <el-input v-model="form.database" placeholder="请输入数据库名称" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="testConnection" :loading="testing">
              <el-icon><CircleCheck /></el-icon>
              测试连接
            </el-button>
            <el-button type="success" @click="saveConfig" :loading="saving">
              <el-icon><Check /></el-icon>
              保存配置
            </el-button>
          </el-form-item>
        </el-form>

        <el-alert v-if="testResult" :title="testResult" :type="testSuccess ? 'success' : 'error'" show-icon />
      </el-card>

      <el-card class="schema-card">
        <template #header>
          <div class="card-header">
            <el-icon><Grid /></el-icon>
            数据库表结构
            <el-button size="small" @click="loadSchema" :loading="loadingSchema" style="margin-left: auto">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </template>

        <el-table :data="dsStore.schemaInfo" stripe v-loading="loadingSchema">
          <el-table-column prop="tableName" label="表名" width="200" />
          <el-table-column prop="tableRemarks" label="表说明" />
          <el-table-column prop="columnCount" label="字段数" width="80" />
          <el-table-column label="主键" width="150">
            <template #default="{ row }">
              {{ row.primaryKeys?.join(', ') || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" link @click="showTableDetail(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="detailVisible" :title="`${currentTable?.tableName} 表字段详情`" width="600px">
      <el-table :data="currentTable?.columns" stripe>
        <el-table-column prop="name" label="字段名" width="150" />
        <el-table-column prop="type" label="类型" width="120" />
        <el-table-column label="可空" width="80">
          <template #default="{ row }">
            <el-tag :type="row.nullable ? 'success' : 'danger'" size="small">
              {{ row.nullable ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remarks" label="说明" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Connection, CircleCheck, Check, Grid, Refresh } from '@element-plus/icons-vue'
import { useDatasourceStore } from '../stores'
import { datasourceApi } from '../api'
import type { DatasourceConfig, SchemaInfo } from '../api'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const dsStore = useDatasourceStore()

const formRef = ref<FormInstance>()
const form = reactive<DatasourceConfig>({
  name: '',
  host: '',
  port: 3306,
  database: '',
  username: '',
  password: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
  database: [{ required: true, message: '请输入数据库名称', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

const testing = ref(false)
const saving = ref(false)
const testResult = ref('')
const testSuccess = ref(false)
const loadingSchema = ref(false)
const detailVisible = ref(false)
const currentTable = ref<SchemaInfo | null>(null)

onMounted(async () => {
  await dsStore.loadCurrentConfig()
  if (dsStore.currentConfig) {
    Object.assign(form, dsStore.currentConfig)
  }
  await loadSchema()
})

const goBack = () => {
  router.push('/')
}

const testConnection = async () => {
  await formRef.value?.validate()
  testing.value = true
  testResult.value = ''
  try {
    const res = await datasourceApi.test(form)
    testSuccess.value = res.data.success
    testResult.value = res.data.message
  } catch (e: unknown) {
    testSuccess.value = false
    testResult.value = '连接测试失败：' + (e instanceof Error ? e.message : '未知错误')
  } finally {
    testing.value = false
  }
}

const saveConfig = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const res = await datasourceApi.save(form)
    if (res.data.success) {
      ElMessage.success('数据源配置保存成功')
      await loadSchema()
    } else {
      ElMessage.error(res.data.message)
    }
  } catch (e: unknown) {
    ElMessage.error('保存失败：' + (e instanceof Error ? e.message : '未知错误'))
  } finally {
    saving.value = false
  }
}

const loadSchema = async () => {
  loadingSchema.value = true
  try {
    await dsStore.loadSchemaInfo()
  } finally {
    loadingSchema.value = false
  }
}

const showTableDetail = (table: SchemaInfo) => {
  currentTable.value = table
  detailVisible.value = true
}
</script>

<style scoped>
.settings-container {
  min-height: 100vh;
  background: #f5f7fa;
}

.header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: white;
  border-bottom: 1px solid #e4e7ed;
}

.header h2 {
  margin: 0;
}

.content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-card, .schema-card {
  max-width: 800px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.schema-card .card-header {
  width: 100%;
}
</style>