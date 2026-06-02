<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h1>Dify AI Platform</h1>
        <p>智能AI应用开发平台</p>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef">
            <el-form-item prop="username">
              <el-input
                  v-model="loginForm.username"
                  placeholder="用户名"
                  :prefix-icon="User"
                  size="large"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="密码"
                  :prefix-icon="Lock"
                  size="large"
                  @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-button type="primary" size="large" @click="handleLogin" :loading="loading" class="submit-btn">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef">
            <el-form-item prop="username">
              <el-input
                  v-model="registerForm.username"
                  placeholder="用户名"
                  :prefix-icon="User"
                  size="large"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="密码"
                  :prefix-icon="Lock"
                  size="large"
              />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="确认密码"
                  :prefix-icon="Lock"
                  size="large"
              />
            </el-form-item>
            <el-form-item prop="email">
              <el-input
                  v-model="registerForm.email"
                  placeholder="邮箱"
                  :prefix-icon="Message"
                  size="large"
              />
            </el-form-item>
            <el-button type="primary" size="large" @click="handleRegister" :loading="loading" class="submit-btn">
              注册
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Message } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref()
const registerFormRef = ref()

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  email: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  email: [{ type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }]
}

const handleLogin = async () => {
  await loginFormRef.value.validate()
  loading.value = true
  try {
    const success = await userStore.login(loginForm)
    if (success) {
      ElMessage.success('登录成功')
      router.push('/')
    } else {
      ElMessage.error('登录失败')
    }
  } catch (error) {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  await registerFormRef.value.validate()
  loading.value = true
  try {
    const success = await userStore.register(registerForm)
    if (success) {
      ElMessage.success('注册成功，请登录')
      activeTab.value = 'login'
      registerForm.username = ''
      registerForm.password = ''
      registerForm.confirmPassword = ''
      registerForm.email = ''
    } else {
      ElMessage.error('注册失败')
    }
  } catch (error) {
    ElMessage.error('注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #0a0e27 0%, #06091a 100%);
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.login-container::before {
  content: '';
  position: absolute;
  width: 200%;
  height: 200%;
  top: -50%;
  left: -50%;
  background: radial-gradient(circle at center, rgba(102, 126, 234, 0.1) 0%, transparent 50%);
  animation: rotate 20s linear infinite;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.login-card {
  width: 420px;
  max-width: 90%;
  padding: 40px 32px;
  background: rgba(15, 25, 45, 0.85);
  backdrop-filter: blur(12px);
  border-radius: 24px;
  border: 1px solid rgba(102, 126, 234, 0.3);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
  z-index: 10;
  transition: all 0.3s ease;
}

.login-card:hover {
  border-color: rgba(102, 126, 234, 0.6);
  box-shadow: 0 0 30px rgba(102, 126, 234, 0.2);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-header h1 {
  font-size: 28px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff, #a5b4fc);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 8px;
}

.login-header p {
  font-size: 13px;
  color: #94a3b8;
}

/* ========== Tabs 样式 ========== */
.login-tabs {
  margin-top: 8px;
}

.login-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
  background: transparent;
  border-bottom: 1px solid #2a2f4a;
}

.login-tabs :deep(.el-tabs__item) {
  color: #94a3b8;
  font-weight: 500;
  font-size: 15px;
  transition: all 0.3s ease;
}

.login-tabs :deep(.el-tabs__item:hover) {
  color: #a78bfa;
}

.login-tabs :deep(.el-tabs__item.is-active) {
  color: #a78bfa;
}

.login-tabs :deep(.el-tabs__active-bar) {
  background: linear-gradient(135deg, #667eea, #764ba2);
  height: 3px;
  border-radius: 3px;
}

/* ========== 表单样式 - 关键修复 ========== */

/* 表单项容器 */
.login-tabs .el-form-item {
  margin-bottom: 24px;
}

/* 输入框外层 */
.login-tabs .el-input__wrapper {
  background: rgba(10, 14, 39, 0.9) !important;
  border: 1px solid #2a2f4a !important;
  border-radius: 12px !important;
  box-shadow: none !important;
  transition: all 0.3s ease;
  padding: 4px 11px !important;
}

.login-tabs .el-input__wrapper:hover {
  border-color: #667eea !important;
}

.login-tabs .el-input__wrapper.is-focus {
  border-color: #667eea !important;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2) !important;
}

/* 输入框文字 - 白色清晰可见 */
.login-tabs .el-input__inner {
  color: #ffffff !important;
  font-size: 14px;
  font-weight: 400;
}

/* 占位符文字 */
.login-tabs .el-input__inner::placeholder {
  color: #64748b !important;
}

/* 前缀图标 */
.login-tabs .el-input__prefix-inner {
  color: #94a3b8 !important;
}

/* 密码框眼睛图标 */
.login-tabs .el-input__suffix {
  color: #94a3b8 !important;
}

.login-tabs .el-input__suffix:hover {
  color: #a78bfa !important;
}

/* 错误提示 */
.login-tabs .el-form-item.is-error .el-input__wrapper {
  border-color: #f87171 !important;
  box-shadow: 0 0 0 2px rgba(248, 113, 113, 0.2) !important;
}

/* 提交按钮 */
.submit-btn {
  width: 100%;
  margin-top: 8px;
  padding: 12px !important;
  background: linear-gradient(135deg, #667eea, #764ba2) !important;
  border: none !important;
  border-radius: 12px !important;
  font-size: 16px;
  font-weight: 600;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

/* 响应式 */
@media screen and (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }

  .login-header h1 {
    font-size: 24px;
  }
}
</style>