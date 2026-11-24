import { ref, computed } from 'vue'

/**
 * ✅ 반응형 accessToken
 */
export const accessToken = ref(localStorage.getItem('accessToken') || null)

/**
 * ✅ 로그인 상태 여부 (computed로 자동 연동)
 */
export const isLoggedIn = computed(() => !!accessToken.value)

/**
 * ✅ 관리자 여부 (JWT 디코딩 후 role 검사)
 */
export const isAdmin = computed(() => {
  if (!accessToken.value) return false
  try {
    const payload = JSON.parse(atob(accessToken.value.split('.')[1]))
    return payload?.role === 'ADMIN'
  } catch {
    return false
  }
})

/**
 * ✅ 로그인 시: localStorage + 상태 동기화
 */
export function login(token) {
  localStorage.setItem('accessToken', token)
  accessToken.value = token
}

/**
 * ✅ 로그아웃 시: localStorage + 상태 초기화
 */
export function logout() {
  localStorage.removeItem('accessToken')
  accessToken.value = null
}

/**
 * ✅ 수동 동기화용 (새로고침 직후 또는 App.vue에서 호출 가능)
 */
export function syncLoginState() {
  const token = localStorage.getItem('accessToken')
  accessToken.value = token
}

/**
 * ✅ 다른 탭에서 로그아웃 시 동기화
 */
window.addEventListener('storage', (e) => {
  if (e.key === 'accessToken') {
    accessToken.value = e.newValue
  }
})
