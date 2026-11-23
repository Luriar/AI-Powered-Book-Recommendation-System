<template>
  <main class="edit-wrapper">
    <div class="edit-card" v-if="book">
      <h2 class="form-title">도서 수정</h2>

      <div class="input-group">
        <input v-model="book.title" placeholder="제목" class="input" />
        <input v-model="book.author" placeholder="저자" class="input" />
        <input v-model="book.publisher" placeholder="출판사" class="input" />

        <select v-model="book.category" class="input" required>
          <option disabled value="">카테고리를 선택하세요</option>
          <option v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</option>
        </select>

        <input v-model.number="book.price" placeholder="가격 (원)" type="number" class="input" />

        <input v-model.number="book.stock" placeholder="재고 수량" type="number" class="input" />

        <!-- 이미지 업로드 -->
        <input type="file" @change="onFileChange" accept="image/*" class="input" />

        <button @click="updateBook" class="button">저장하기</button>
      </div>

      <p v-if="message" class="message">{{ message }}</p>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const book = ref(null)
const message = ref('')
const selectedFile = ref(null)

const categories = [
  "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
  "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
  "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
  "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
  "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

onMounted(async () => {
  const id = route.params.id
  try {
    const res = await axios.get(`/api/books/${id}`)
    book.value = res.data
  } catch (err) {
    console.error('도서 불러오기 실패:', err)
  }
})

function onFileChange(e) {
  selectedFile.value = e.target.files[0]
}

async function updateBook() {
  try {
    const formData = new FormData()
    formData.append('book', new Blob([JSON.stringify(book.value)], { type: 'application/json' }))
    if (selectedFile.value) {
      formData.append('image', selectedFile.value)
    }

    await axios.put(`/api/books/${book.value.id}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    message.value = '도서 수정 완료!'
    setTimeout(() => {
      router.push(`/books/${book.value.id}`)
    }, 1000)
  } catch (err) {
    console.error('도서 수정 실패:', err)
    message.value = '수정 실패: ' + (err.response?.data || err.message)
  }
}
</script>

<style scoped>
.edit-wrapper {
  background-color: #f9f5f0;
  min-height: 100vh;
  padding: 3rem 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}
.edit-card {
  background-color: #fff;
  padding: 2rem;
  border-radius: 20px;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
  max-width: 500px;
  width: 100%;
}
.form-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 1.5rem;
  text-align: center;
  color: #2a2a2a;
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
}
.button {
  background-color: #f2e8dc;
  border: 1px solid #d4c2aa;
  border-radius: 10px;
  padding: 0.6rem 1rem;
  font-weight: 600;
  cursor: pointer;
}
.message {
  text-align: center;
  margin-top: 1rem;
  color: #444;
  font-size: 0.95rem;
}
</style>
