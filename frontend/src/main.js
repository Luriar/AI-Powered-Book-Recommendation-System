import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import axios from 'axios' // ✅ axios import

// ✅ JWT 토큰 설정 (accessToken 기준)
(function watchTranslateBanner () {
  const update = () => {
    /* Edge/Chrome이 body 에 강제로 넣은 top 값을 읽어옴 (없으면 '') */
    const t = parseInt(document.body.style.top) || 0;
    document.documentElement.style.setProperty('--banner', t + 'px');
  };
  /* 최초 100 ms 간격으로 몇 번 체크 – 배너가 늦게 생겨도 반영 */
  let tries = 0;
  const timer = setInterval(() => {
    update();
    if (++tries > 20) clearInterval(timer);
  }, 100);

  /* 혹시 사용자가 “번역 취소”해서 배너가 사라지는 경우 대비 */
  const obs = new MutationObserver(update);
  obs.observe(document.body,{attributes:true,attributeFilter:['style']});
})();

const token = localStorage.getItem('accessToken')
if (token) {
  axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
}

// ✅ CORS에서 쿠키·헤더 포함 요청 허용
axios.defaults.withCredentials = true

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
