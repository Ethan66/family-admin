<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

interface LoginForm {
  username: string
  password: string
}

const router = useRouter()
const authStore = useAuthStore()
const loginFormRef = ref<FormInstance>()
const isSubmitting = ref(false)
const errorMessage = ref('')
const loginForm = reactive<LoginForm>({
  username: '',
  password: '',
})
const loginRules: FormRules<LoginForm> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleSubmit(): Promise<void> {
  const tempValid = await loginFormRef.value?.validate().catch(() => false)
  if (!tempValid || isSubmitting.value) return
  isSubmitting.value = true
  errorMessage.value = ''
  try {
    await authStore.login(loginForm.username, loginForm.password)
    await router.replace('/dashboard')
  } catch {
    errorMessage.value = '登录失败，请检查账号、密码或确认服务已启动。'
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <div class="brand-mark">家</div>
      <p class="story-kicker">FAMILY DESK</p>
      <h1>把散落在电脑里的<br />家庭记忆，放回家里。</h1>
      <p class="story-copy">照片、影片、笔记与试卷保留在本机；家庭账目和成员权限继续由你的服务端安全管理。</p>
      <div class="privacy-note"><span class="privacy-dot"></span>本地文件不会上传</div>
    </section>
    <section class="login-card">
      <div>
        <p class="form-kicker">欢迎回来</p>
        <h2>登录家庭管理</h2>
        <p>连接你的家庭服务，开始整理今天。</p>
      </div>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top" size="large" @submit.prevent="handleSubmit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="loginForm.username" autocomplete="username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="loginForm.password" type="password" autocomplete="current-password" placeholder="请输入密码" show-password @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-button class="login-button" type="primary" native-type="submit" :loading="isSubmitting">进入家庭管理</el-button>
      </el-form>
      <p class="session-tip">为保护家庭数据，关闭应用后需要重新登录。</p>
    </section>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; grid-template-columns: 1.15fr 0.85fr; background: #f7f5ef; }
.login-story { position: relative; overflow: hidden; display: flex; min-height: 100vh; flex-direction: column; justify-content: center; padding: 8vw; color: #fff; background: linear-gradient(145deg, #123b2e 0%, #236b4e 60%, #2f8060 100%); }
.login-story::after { content: ''; position: absolute; right: -12%; bottom: -20%; width: 480px; height: 480px; border: 1px solid rgb(255 255 255 / 18%); border-radius: 50%; box-shadow: 0 0 0 80px rgb(255 255 255 / 4%), 0 0 0 160px rgb(255 255 255 / 3%); }
.brand-mark { display: grid; width: 52px; height: 52px; margin-bottom: 80px; place-items: center; border: 1px solid rgb(255 255 255 / 36%); border-radius: 16px; font-family: serif; font-size: 27px; }
.story-kicker, .form-kicker { margin: 0 0 16px; color: #f3b56f; font-size: 12px; font-weight: 800; letter-spacing: .22em; }
h1 { max-width: 720px; margin: 0; font-family: "Microsoft YaHei", sans-serif; font-size: clamp(42px, 5vw, 72px); line-height: 1.15; letter-spacing: -.05em; }
.story-copy { max-width: 560px; margin: 30px 0; color: rgb(255 255 255 / 72%); font-size: 17px; line-height: 1.8; }
.privacy-note { display: flex; align-items: center; gap: 10px; font-size: 13px; color: rgb(255 255 255 / 78%); }
.privacy-dot { width: 8px; height: 8px; border-radius: 50%; background: #f3b56f; box-shadow: 0 0 0 5px rgb(243 181 111 / 15%); }
.login-card { width: min(430px, calc(100% - 80px)); align-self: center; justify-self: center; display: grid; gap: 24px; padding: 40px; border-radius: 24px; background: #fff; box-shadow: 0 28px 70px rgb(38 55 46 / 12%); }
.login-card h2 { margin: 0; font-size: 30px; color: #18231d; }
.login-card p { color: #718077; }
.login-button { width: 100%; margin-top: 8px; }
.session-tip { margin: -4px 0 0; text-align: center; font-size: 12px; }
</style>
