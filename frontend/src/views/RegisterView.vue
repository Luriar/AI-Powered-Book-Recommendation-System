<template>
  <div class="register-page">
    <h2>회원가입</h2>
    <form @submit.prevent="handleRegister" class="register-card">
      <div class="form-group">
        <label>이름</label>
        <input v-model="username" type="text" placeholder="이름을 입력하세요" required />
      </div>

      <div class="form-group">
        <label>이메일</label>
        <input v-model="email" type="email" placeholder="이메일 주소" required />
        <p v-if="emailError" class="input-error">{{ emailError }}</p>
      </div>

      <div class="form-group">
        <label>비밀번호</label>
        <input v-model="password" type="password" placeholder="비밀번호 (8자 이상, 문자+숫자 포함)" required />
        <p v-if="passwordError" class="input-error" v-html="passwordError.replace(/\n/g, '<br>')"/>
      </div>

      <div class="form-group">
        <label>관심 카테고리 (최소 3개)</label>
        <div class="category-grid">
          <label
            v-for="cat in categoryOptions"
            :key="cat"
            class="category-box"
            :class="{ selected: selectedCategories.includes(cat) }"
          >
            <input type="checkbox" :value="cat" v-model="selectedCategories" />
            {{ cat }}
          </label>
        </div>
        <p v-if="selectedCategories.length < 3" class="warning">
          최소 3개 이상의 카테고리를 선택해주세요.
        </p>
      </div>

      <button type="submit" :disabled="selectedCategories.length < 3">회원가입</button>
    </form>
  </div>
</template>

<script setup>
defineOptions({ name: 'RegisterView' })

import { ref } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

const router = useRouter()

const username = ref('')
const email = ref('')
const password = ref('')
const selectedCategories = ref([])

const categoryOptions = [
  "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
  "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
  "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
  "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
  "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

const emailError = ref('')
const passwordError = ref('')

async function handleRegister() {
  emailError.value = ''
  passwordError.value = ''

  const payload = {
    username: username.value,
    email: email.value,
    password: password.value,
    categories: selectedCategories.value
  }

  try {
    const res = await axios.post('/api/auth/register', payload)

    if (res.data?.error) {
      if (res.data.error.includes('이메일')) {
        emailError.value = res.data.error
      } else if (res.data.error.includes('비밀번호')) {
        passwordError.value = res.data.error
      }
      return
    }
    alert('회원가입이 성공적으로 완료되었습니다!')
    await router.push('/login')
  } catch (e) {
    const msg = e.response?.data?.message || '회원가입에 실패했습니다.'
    if (msg.includes('이메일')) {
      emailError.value = msg
    } else if (msg.includes('비밀번호')) {
      passwordError.value = msg
    } else {
      // 기타 예외는 콘솔에만 출력
      console.error('Unhandled register error:', msg)
    }
  }
}

</script>

<style scoped>
.register-page {
  max-width: 800px;
  margin: 3rem auto;
  padding: 1rem;
  background-color: #f9f5f0;
}

h2 {
  text-align: center;
  font-size: 2rem;
  margin-bottom: 2rem;
}

.register-card {
  background: white;
  border-radius: 16px;
  padding: 2rem;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  font-weight: 600;
  display: block;
  margin-bottom: 0.4rem;
}

.form-group input[type="text"],
.form-group input[type="email"],
.form-group input[type="password"] {
  width: 100%;
  padding: 0.8rem;
  border-radius: 10px;
  border: 1px solid #ccc;
  font-size: 1rem;
}

.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.category-box {
  padding: 0.5rem 0.8rem;
  border: 1px solid #ccc;
  border-radius: 10px;
  font-size: 0.9rem;
  background-color: #f9f9f9;
  cursor: pointer;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.category-box.selected {
  background-color: #ffe7c8;
  border-color: #e1a15d;
}

.category-box input {
  margin: 0;
}

.input-error {
  color: red;
  margin-top: 0.4rem;
  font-size: 0.85rem;
}

.warning {
  color: red;
  margin-top: 0.5rem;
}

button[type="submit"] {
  width: 100%;
  padding: 0.8rem;
  background-color: #8b5e3c;
  color: white;
  font-weight: 600;
  font-size: 1rem;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s;
}
button[type="submit"]:hover:not(:disabled) {
  background-color: #6f452c;
}
button[type="submit"]:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}
</style>
