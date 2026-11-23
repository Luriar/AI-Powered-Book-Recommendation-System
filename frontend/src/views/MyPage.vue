<template>
  <!-- ✅ 사용자 정보 로딩 전 -->
  <div v-if="!userInfo" class="mypage-loading">
    사용자 정보를 불러오는 중입니다...
  </div>

  <!-- ✅ 사용자 정보가 로드된 경우 -->
  <main class="mypage-wrapper" v-else>
    <!-- 고정 사용자 정보 -->
    <section class="user-info-fixed">
      <p><strong>이름:</strong> {{ userInfo.name }}</p>
      <p><strong>이메일:</strong> {{ userInfo.email }}</p>
      <p><strong>관심 카테고리:</strong> {{ userInfo.preferredCategories }}</p>
    </section>

    <div class="mypage-layout">
      <!-- 좌측 메뉴 -->
      <aside class="menu-sidebar">
        <button
          v-for="item in menuItems"
          :key="item.key"
          :class="['menu-button', selectedMenu === item.key ? 'active' : '']"
          @click="selectedMenu = item.key"
        >
          {{ item.label }}
        </button>
      </aside>

      <!-- 우측 콘텐츠 -->
      <section class="menu-content">

        <!-- ✅ 대출한 도서 -->
        <div v-if="selectedMenu === 'borrowed'">
          <label class="label">대출한 도서</label>
          <div v-if="borrowedBooks.length > 0" class="book-grid">
            <div v-for="book in borrowedBooks" :key="book.id" class="book-card">
              <img :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'" class="book-image" @click="goToDetail(book.id)" />
              <h3 class="book-title">{{ book.title }}</h3>
              <p class="book-author">저자: {{ book.author }}</p>
              <p class="book-meta">재고: {{ book.stock ?? '알 수 없음' }}</p>
              <button @click.stop="toggleFavorite(book)">
                <span v-if="book.isFavorited">❤️</span>
                <span v-else>🤍</span>
              </button>
            </div>
          </div>
          <p v-else class="empty-message">대출한 도서가 없습니다.</p>
        </div>

        <!-- 위시리스트 -->
        <div v-if="selectedMenu === 'favorites'">
          <label class="label">위시리스트</label>
          <div v-if="favoriteBooks.length > 0" class="book-grid">
            <div v-for="book in favoriteBooks" :key="book.id" class="book-card">
              <span class="close-button" @click.stop="removeFavorite(book)">✖</span>
              <img   :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'" class="book-image" @click="goToDetail(book.id)" />
              <h3 class="book-title">{{ book.title }}</h3>
              <p class="book-author">저자: {{ book.author }}</p>
              <p class="book-meta">재고: {{ book.stock ?? '알 수 없음' }}</p>
              <button @click.stop="toggleFavorite(book)">
                <span v-if="book.isFavorited">❤️</span>
                <span v-else>🤍</span>
              </button>
            </div>
          </div>
          <p v-else class="empty-message">위시리스트에 도서가 없습니다.</p>
        </div>

        <!-- 추천받았던 도서 -->
        <div v-else-if="selectedMenu === 'recommendations'">
          <label class="label">추천받았던 도서</label>
          <div v-if="recommendedBooks.length > 0" class="book-grid">
            <div v-for="book in recommendedBooks" :key="book.id" class="book-card">
              <span class="close-button" @click.stop="removeRecommendation(book)">✖</span>
              <img   :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'" class="book-image" @click="goToDetail(book.id)" />
              <h3 class="book-title">{{ book.title }}</h3>
              <p class="book-author">저자: {{ book.author }}</p>
              <p class="book-meta">재고: {{ book.stock ?? '알 수 없음' }}</p>
              <button @click.stop="toggleFavorite(book)">
                <span v-if="book.isFavorited">❤️</span>
                <span v-else>🤍</span>
              </button>
            </div>
          </div>
          <p v-else class="empty-message">추천받은 도서가 없습니다.</p>
        </div>

        <!-- 관심사 수정 -->
        <div v-else-if="selectedMenu === 'interests'">
          <label class="label">관심 카테고리 수정 (최소 3개 선택)</label>
          <div class="category-grid">
            <button
              v-for="cat in categories"
              :key="cat"
              :class="['category-button', selectedCategories.includes(cat) ? 'selected' : '']"
              @click="toggleCategory(cat)"
            >
              {{ cat }}
            </button>
          </div>
          <button @click="updateInterests" class="button">관심사 저장</button>
          <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        </div>

        <!-- 비밀번호 변경 -->
        <div v-else-if="selectedMenu === 'password'">
          <label class="label">비밀번호 변경</label>
          <div class="form-group">
            <label>현재 비밀번호</label>
            <input v-model="currentPassword" type="password" placeholder="현재 비밀번호" />
          </div>
          <div class="form-group">
            <label>새 비밀번호</label>
            <input v-model="newPassword" type="password" placeholder="새 비밀번호 (8자 이상, 문자+숫자 포함)" />
            <p v-if="passwordMessage" class="input-error" v-html="passwordMessage.replace(/\n/g, '<br>')" />
          </div>
          <button @click="changePassword" class="button">비밀번호 변경</button>
        </div>

        <!-- 회원 탈퇴 -->
        <div v-else-if="selectedMenu === 'delete'">
          <label class="label">회원탈퇴</label>
          <button @click="deleteAccount" class="button danger">회원탈퇴</button>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { isLoggedIn } from '../stores/auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL
const router = useRouter()
const userInfo = ref(null)

const selectedCategories = ref([])
const currentPassword = ref('')
const newPassword = ref('')
const errorMessage = ref('')
const passwordMessage = ref('')
const selectedMenu = ref('borrowed')

const menuItems = [
  { key: 'borrowed', label: '대출한 도서' },
  { key: 'favorites', label: '위시리스트' },
  { key: 'recommendations', label: '추천받았던 도서' },
  { key: 'interests', label: '관심사 수정' },
  { key: 'password', label: '비밀번호 변경' },
  { key: 'delete', label: '회원탈퇴' }
]

const categories = [
  "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
  "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
  "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
  "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
  "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

const favoriteBooks = ref([])
const recommendedBooks = ref([])
const borrowedBooks = ref([])

const goToDetail = (id) => {
  router.push(`/books/${id}`)
}

const removeFavorite = async (book) => {
  const token = localStorage.getItem('accessToken')
  const headers = { Authorization: `Bearer ${token}` }
  try {
    await axios.delete(`/api/books/${book.id}/favorite`, { headers })
    favoriteBooks.value = favoriteBooks.value.filter(b => b.id !== book.id)
  } catch {
    alert('찜 해제 실패')
  }
}

const removeRecommendation = async (book) => {
  const token = localStorage.getItem('accessToken')
  const headers = { Authorization: `Bearer ${token}` }
  try {
    await axios.delete(`/api/users/me/recommendation/${book.id}`, { headers })
    recommendedBooks.value = recommendedBooks.value.filter(b => b.id !== book.id)
  } catch {
    alert("추천 도서 삭제 실패")
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

const loadFavoriteBooks = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/favorites', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const books = res.data
    books.forEach(book => (book.isFavorited = true))
    favoriteBooks.value = books
  } catch {
    favoriteBooks.value = []
  }
}

const loadRecommendedBooks = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/recommendations', {
      headers: { Authorization: `Bearer ${token}` }
    })
    const books = res.data
    books.forEach(book => (book.isFavorited = false))
    recommendedBooks.value = books
  } catch {
    recommendedBooks.value = []
  }
}

const loadBorrowedBooks = async () => {
  try {
    const token = localStorage.getItem('accessToken')
    const res = await axios.get('/api/users/me/borrowed', {
      headers: { Authorization: `Bearer ${token}` }
    })
    borrowedBooks.value = res.data
  } catch {
    borrowedBooks.value = []
  }
}

watch(selectedMenu, (val) => {
  if (val === 'favorites') loadFavoriteBooks()
  else if (val === 'recommendations') loadRecommendedBooks()
  else if (val === 'borrowed') loadBorrowedBooks()
})

onMounted(async () => {
  if (!isLoggedIn.value) {
    router.push('/login')
    return
  }

  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert('로그인이 필요합니다.')
    router.push('/login')
    return
  }

  try {
    const res = await axios.get('/api/users/me', {
      headers: { Authorization: `Bearer ${token}` },
      withCredentials: true
    })
    userInfo.value = res.data

    if (userInfo.value.preferredCategories) {
      selectedCategories.value = userInfo.value.preferredCategories.split(',').map(s => s.trim())
    }

    if (selectedMenu.value === 'favorites') loadFavoriteBooks()
    else if (selectedMenu.value === 'recommendations') loadRecommendedBooks()
    else if (selectedMenu.value === 'borrowed') loadBorrowedBooks()

  } catch {
    alert('사용자 정보를 불러올 수 없습니다.')
    router.push('/login')
  }
})

function toggleCategory(cat) {
  const idx = selectedCategories.value.indexOf(cat)
  if (idx === -1) {
    selectedCategories.value.push(cat)
  } else {
    selectedCategories.value.splice(idx, 1)
  }
}

async function updateInterests() {
  errorMessage.value = ''
  if (selectedCategories.value.length < 3) {
    errorMessage.value = '관심 카테고리는 최소 3개 이상 선택해야 합니다.'
    return
  }

  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert('로그인이 필요합니다.')
    return
  }

  try {
    const joined = selectedCategories.value.join(',')
    await axios.put('/api/users/interests', { interests: joined }, {
      headers: { Authorization: `Bearer ${token}` }
    })
    alert('관심사가 수정되었습니다!')
    userInfo.value.preferredCategories = joined
  } catch (err) {
    alert('관심사 수정 실패')
    console.error(err)
  }
}


async function changePassword() {
  passwordMessage.value = ''

  if (
    newPassword.value.length < 8 ||
    !/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]*$/.test(newPassword.value)
  ) {
    passwordMessage.value = '비밀번호는 최소 8자 이상이며, 영문자와 숫자를 각각 1개 이상 포함해야 합니다.'
    return
  }

  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert("로그인이 필요합니다.")
    return
  }

  try {
    await axios.put('/api/users/password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value
    }, {
      headers: { Authorization: `Bearer ${token}` }  // ✅ 여기 추가
    })
    alert('비밀번호가 성공적으로 변경되었습니다!')
    currentPassword.value = ''
    newPassword.value = ''
  } catch (err) {
    passwordMessage.value = err.response?.data || '비밀번호 변경 실패'
    console.error(err)
  }
}

async function deleteAccount() {
  const confirmed = confirm('정말로 회원 탈퇴하시겠습니까? 이 작업은 되돌릴 수 없습니다.')
  if (!confirmed) return

  const token = localStorage.getItem('accessToken')
  if (!token) {
    alert('로그인이 필요합니다.')
    return
  }

  try {
    await axios.delete('/api/users/delete', {
      headers: { Authorization: `Bearer ${token}` }
    })
    alert('회원탈퇴가 완료되었습니다.')
    localStorage.removeItem('accessToken')  // ✅ 변수명도 통일
    isLoggedIn.value = false
    router.push('/login')
  } catch (err) {
    alert('회원탈퇴 실패')
    console.error(err)
  }
}
</script>

<style scoped>
.mypage-wrapper {
  padding: 2rem;
  max-width: 1000px;
  margin: auto;
  background-color: #f9f5f0;
  border-radius: 16px;
}

.user-info-fixed {
  background-color: #fff;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  line-height: 1.6;
}

.mypage-layout {
  display: flex;
  gap: 2rem;
  margin-top: 2rem;
}

.menu-sidebar {
  width: 180px;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.menu-button {
  background-color: #f7f1e8;
  border: 1px solid #ccc;
  border-radius: 12px;
  padding: 0.7rem 1rem;
  cursor: pointer;
  text-align: left;
  font-weight: 500;
  transition: 0.2s;
}
.menu-button.active {
  background-color: #d3b685;
  color: white;
  font-weight: bold;
  border-color: #c0a167;
}

.menu-content {
  flex: 1;
  min-width: 0;
}

.label {
  font-weight: 600;
  margin-bottom: 0.5rem;
  display: block;
}

.category-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0;
}

.category-button {
  padding: 0.5rem 1rem;
  border-radius: 20px;
  border: 1px solid #ccc;
  background-color: #fefefe;
  cursor: pointer;
  transition: 0.2s;
}
.category-button:hover {
  background-color: #eee7db;
}
.category-button.selected {
  background-color: #d3b685;
  color: white;
  font-weight: bold;
  border-color: #d3b685;
}

.close-button {
  position: absolute;
  top: 10px;
  right: 12px;
  background: transparent;
  border: none;
  font-size: 1.2rem;
  color: #888;
  cursor: pointer;
}
.close-button:hover {
  color: #c00;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
  margin-top: 1rem;
}

.book-card {
  position: relative;
  background-color: #fff;
  padding: 1rem;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  text-align: center;
  transition: 0.2s;
  cursor: pointer;
}
.book-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.book-image {
  width: 100%;
  height: 200px;
  object-fit: contain;
  border-radius: 12px;
  margin-bottom: 1rem;
  background-color: #f5f5f5;
}

.book-title {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.3rem;
  color: #333;
}

.book-author {
  font-size: 0.9rem;
  color: #666;
}

.book-meta {
  font-size: 0.9rem;
  margin-bottom: 0.5rem;
  color: #444;
}

.empty-message {
  font-size: 1rem;
  color: #999;
  margin-top: 2rem;
  text-align: center;
}

.button {
  display: block;
  width: 100%;
  padding: 0.6rem 1rem;
  background-color: #f0e1ce;
  border: none;
  border-radius: 12px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
  margin-top: 1rem;
}
.button:hover {
  background-color: #e9dece;
  transform: translateY(-2px);
}

.button.danger {
  background-color: #f2c0c0;
  color: #600;
  border: 1px solid #c88;
}
.button.danger:hover {
  background-color: #ebaaaa;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin-bottom: 1rem;
}

.form-group input {
  padding: 0.75rem 1rem;
  border-radius: 12px;
  border: 1px solid #ccc;
  background-color: #fefefe;
  font-size: 0.95rem;
}

.form-group input:focus {
  outline: none;
  border-color: #d3b685;
  box-shadow: 0 0 0 3px rgba(211, 182, 133, 0.25);
}

.mypage-loading {
  text-align: center;
  padding: 3rem 1rem;
  font-size: 1.1rem;
  color: #555;
}

.user-info-fixed {
  background-color: #fff;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
  line-height: 1.6;
  text-align: center; /* ✅ 가운데 정렬 */
}

.input-error,
.error-message {
  color: #d9534f;
  font-size: 0.9rem;
  margin-top: 0.3rem;
  white-space: pre-line;
}

</style>