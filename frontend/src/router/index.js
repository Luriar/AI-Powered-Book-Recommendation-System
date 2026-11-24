import { createRouter, createWebHistory } from 'vue-router'
import BookDetail from '../components/BookDetail.vue'
import BookEdit from '../components/BookEdit.vue'
import BookList from '../components/BookList.vue'
import BookRegister from '../components/BookRegister.vue'
import ChatBot from '../components/ChatBot.vue'
import MainPage from '../views/MainPage.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import MyPage from '../views/MyPage.vue' // ✅ 추가

const routes = [
  {
    path: '/',
    name: 'MainPage',
    component: MainPage
  },
  {
    path: '/books',
    name: 'BookListByCategory',
    component: BookList
  },
  {
    path: '/books/:id',
    name: 'BookDetail',
    component: BookDetail
  },
  {
    path: '/books/:id/edit',
    name: 'BookEdit',
    component: BookEdit
  },
  {
    path: '/register-book',
    name: 'BookRegister',
    component: BookRegister
  },
  {
    path: '/chatbot',
    name: 'ChatBot',
    component: ChatBot
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView
  },
  {
    path: '/register',
    name: 'Register',
    component: RegisterView
  },
  {
    path: '/mypage',
    name: 'MyPage',
    component: MyPage
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return { top: 0 }
  }
})

export default router
