<template>
  <main class="register-wrapper">
    <div class="register-card">
      <h2 class="form-title">도서 수동 등록</h2>

      <!-- 등록 모드 선택 -->
      <div class="mode-select">
        <label><input type="radio" value="isbn" v-model="mode" /> ISBN으로 등록</label>
        <label><input type="radio" value="manual" v-model="mode" /> ISBN 없이 수동 등록</label>
      </div>

      <!-- ISBN 등록 -->
      <div v-if="mode === 'isbn'" class="input-group">
        <input v-model="isbn" placeholder="ISBN 입력" class="input" />
        <button @click="registerByIsbn" class="button">도서 등록</button>
      </div>

      <!-- 수동 등록 -->
      <div v-else class="input-group">
        <input v-model="title" placeholder="제목" class="input" />
        <input v-model="author" placeholder="저자" class="input" />
        <input v-model="publisher" placeholder="출판사" class="input" />

        <select v-model="category" class="input" required>
          <option disabled value="">카테고리를 선택하세요</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>

        <input v-model="price" type="number" placeholder="가격 (원)" class="input" />
        <input v-model.number="stock" placeholder="재고 수량" type="number" class="input" />
        <input type="file" @change="onFileChange" accept="image/*" class="input" />
        <button @click="registerManual" class="button">도서 등록</button>
      </div>

      <p v-if="message" class="message">{{ message }}</p>
    </div>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import axios from 'axios'

const mode = ref('isbn')
const isbn = ref('')
const title = ref('')
const author = ref('')
const publisher = ref('')
const category = ref('')
const price = ref('')
const message = ref('')
const selectedFile = ref(null)
const stock = ref(1)  // 기본 재고 수량

const categories = [
  "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
  "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
  "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
  "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
  "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

function onFileChange(e) {
  selectedFile.value = e.target.files[0]
}

const registerByIsbn = async () => {
  message.value = ''
  try {
    const { data } = await axios.post('/api/books/register-isbn', { isbn: isbn.value })
    message.value = `"${data.title}" 등록 완료!`
  } catch (err) {
    message.value = '등록 실패: ' + (typeof err.response?.data === 'string' ? err.response.data : JSON.stringify(err.response?.data || err.message))
  }
}

const registerManual = async () => {
  message.value = ''
  try {
    const formData = new FormData()
    const bookData = {
      title: title.value,
      author: author.value,
      publisher: publisher.value,
      category: category.value,
      price: +price.value,
      stock: +stock.value
    }

    formData.append('book', new Blob([JSON.stringify(bookData)], { type: 'application/json' }))
    if (selectedFile.value) {
      formData.append('image', selectedFile.value)
    }

    const { data } = await axios.post('/api/books/custom', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    message.value = `"${data.title}" 등록 완료!`
  } catch (err) {
    const errorMsg = err.response?.data
    if (typeof errorMsg === 'object') {
      message.value = '등록 실패: ' + JSON.stringify(errorMsg)
    } else {
      message.value = '등록 실패: ' + (errorMsg || err.message)
    }
  }
}
</script>

<style scoped>
.register-wrapper {
  width: 1200px;
  background-color: #f9f5f0;
  min-height: 100vh;
  padding: 3rem 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.register-card {
  background-color: #fff;
  padding: 2rem;
  border-radius: 20px;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
  max-width: 500px;
  width: 100%;
}

.form-title {
  font-size: 1.4rem;
  font-weight: 600;
  margin-bottom: 1.5rem;
  text-align: center;
}

.mode-select {
  display: flex;
  gap: 1.5rem;
  justify-content: center;
  margin-bottom: 1.5rem;
  font-size: 0.95rem;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.input {
  padding: 0.75rem 1rem;
  border-radius: 12px;
  border: 1px solid #ccc;
  background-color: #fefefe;
  font-size: 0.95rem;
  transition: 0.2s;
}
.input:focus {
  outline: none;
  border-color: #d3b685;
  box-shadow: 0 0 0 3px rgba(211, 182, 133, 0.25);
}

.button {
  padding: 0.8rem;
  background-color: #f0e1ce;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}
.button:hover {
  background-color: #e9dece;
  transform: translateY(-2px);
}

.message {
  text-align: center;
  margin-top: 1rem;
  color: #444;
  font-size: 0.95rem;
}
</style>
