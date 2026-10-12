<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { auditApi, familiesApi } from '@/api/modules'
import type { AuditLog, Family } from '@/types/api'

const auditLogsData = ref<AuditLog[]>([])
const familiesData = ref<Family[]>([])
const total = ref(0)
const isLoading = ref(false)
const filters = reactive<{ familyId?: number; page: number; pageSize: number }>({ page: 1, pageSize: 20 })

async function loadAuditLogs(): Promise<void> {
  isLoading.value = true
  try {
    const res = await auditApi.list(filters)
    auditLogsData.value = res.records
    total.value = res.total
  } finally {
    isLoading.value = false
  }
}

async function loadPage(): Promise<void> {
  const res = await familiesApi.list()
  familiesData.value = res
  await loadAuditLogs()
}

onMounted(loadPage)
</script>

<template>
  <section class="page-stack">
    <div class="filters surface"><el-select v-model="filters.familyId" placeholder="全部家庭" clearable filterable><el-option v-for="family in familiesData" :key="family.id" :label="family.name" :value="family.id" /></el-select><el-button :icon="Search" @click="loadAuditLogs">查询</el-button></div>
    <div class="surface table-panel"><el-table v-loading="isLoading" :data="auditLogsData" empty-text="暂无审计日志"><el-table-column prop="createdAt" label="时间" width="190" /><el-table-column prop="familyId" label="家庭 ID" width="100" /><el-table-column prop="operatorId" label="操作者" width="100" /><el-table-column prop="action" label="操作" min-width="150" /><el-table-column prop="resourceType" label="资源类型" min-width="130" /><el-table-column prop="resourceId" label="资源 ID" min-width="110" /><el-table-column label="结果" width="100"><template #default="scope"><el-tag :type="scope.row.result === 'SUCCESS' ? 'success' : 'danger'">{{ scope.row.result }}</el-tag></template></el-table-column></el-table><el-pagination v-if="total > filters.pageSize" v-model:current-page="filters.page" class="pagination" :page-size="filters.pageSize" :total="total" layout="prev, pager, next" @current-change="loadAuditLogs" /></div>
  </section>
</template>

<style scoped>
.filters { display: flex; gap: 10px; padding: 16px; }.filters .el-select { width: 240px; }.table-panel { padding: 12px 18px 18px; }.pagination { justify-content: flex-end; margin-top: 18px; }
</style>
