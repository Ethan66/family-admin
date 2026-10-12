<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter(); const auth = useAuthStore(); const loading = ref(false)
const form = reactive({ username: '', password: '' })
async function submit() {
  if (!form.username || !form.password) return ElMessage.warning('请输入账号和密码')
  loading.value = true
  try { await auth.login(form.username, form.password); await router.replace('/dashboard') } finally { loading.value = false }
}
</script>
<template>
  <main class="login-page">
    <section class="story"><div class="story-inner"><div class="seal">家</div><h1>让家庭的每一份记录<br>都有清晰的归处</h1><p>收支、成长、影像与共同记忆，在一个安静可靠的空间里彼此相连。</p><div class="line-art"><span /><span /><span /></div></div></section>
    <section class="login-panel"><form class="login-form" @submit.prevent="submit"><div class="mobile-brand">家序</div><p class="eyebrow">WELCOME HOME</p><h2>回到家庭资料台</h2><p class="hint">使用管理员分配的账号登录</p><label>账号<el-input v-model="form.username" size="large" placeholder="请输入账号" autocomplete="username" /></label><label>密码<el-input v-model="form.password" type="password" size="large" placeholder="请输入密码" show-password autocomplete="current-password" @keyup.enter="submit" /></label><el-button native-type="submit" type="primary" size="large" :loading="loading">登录</el-button><small>家庭之间数据独立存放，访问受权限保护</small></form></section>
  </main>
</template>
<style scoped>
.login-page { min-height: 100dvh; display: grid; grid-template-columns: minmax(420px,1.2fr) minmax(420px,.8fr); background: var(--paper); }.story { position: relative; overflow: hidden; display: grid; place-items: center; padding: 64px; color: #f8f2e8; background: #173b31; }.story::before { content:''; position:absolute; inset:-20%; background: radial-gradient(circle at 25% 25%,rgba(215,145,88,.24),transparent 24%),radial-gradient(circle at 70% 76%,rgba(153,184,144,.18),transparent 30%); }.story-inner { position:relative; max-width: 620px; }.seal { width: 52px;height:52px;display:grid;place-items:center;border:1px solid rgba(255,255,255,.4);border-radius:14px;font:24px Georgia,serif; }.story h1 { margin: 38px 0 22px; font: 500 clamp(40px,5vw,68px)/1.18 Georgia,"Songti SC",serif; letter-spacing:-.04em; }.story p { max-width: 430px; color:#c0cec7; line-height:1.8; }.line-art { margin-top:64px; display:flex;gap:10px;align-items:flex-end; }.line-art span { width:80px;height:1px;background:#d99862; }.line-art span:nth-child(2){width:30px;height:30px;border:1px solid #d99862;border-radius:50%;background:transparent}.line-art span:nth-child(3){width:140px}.login-panel{display:grid;place-items:center;padding:40px}.login-form{width:min(390px,100%);display:grid;gap:20px}.eyebrow{margin:0;color:#9b6b4e;font-size:11px;font-weight:700;letter-spacing:.18em}.login-form h2{margin:-7px 0 0;font:600 32px Georgia,"Songti SC",serif}.hint{margin:-12px 0 10px;color:var(--muted)}label{display:grid;gap:8px;color:#43544d;font-size:13px;font-weight:600}.login-form .el-button{width:100%;margin-top:6px}.login-form small{text-align:center;color:#89938e}.mobile-brand{display:none}@media(max-width:800px){.login-page{grid-template-columns:1fr}.story{display:none}.login-panel{padding:24px}.mobile-brand{display:block;color:var(--forest);font:600 34px Georgia,"Songti SC",serif;margin-bottom:20px}}
</style>
