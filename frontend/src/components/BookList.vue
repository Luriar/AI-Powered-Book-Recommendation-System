<template>
  <main class="book-page-container">
    <!-- ✅ 상단 제목 + 검색 -->
    <div class="search-header">
      <h2 class="section-title">도서 목록</h2>
      <div class="search-bar">
        <div class="search-options">
          <label><input type="radio" value="title" v-model="searchType" /> 제목</label>
          <label><input type="radio" value="author" v-model="searchType" /> 저자</label>
        </div>
        <input
          v-model="searchQuery"
          @keyup.enter="searchBooks"
          placeholder="검색어 입력 후 Enter"
          class="search-input"
        />
        <button @click="searchBooks" class="search-button">검색</button>
      </div>
    </div>

    <!-- ✅ 본문: 왼쪽 카테고리 / 오른쪽 도서 -->
    <div class="main-content">
      <aside class="category-sidebar">
        <h3>카테고리</h3>
        <ul>
          <li
            v-for="cat in categories"
            :key="cat"
            :class="{ active: currentCategory === cat }"
            @click="changeCategory(cat)"
          >
            {{ cat }}
          </li>
        </ul>
      </aside>

      <section class="book-list-area">
        <div v-if="loading" class="loading">불러오는 중...</div>
        <div v-else>
          <div v-if="books.length === 0" class="empty-message">
            등록된 도서가 없습니다.
          </div>
          <div v-else>
            <div class="book-grid">
              <div
                v-for="book in books"
                :key="book.id"
                class="book-card"
                @click="goToDetail(book.id)"
              >
                <img
                  :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'"
                  alt="책 표지"
                  class="book-image"
                />
                <h3 class="book-title">{{ book.title }}</h3>
                <p class="book-author">저자: {{ book.author }}</p>
                <p class="book-meta">재고: {{ book.stock ?? '알 수 없음' }}</p>
                <button @click.stop="toggleFavorite(book)">
                  <span v-if="book.isFavorited">❤️</span>
                  <span v-else>🤍</span>
                </button>
              </div>
            </div>

            <!-- 페이지네이션 -->
            <div class="pagination-row">
              <div class="pagination-controls">
                <button @click="goToPage(page - 1)" :disabled="page === 0">이전</button>
                <span>페이지 {{ page + 1 }} / {{ totalPages }}</span>
                <button @click="goToPage(page + 1)" :disabled="page >= totalPages - 1">다음</button>
              </div>
              <div class="pagination-input">
                <input
                  type="number"
                  v-model.number="inputPage"
                  :min="1"
                  :max="totalPages"
                  class="page-number-input"
                />
                <button @click="goToPage(inputPage - 1)">이동</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL

const router = useRouter()
const route = useRoute()

const books = ref([])
const loading = ref(true)
const mode = ref('all')
const currentCategory = ref(route.query.category || '전체')
const currentQuery = ref('')
const page = ref(0)
const totalPages = ref(1)
const size = 9
const inputPage = ref(1)

const searchQuery = ref('')
const searchType = ref('title')
const favoriteBookIds = ref([])

const categories = [
  '전체', '소설', '시', '에세이', '인문', '가정/육아', '요리', '건강',
  '취미/실용/스포츠', '경제/경영', '자기계발', '정치/사회', '역사/문화',
  '종교', '예술/대중문화', '중/고등참고서', '기술/공학', '외국어', '과학',
  '취업/수험서', '여행', '컴퓨터/IT', '잡지', '청소년', '초등참고서',
  '유아(0~7세)', '어린이(초등)', '만화', '대학교재'
]

const goToDetail = (id) => router.push(`/books/${id}`)

const fetchFavorites = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/favorites', {
      headers: { Authorization: `Bearer ${token}` }
    })
    favoriteBookIds.value = res.data.map(book => book.id)
  } catch {
    favoriteBookIds.value = []
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
    alert("찜 처리 실패")
  }
}

const fetchPagedBooks = async (resetPage = true) => {
  mode.value = 'all'
  if (resetPage) {
    page.value = 0
    inputPage.value = 1
  }
  loading.value = true
  try {
    const res = await axios.get('/api/books/paged', {
      params: { page: page.value, size }
    })
    books.value = res.data.books
    totalPages.value = res.data.totalPages
  } finally {
    loading.value = false
  }
}

const searchBooks = async (resetPage = true) => {
  const q = searchQuery.value.trim()
  if (!q) return fetchPagedBooks()

  mode.value = 'search'
  currentQuery.value = q
  if (resetPage) {
    page.value = 0
    inputPage.value = 1
  }
  loading.value = true
  try {
    const res = await axios.get('/api/books/search-paged', {
      params: { q, type: searchType.value, page: page.value, size }
    })
    books.value = res.data.books
    totalPages.value = res.data.totalPages
  } finally {
    loading.value = false
  }
}

const filterByCategory = async (cat, resetPage = true) => {
  mode.value = 'category'
  currentCategory.value = cat
  if (resetPage) {
    page.value = 0
    inputPage.value = 1
  }
  loading.value = true
  try {
    const res = await axios.get('/api/books/category-paged', {
      params: {
        category: cat,
        page: page.value,
        size
      }
    })
    books.value = res.data.books
    totalPages.value = res.data.totalPages
  } finally {
    loading.value = false
  }
}

const changeCategory = (cat) => {
  currentCategory.value = cat
  router.push({ path: '/books', query: cat === '전체' ? {} : { category: cat } })
}

const goToPage = (newPage) => {
  if (newPage >= 0 && newPage < totalPages.value) {
    page.value = newPage
    inputPage.value = newPage + 1

    if (mode.value === 'category') {
      filterByCategory(currentCategory.value, false)
    } else if (mode.value === 'search') {
      searchBooks(false)
    } else {
      fetchPagedBooks(false)
    }
  }
}

onMounted(async () => {
  await fetchFavorites()
  const category = route.query.category
  if (category) {
    await filterByCategory(category)
  } else {
    await fetchPagedBooks()
  }
  books.value.forEach(book => {
    book.isFavorited = favoriteBookIds.value.includes(book.id)
  })
})

watch(() => route.query.category, (newCategory) => {
  if (newCategory) {
    currentCategory.value = newCategory
    filterByCategory(newCategory)
  } else {
    currentCategory.value = '전체'  // ✅ 전체 선택 감지
    fetchPagedBooks()
  }
})
</script>

<style scoped>
.book-page-container {
  padding: 3rem 2rem;
  background-color: #f9f5f0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.search-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.section-title {
  font-size: 2rem;
  font-weight: 600;
  color: #2a2a2a;
}

.search-bar {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.search-options {
  display: flex;
  gap: 1rem;
  margin-right: 1rem;
}

.search-options label {
  font-size: 0.95rem;
  color: #444;
}

.search-input {
  padding: 0.6rem 1rem;
  border-radius: 12px;
  border: 1px solid #ccc;
  font-size: 1rem;
  width: 250px;
}

.search-button {
  padding: 0.6rem 1.2rem;
  background-color: #8b5e3c;
  color: white;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 500;
}

.search-button:hover {
  background-color: #6f452c;
}

.main-content {
  display: flex;
  gap: 2rem;
}

.category-sidebar {
  width: 200px;
  background: #fff;
  padding: 1rem;
  border-radius: 12px;
  border: 1px solid #ddd;
  height: fit-content;
}

.category-sidebar h3 {
  margin-bottom: 1rem;
  font-size: 1.1rem;
  font-weight: bold;
}

.category-sidebar ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.category-sidebar li {
  padding: 0.5rem;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.2s;
}

.category-sidebar li:hover {
  background-color: #f0ebe3;
}

.category-sidebar li.active {
  background-color: #e3d3c1;
  font-weight: bold;
}

.book-list-area {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.book-grid {
  width: 100%;
  max-width: 1200px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2rem;
}

.book-card {
  background-color: #fff;
  padding: 1.2rem;
  border-radius: 16px;
  box-shadow: 0 12px 20px rgba(0, 0, 0, 0.08);
  transition: 0.25s;
  cursor: pointer;
  text-align: center;
}

.book-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 28px rgba(0, 0, 0, 0.12);
}

.book-image {
  width: 100%;
  height: 220px;
  object-fit: contain;
  border-radius: 12px;
  background-color: #f5f5f5;
  margin-bottom: 1rem;
}

.book-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: #333;
  margin-bottom: 0.3rem;
}

.book-author {
  font-size: 0.9rem;
  color: #666;
}

.book-meta {
  font-size: 0.9rem;
  color: #444;
}

.loading {
  font-size: 1.2rem;
  color: #999;
}

.empty-message {
  font-size: 1.1rem;
  color: #888;
  margin-top: 3rem;
  text-align: center;
}

.pagination-row {
  margin-top: 2rem;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.pagination-controls,
.pagination-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-number-input {
  width: 60px;
  padding: 0.4rem 0.6rem;
  border-radius: 8px;
  border: 1px solid #aaa;
  text-align: center;
}

.pagination-controls button {
  padding: 0.5rem 1.2rem;
  background-color: #8b5e3c;
  color: white;
  border: none;
  border-radius: 9999px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.pagination-controls button:hover:not(:disabled) {
  background-color: #6f452c;
}

.pagination-controls button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

</style>
