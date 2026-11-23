<!-- src/pages/MainPage.vue -->
<template>
  <main class="mainpage-wrapper">
    <!-- 상단 안내 -->
    <section class="intro-section">
      <h2 class="section-title">AI 온라인 서점에 오신 것을 환영합니다!</h2>
    </section>

    <!-- ① 로그인 후: 추천 도서 → 기존 그대로 --------------- -->
    <section
      v-if="!loading && personalizedBooks.length > 0"
      class="recommend-section"
    >
      <h3 class="section-subtitle">추천 도서</h3>
      <div class="book-grid">
        <div
          v-for="book in personalizedBooks"
          :key="book.id"
          class="book-card"
        >
          <img
            :src="book.imageUrl ? BASE_URL + book.imageUrl : '/no-image.png'"
            class="book-image"
            @click="goToDetail(book.id)"
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
    </section>

    <!-- ② 로그인 X · 데이터 없음: 캐치-프레이즈 & 플레이스홀더 ---- -->
    <section
      v-else-if="!loading"
      class="empty-state-wrapper"
    >
      <h3 class="empty-title">
        로그인하고&nbsp;<span class="highlight">나에게 꼭 맞는 도서</span>를
        추천받아보세요!
      </h3>
      <p class="sub-text">
        책을 추천받고, 요약받고, 위시리스트를 관리해보세요.
      </p>
      <!-- 미니 카드 4 개 – 서비스 특징 / 기능 소개 -->
      <div class="feature-grid">
        <div class="feature-card">
          <img src="/icons/recommend.svg" class="feature-icon" />
          <p class="feature-text">AI 개인화<br />추천</p>
        </div>
        <div class="feature-card">
          <img src="/icons/summary.svg" class="feature-icon" />
          <p class="feature-text">세 줄 요약<br />미리 보기</p>
        </div>
        <div class="feature-card">
          <img src="/icons/chat.svg" class="feature-icon" />
          <p class="feature-text">챗봇에게<br />도서 추천받기</p>
        </div>
        <div class="feature-card">
          <img src="/icons/bookmark.svg" class="feature-icon" />
          <p class="feature-text">위시리스트<br />관리</p>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter, useRoute} from 'vue-router'
import axios from 'axios'
import { accessToken } from '../stores/auth'

const BASE_URL = import.meta.env.VITE_API_BASE_URL
const router = useRouter()
const route  = useRoute()

const personalizedBooks = ref([])
const loading = ref(true)


const fetchRecommendations = async () => {
  const token = accessToken.value
  if (!token) {
    loading.value = false  // ← 토큰 없어도 로딩 끝
    return
  }
  try {
    const res = await axios.get('/api/recommend/personal', {
      headers: { Authorization: `Bearer ${token}` },
    })
    personalizedBooks.value = res.data
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false  // ← 항상 마지막에 로딩 false 처리
  }
}

onMounted(() => {
  fetchRecommendations()
  window.addEventListener('storage', () => {
    accessToken.value = localStorage.getItem('accessToken')
  })
})

watch(
  () => route.fullPath,
  (p) => p === '/' && fetchRecommendations()
)
watch(accessToken, (t) => {
  t ? fetchRecommendations() : (personalizedBooks.value = [])
})

const goToDetail = (id) => router.push(`/books/${id}`)
</script>

<style scoped>
/* ------------ 공통 ------------ */
.mainpage-wrapper {
  padding: 3rem 2rem;
  background: #f9f5f0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 0rem;
}
.intro-section { text-align: center; }
.section-title { font-size: 2rem; font-weight: 700; margin-bottom: .5rem; }
.sub-text      { margin-top: 1rem; color:#666; }

/* ------------ 추천 도서 ------------ */
.recommend-section { margin-top: 0rem; }
.section-subtitle  { font-size:1.25rem;font-weight:600;margin-bottom:1.2rem; }
.book-grid {
  width:100%;max-width:1200px;margin:0 auto;
  display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:2rem;
}
.book-card {
  background:#fff;padding:1.2rem;border-radius:16px;text-align:center;
  box-shadow:0 12px 20px rgba(0,0,0,.08);transition:.25s;cursor:pointer;
}
.book-card:hover { transform:translateY(-4px);box-shadow:0 16px 28px rgba(0,0,0,.12);}
.book-image {width:100%;height:220px;object-fit:contain;border-radius:12px;background:#f5f5f5;margin-bottom:1rem;}
.book-title{font-weight:600;font-size:1.05rem;margin-bottom:.3rem;}
.book-author {
  font-size: 0.9rem;
  color: #666;
}
.book-meta{font-size:.9rem;color:#444;}

/* ------------ Empty-State ------------ */
.empty-state-wrapper{
  display:flex;flex-direction:column;align-items:center;gap:0rem;margin-top:0rem;
}
.empty-title{font-size:1.3rem;font-weight:600;color:#333;text-align:center;}
.highlight{color:#8b5e3c;}

/* 미니 기능 카드 */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr); /* ✅ 2개씩 고정 */
  gap: 1.4rem;
  width: 100%;
  max-width: 600px;
  margin-top: 2rem
}

.feature-card{
  background:#fff;border-radius:14px;padding:1rem 0;display:flex;flex-direction:column;
  align-items:center;gap:.6rem;box-shadow:0 8px 14px rgba(0,0,0,.05);transition:.25s;
}
.feature-card:hover{transform:translateY(-3px);}
.feature-icon{width:125px;height:125px;object-fit:contain;}
.feature-text{font-size:.9rem;font-weight:500;color:#444;text-align:center;line-height:1.3;}
</style>
