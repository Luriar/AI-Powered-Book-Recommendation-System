<template>
  <div class="auth-page">
    <h2>로그인</h2>
    <form @submit.prevent="handleLogin" class="login-form">
      <input v-model="email" type="email" placeholder="이메일" required />
      <input v-model="password" type="password" placeholder="비밀번호" required />
      <button type="submit">로그인</button>
    </form>
    <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

    <!-- ✅ 회원가입 안내 -->
    <div class="signup-box">
      <p>아직 계정이 없으신가요?</p>
      <RouterLink to="/register" class="signup-button">회원가입</RouterLink>
    </div>
  </div>
</template>

<script setup>
defineOptions({ name: 'LoginView' })

import { login } from '../stores/auth'  // ✅ login 함수 import
import { ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

const email = ref('')
const password = ref('')
const errorMsg = ref('')
const router = useRouter()

async function handleLogin() {
  try {
    const payload = { email: email.value, password: password.value }
    const response = await axios.post('/api/auth/login', payload)

    const token = response.data.token
    login(token)  // ✅ accessToken 저장 + 상태 반영

    await router.push('/')
  } catch {
    errorMsg.value = '로그인 실패: 이메일 또는 비밀번호를 확인하세요.'
  }
}
</script>

<style scoped>
.auth-page {
  max-width: 400px;
  margin: 2rem auto;
  padding: 1rem;
  border: 1px solid #ddd;
  border-radius: 8px;
  background-color: #fff;
}

.login-form input {
  display: block;
  margin: 0.5rem 0;
  width: 100%;
  padding: 0.6rem;
  border-radius: 8px;
  border: 1px solid #ccc;
}

.login-form button {
  margin-top: 1rem;
  width: 100%;
  padding: 0.6rem;
  background-color: #8b5e3c;
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
}
.login-form button:hover {
  background-color: #6f452c;
}

.error-msg {
  color: red;
  margin-top: 1rem;
  text-align: center;
}

.signup-box {
  margin-top: 2rem;
  text-align: center;
  padding-top: 1rem;
  border-top: 1px solid #eee;
}

.signup-button {
  display: block;
  width: 100%;
  margin-top: 1rem;
  padding: 0.6rem;
  background-color: #ccc;
  color: #222;
  text-align: center;
  text-decoration: none;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
}
.signup-button:hover {
  background-color: #aaa;
}
</style>
