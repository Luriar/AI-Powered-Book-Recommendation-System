<template>
  <main class="detail-wrapper">
    <div class="detail-card" v-if="book">
      <div class="detail-content">
        <img
          :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'"
          alt="책 표지"
          class="book-image"
        />
        <div class="book-info">
          <h2 class="book-title">{{ book.title }}</h2>
          <p class="book-meta">저자: {{ book.author }}</p>
          <p class="book-meta">출판사: {{ book.publisher }}</p>
          <p class="book-meta">카테고리: {{ book.category }}</p>
          <p class="book-meta">
            가격:
            <span v-if="book.price > 0">{{ book.price.toLocaleString() }}원</span>
            <span v-else>무료</span>
          </p>
          <p class="book-meta">재고: {{ book.stock ?? '알 수 없음' }}</p>

          <div class="book-summary">
            <p>{{ book.summary || '요약이 없습니다.' }}</p>
          </div>

          <!-- ❤️ 찜 버튼 -->
          <div style="margin-top: 0.5rem;">
            <button @click="toggleFavorite(book)">
              <span v-if="book.isFavorited">❤️ 위시리스트</span>
              <span v-else>🤍 위시리스트</span>
            </button>
          </div>

          <!-- 📦 대출/반납 버튼 -->
          <div class="button-container">
            <button v-if="!book.isBorrowed" @click="borrowBook" class="borrow-button">대출하기</button>
            <button v-else @click="returnBook" class="return-button">반납하기</button>

            <!-- ✏️ 관리자만 보이는 수정/삭제 -->
            <template v-if="isAdmin">
              <RouterLink :to="`/books/${book.id}/edit`" class="edit-button">수정하기</RouterLink>
              <button @click="confirmDelete" class="delete-button">삭제하기</button>
            </template>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="loading">불러오는 중 또는 도서를 찾을 수 없습니다.</div>
  </main>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { isAdmin } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const book = ref(null)
const loading = ref(true)
const cacheBuster = ref(Date.now())

const favoriteBookIds = ref([])
const borrowedBookIds = ref([])

const BASE_URL = import.meta.env.VITE_API_BASE_URL

const fetchFavoriteStatus = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/favorites', {
      headers: { Authorization: `Bearer ${token}` }
    })
    favoriteBookIds.value = res.data.map(b => b.id)
  } catch {
    favoriteBookIds.value = []
  }
}

const fetchBorrowedStatus = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/borrowed', {
      headers: { Authorization: `Bearer ${token}` }
    })
    borrowedBookIds.value = res.data.map(b => b.id)
  } catch {
    borrowedBookIds.value = []
  }
}

const toggleFavorite = async (book) => {
  const token = localStorage.getItem('accessToken')
  const headers = { Authorization: `Bearer ${token}` }
  if (!token) return alert("로그인이 필요합니다.")

  try {
    if (book.isFavorited) {
      await axios.delete(`/api/books/${book.id}/favorite`, { headers })
      book.isFavorited = false
    } else {
      await axios.post(`/api/books/${book.id}/favorite`, {}, { headers })
      book.isFavorited = true
    }
  } catch {
    alert("위시리스트 처리 실패")
  }
}

const borrowBook = async () => {
  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert('로그인이 필요합니다.')
    return
  }

  const headers = { Authorization: `Bearer ${token}` }
  try {
    await axios.post(`/api/books/${book.value.id}/borrow`, {}, { headers })
    alert('도서 대출 완료')
    book.value.isBorrowed = true
  } catch (err) {
    alert((err.response?.data?.message || err.response?.data || err.message))
  }
}

const returnBook = async () => {
  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert('로그인이 필요합니다.')
    return
  }

  const headers = { Authorization: `Bearer ${token}` }
  try {
    await axios.post(`/api/books/${book.value.id}/return`, {}, { headers })
    alert('도서 반납 완료')
    book.value.isBorrowed = false
  } catch (err) {
    alert('반납 실패: ' + (err.response?.data || err.message))
  }
}

const confirmDelete = async () => {
  if (confirm('정말 이 도서를 삭제하시겠습니까?')) {
    try {
      await axios.delete(`/api/books/${book.value.id}`)
      alert('도서가 삭제되었습니다.')
      router.push('/')
    } catch (err) {
      alert('삭제 실패: ' + (err.response?.data || err.message))
    }
  }
}

onMounted(async () => {
  const id = route.params.id
  if (route.query.updated === 'true') {
    cacheBuster.value = Date.now()
  }

  await fetchFavoriteStatus()
  await fetchBorrowedStatus()

  try {
    const res = await axios.get(`/api/books/${id}`)
    book.value = res.data
    book.value.isFavorited = favoriteBookIds.value.includes(book.value.id)
    book.value.isBorrowed = borrowedBookIds.value.includes(book.value.id)

    if (!book.value.summary || book.value.summary.trim() === '') {
      const summaryRes = await axios.get(`/api/books/${id}/summary`)
      book.value.summary = summaryRes.data
    }
  } catch (err) {
    console.error('도서 상세 정보 불러오기 실패:', err)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.detail-wrapper {
  background-color: #f9f5f0;
  min-height: 100vh;
  padding: 3rem 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.detail-card {
  background-color: #fff;
  padding: 2rem;
  border-radius: 20px;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
  max-width: 900px;
  width: 100%;
}

.detail-content {
  display: flex;
  flex-wrap: wrap;
  gap: 2rem;
}

.book-image {
  width: 300px;
  height: 400px;
  object-fit: contain;
  border-radius: 16px;
  background-color: #fafafa;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.06);
}

.book-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.book-title {
  font-size: 1.8rem;
  font-weight: 600;
  color: #2a2a2a;
  margin-bottom: 0.5rem;
}

.book-meta {
  font-size: 1rem;
  color: #555;
}

.book-summary {
  margin-top: 1rem;
  padding: 1rem;
  background-color: #f5f0e8;
  border-radius: 12px;
  font-size: 0.95rem;
  color: #333;
  line-height: 1.6;
  box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.05);
}

.button-container {
  margin-top: 0rem;
  text-align: right;
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.borrow-button,
.return-button,
.edit-button,
.delete-button {
  display: inline-block;
  padding: 0.6rem 1.2rem;
  border-radius: 8px;
  text-decoration: none;
  transition: 0.2s;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid transparent;
}

.borrow-button {
  background-color: #e0f7e9;
  color: #157347;
  border-color: #b8e2cb;
}

.borrow-button:hover {
  background-color: #cbf0d9;
}

.return-button {
  background-color: #fff3e0;
  color: #d17b00;
  border-color: #f5cf94;
}

.return-button:hover {
  background-color: #ffe2ba;
}

.edit-button {
  background-color: #fafafa;
  border: 1px solid #ccc;
  color: #333;
}

.edit-button:hover {
  background-color: #eee;
}

.delete-button {
  background-color: #ffecec;
  border: 1px solid #e88;
  color: #c00;
}

.delete-button:hover {
  background-color: #ffdcdc;
}

.favorite-button-wrapper {
  display: flex;
  justify-content: flex-end; /* 오른쪽 정렬 */
  margin-top: 1rem;
}

</style>