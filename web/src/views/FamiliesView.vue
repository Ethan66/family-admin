<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { familiesApi, type FamilyMember } from '@/api/modules'
import type { Family } from '@/types'
const rows = ref<Family[]>([]),
  loading = ref(false),
  dialog = ref(false),
  membersVisible = ref(false),
  memberDialog = ref(false),
  members = ref<FamilyMember[]>([]),
  current = ref<Family | null>(null),
  editingMember = ref<FamilyMember | null>(null)
const form = reactive({ name: '', remark: '' })
const memberForm = reactive({
  username: '',
  displayName: '',
  password: '',
  active: true,
})
async function load() {
  loading.value = true
  try {
    rows.value = await familiesApi.list()
  } finally {
    loading.value = false
  }
}
async function save() {
  await familiesApi.create(form)
  dialog.value = false
  Object.assign(form, { name: '', remark: '' })
  ElMessage.success('家庭已创建')
  load()
}
async function toggle(row: Family) {
  await familiesApi.changeStatus(row.id, row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')
  load()
}
async function showMembers(row: Family) {
  current.value = row
  members.value = await familiesApi.members(row.id)
  membersVisible.value = true
}
function view(row: Family) {
  localStorage.setItem('family-admin-selected-family', String(row.id))
  location.href = '/dashboard'
}
onMounted(load)
function openMember(row?: FamilyMember) {
  editingMember.value = row || null
  Object.assign(
    memberForm,
    row
      ? {
          username: row.username,
          displayName: row.displayName,
          password: '',
          active: row.status === 'ACTIVE',
        }
      : { username: '', displayName: '', password: '', active: true },
  )
  memberDialog.value = true
}
async function saveMember() {
  if (!current.value) return
  if (editingMember.value)
    await familiesApi.updateMember(current.value.id, editingMember.value.userId, {
      displayName: memberForm.displayName,
      active: memberForm.active,
    })
  else
    await familiesApi.createMember(current.value.id, {
      username: memberForm.username,
      displayName: memberForm.displayName,
      password: memberForm.password,
    })
  memberDialog.value = false
  members.value = await familiesApi.members(current.value.id)
  ElMessage.success('成员账号已保存')
}
</script>
<template>
  <section class="workspace">
    <header class="page-head">
      <div>
        <h1>家庭管理</h1>
        <p>仅超级管理员可见，用于维护租户和成员账号</p>
      </div>
      <el-button type="primary" @click="dialog = true">创建家庭</el-button>
    </header>
    <div class="surface">
      <el-table v-loading="loading" :data="rows"
        ><el-table-column prop="name" label="家庭名称" min-width="180" /><el-table-column
          prop="remark"
          label="备注"
          min-width="220"
        /><el-table-column label="状态"
          ><template #default="s"
            ><el-tag :type="s.row.status === 'ACTIVE' ? 'success' : 'info'">{{
              s.row.status === 'ACTIVE' ? '启用' : '停用'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="250" align="right"
          ><template #default="s"
            ><el-button link @click="view(s.row as Family)">查看数据</el-button
            ><el-button link @click="showMembers(s.row as Family)">成员账号</el-button
            ><el-button
              link
              :type="s.row.status === 'ACTIVE' ? 'danger' : 'success'"
              @click="toggle(s.row as Family)"
              >{{ s.row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button
            ></template
          ></el-table-column
        ></el-table
      ><el-empty v-if="!loading && !rows.length" description="还没有家庭租户" />
    </div>
    <el-dialog v-model="dialog" title="创建家庭" width="min(480px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="家庭名称"><el-input v-model="form.name" /></el-form-item
        ><el-form-item label="备注"
          ><el-input v-model="form.remark" type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">创建</el-button></template
      ></el-dialog
    ><el-drawer
      v-model="membersVisible"
      :title="`${current?.name || ''} · 成员账号`"
      size="min(620px,94vw)"
      ><div class="mb-4 text-right">
        <el-button type="primary" @click="openMember()">新增成员账号</el-button>
      </div>
      <el-table :data="members"
        ><el-table-column prop="displayName" label="姓名" /><el-table-column
          prop="username"
          label="账号"
        /><el-table-column label="角色">家庭成员</el-table-column
        ><el-table-column prop="status" label="状态" /><el-table-column label="操作" width="70"
          ><template #default="s"
            ><el-button link @click="openMember(s.row as FamilyMember)">编辑</el-button></template
          ></el-table-column
        ></el-table
      ><el-alert
        class="mt-5"
        type="info"
        :closable="false"
        title="成员账号由超级管理员统一维护，家庭用户侧不提供成员管理页面。" /></el-drawer
    ><el-dialog
      v-model="memberDialog"
      :title="editingMember ? '编辑成员账号' : '新增成员账号'"
      width="min(500px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="登录账号"
          ><el-input v-model="memberForm.username" :disabled="!!editingMember" /></el-form-item
        ><el-form-item label="显示名称"><el-input v-model="memberForm.displayName" /></el-form-item
        ><el-form-item v-if="!editingMember" label="初始密码"
          ><el-input v-model="memberForm.password" type="password" show-password /></el-form-item
        ><el-form-item v-if="editingMember" label="账号状态"
          ><el-switch
            v-model="memberForm.active"
            active-text="启用"
            inactive-text="停用" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="memberDialog = false">取消</el-button
        ><el-button type="primary" @click="saveMember">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>
