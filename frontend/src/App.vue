<template>
  <div class="app-wrapper">
    <header class="global-header">
      <div class="header-container">
        <!-- 헤더 왼쪽에 BGM 선택 -->
        <div class="header-left bgm-inline-control">
          <label for="bgm-select" class="bgm-label">분위기</label>
          <select id="bgm-select" v-model="selectedBgm" @change="changeBgm" class="bgm-select">
            <option value="cafe">카페</option>
            <option value="calm">잔잔한</option>
            <option value="rain">빗소리</option>
            <option value="sad">슬픔</option>
            <option value="happy">기쁨</option>
            <option value="fantasy">판타지</option>
          </select>

          <button @click="togglePlay" class="bgm-toggle">
            {{ isPlaying ? '🔊' : '🔇' }}
          </button>

          <input
            type="range"
            min="0"
            max="1"
            step="0.01"
            v-model.number="volume"
            @input="updateVolume"
            class="bgm-slider"
          />
        </div>
        <!-- 가운데 메뉴 -->
        <div class="header-center">
          <nav class="header-nav">
            <RouterLink to="/" exact>Home</RouterLink>

            <div
              class="category-wrapper"
              @mouseenter="showCategory = true"
              @mouseleave="showCategory = false"
            >
              <div class="category-dropdown">
                <span class="nav-link"  @click.stop="handleCategoryClick">도서</span>
                <div class="category-hover-buffer"></div>
                <div v-if="showCategory" class="category-popover-grid">
                  <ul>
                    <li @click="goToCategory('전체')"><strong>전체</strong></li>
                  </ul>
                  <hr class="category-divider" />
                  <ul>
                    <li v-for="cat in categories.slice(1)" :key="cat" @click="goToCategory(cat)">
                      {{ cat }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>
            <RouterLink to="/chatbot">도서 추천 챗봇</RouterLink>
            <router-link v-if="isAdmin" to="/register-book">도서 수동 등록</router-link>
          </nav>
        </div>

        <!-- 오른쪽 로그인 / 로그아웃 -->
        <div class="header-right">
          <template v-if="isLoggedIn">
            <RouterLink to="/mypage" class="button">마이페이지</RouterLink>
            <button @click="logout" class="button">로그아웃</button>
          </template>
          <template v-else>
            <RouterLink to="/login" class="button">로그인</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main class="page-container">
      <RouterView />
    </main>

    <footer class="footer-nav">
      <a href="#" @click.prevent="setLang('ko')">한국어</a>
      <a href="#" @click.prevent="setLang('en')">English</a>
      <a href="#" @click.prevent="setLang('zh-CN')">简体中文</a>
    </footer>
  </div>
</template>

<script setup>
import { isLoggedIn, syncLoginState, isAdmin, accessToken } from './stores/auth'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { onMounted, onBeforeMount, ref } from 'vue'

const showCategory = ref(false)
const router = useRouter()

const selectedBgm = ref('cafe')
const isPlaying = ref(false)
const audio = new Audio()
const volume = ref(0.4)


function updateVolume() {
  audio.volume = volume.value
}


function changeBgm() {
  const newSrc = `/audio/${selectedBgm.value}.mp3`
  if (audio.src !== location.origin + newSrc) {
    audio.pause()
    audio.src = newSrc
    if (isPlaying.value) {
      audio.play().catch(() => {
        isPlaying.value = false
      })
    }
  }
}

function togglePlay() {
  if (isPlaying.value) {
    audio.pause()
  } else {
    // ✅ 버튼 클릭 이벤트와 동기적으로 실행
    try {
      audio.play().then(() => {
        isPlaying.value = true
      }).catch((err) => {
        console.warn('🎵 재생 차단됨:', err)
        isPlaying.value = false
      })
    } catch (e) {
      console.warn('🎵 재생 실패:', e)
    }
    return
  }
  isPlaying.value = false
}

onMounted(() => {
  const path = `/audio/${selectedBgm.value}.mp3`
  audio.src = path       // 👉 초기엔 재생하지 않고 src만 설정
  audio.loop = true
  audio.volume = volume.value
})


const categories = [
  "전체",
  "소설", "시", "에세이", "인문", "가정/육아", "요리", "건강",
  "취미/실용/스포츠", "경제/경영", "자기계발", "정치/사회", "역사/문화",
  "종교", "예술/대중문화", "중/고등참고서", "기술/공학", "외국어", "과학",
  "취업/수험서", "여행", "컴퓨터/IT", "잡지", "청소년", "초등참고서",
  "유아(0~7세)", "어린이(초등)", "만화", "대학교재"
]

const goToCategory = (cat) => {
  if (cat === '전체') {
    router.push('/books')
  } else {
    router.push({ path: '/books', query: { category: cat } })
  }
  showCategory.value = false
}

const handleCategoryClick = () => {
  showCategory.value = false
  setTimeout(() => {
    goToCategory('전체')
  }, 50)  // 약간의 지연을 줘서 마우스 이벤트 겹침 방지
}

const logout = () => {
  localStorage.removeItem('accessToken')   // ① storage 제거
  accessToken.value = null                 // ② 상태 반영 → isLoggedIn 자동 false 됨
  router.push('/')                         // ③ 홈으로 이동
}

// ✅ 렌더 전에 로그인 상태 동기화 (여기서 핵심)
onBeforeMount(() => {
  syncLoginState()
})

// ❓ scrollTo는 렌더 후 실행
onMounted(() => {
  window.scrollTo(0, 0)
})

/** @param {'ko'|'en'|'zh-CN'} lang */
function setLang (lang) {
  /* 위젯이 읽는 googtrans 쿠키를 수동으로 조작 */
  if (lang === 'ko') {
    document.cookie = 'googtrans=;path=/;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  } else {
    document.cookie = `googtrans=/ko/${lang};path=/`;
  }
  location.reload();
}
</script>

<style scoped>
/* 기본 배경, 폰트 */
body {
  margin: 0;
  background-color: #f9f5f0;
  font-family: 'Noto Sans KR', sans-serif;
}

.app-wrapper {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  width: 100%;
}

/* ─────────── 헤더 구조 ─────────── */
.global-header{
  position: sticky;          /* 스크롤해도 고정 */
  top: calc(var(--banner,0));/* 번역배너 높이만큼 내려 놓기 */
  z-index: 999;              /* 배너 위에 올라오지 않도록 */
  width: 100%;
  background:#f9f5f0;
  border-bottom:1px solid #ddd;
}

.header-container {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  width: 1200px;
  margin: 0 auto;
  padding: 1.2rem 0; /* 내부 여백만 */
  box-sizing: border-box;
}

.header-center {
  display: flex;
  justify-content: center;
}

.header-right {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.5rem;
  margin-right: 2rem;
}

.header-nav {
  display: flex;
  gap: 2rem;
  align-items: center;
  margin-right: 2rem;
}

.header-nav a,

.nav-link {
  color: #222;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
  cursor: pointer;
  display: inline-block;
  vertical-align: middle;
  line-height: 1.4;
}
.header-nav a.router-link-exact-active {
  color: #aaa;
}
.header-nav a:hover,
.nav-link:hover {
  text-decoration: underline;
}

/* 로그인/마이페이지 버튼 공통 스타일 */
.button {
  padding: 0.5rem 1rem;
  background-color: #8b5e3c;
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
}
.button:hover {
  background-color: #6f452c;
}

/* ─────────── 카테고리 드롭다운 ─────────── */
.category-wrapper {
  position: relative;
}
.category-divider {
  border: none;
  border-top: 1px solid #ccc;
  margin: 0.6rem 0;
}
.category-dropdown {
  position: relative;
}
.category-hover-buffer {
  position: absolute;
  top: 100%;
  left: 0;
  width: 100%;
  height: 26px;
  z-index: 998;
}
.category-popover-grid {
  position: absolute;
  top: 3.2rem;
  left: 0;
  width: 600px;
  background: white;
  border: 1px solid #ccc;
  border-radius: 12px;
  padding: 1rem;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
  z-index: 999;
}
.category-popover-grid ul {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  list-style: none;
  padding: 0;
  margin: 0;
  gap: 0.5rem;
}
.category-popover-grid li {
  padding: 0.4rem 0.6rem;
  cursor: pointer;
  font-size: 0.95rem;
  color: #333;
  border-radius: 6px;
}
.category-popover-grid li:hover {
  background-color: #f0f0f0;
}

/* ─────────── 메인 영역 ─────────── */
.page-container {
  width: 1200px;
  margin: 0 auto;
  overflow-x: auto;
}

/* ─────────── 푸터 ─────────── */
.footer-nav {
  display: flex;
  justify-content: center;
  gap: 2rem;
  width: 1200px;          /* ✅ 고정 너비 */
  margin: 0 auto;         /* ✅ 가운데 정렬 */
  padding: 1.2rem 0;
  background-color: #f9f5f0;
  border-top: 1px solid #ddd;
}

.footer-nav a {
  color: #222;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}
.footer-nav a.router-link-exact-active {
  color: #aaa;
}
.footer-nav a:hover {
  text-decoration: underline;
}

.bgm-inline-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: 1rem;
}

.bgm-label {
  font-size: 0.9rem;
  color: #444;
  font-weight: 500;
}

.bgm-select {
  padding: 0.4rem 0.6rem;
  font-size: 0.9rem;
  border-radius: 8px;
  border: 1px solid #ccc;
  background-color: #fff;
  font-weight: 500;
  min-width: 120px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.bgm-toggle {
  background: none;
  border: none;
  font-size: 0.95rem;
  padding: 0.4rem 0rem;
  cursor: pointer;
  font-weight: 500;
}
.bgm-toggle:hover {
  text-decoration: underline;
}
.bgm-slider {
  width: 80px;
  height: 6px;
  background: #ddd;
  border-radius: 4px;
  outline: none;
  appearance: none;
}
.bgm-slider::-webkit-slider-thumb {
  appearance: none;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #8b5e3c;
  cursor: pointer;
}


</style>
