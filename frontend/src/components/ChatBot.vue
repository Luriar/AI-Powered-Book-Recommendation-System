<template>
  <main class="chat-wrapper">
    <div class="chat-card">
      <h2 class="chat-title">도서 추천 챗봇</h2>

      <div class="chat-messages" ref="chatMessages">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['chat-message', msg.type]"
          v-html="formatMessage(msg.text)"
        ></div>
      </div>

      <div class="chat-input-group">
        <input
          v-model="question"
          @keyup.enter="sendMessage"
          type="text"
          placeholder="추천받고 싶은 책에 대해 질문하세요!"
          class="chat-input"
        />
        <button @click="sendMessage" class="chat-send-button">전송</button>
      </div>

      <p v-if="loading" class="chat-loading">📡 답변을 기다리는 중...</p>
    </div>
  </main>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { isLoggedIn } from '../stores/auth'

const router = useRouter()

onMounted(() => {
  if (!isLoggedIn.value) {
    router.push('/login')
  }
})

const question = ref('')
const messages = ref([])
const loading = ref(false)
const chatMessages = ref(null)

async function sendMessage() {
  if (!question.value.trim()) return

  const userMessage = {
    type: 'user',
    text: question.value.trim()
  }
  messages.value.push(userMessage)

  loading.value = true
  const currentQuestion = question.value.trim()
  question.value = ''

  try {
    const token = localStorage.getItem('accessToken') // ✅ 실제 토큰 읽기
    const response = await axios.post(
      'http://localhost:8080/api/chatbot',
      { question: currentQuestion },
      {
        headers: {
          Authorization: `Bearer ${token}`
        },
        withCredentials: true
      }
    )
    const botMessage = {
      type: 'bot',
      text: response.data.answer
    }
    messages.value.push(botMessage)
  } catch (error) {
    const errorMessage = {
      type: 'bot',
      text: '⚠️ 추천 중 오류가 발생했습니다. 다시 시도해 주세요.'
    }
    messages.value.push(errorMessage)
    console.error('챗봇 오류:', error)
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

function scrollToBottom() {
  if (chatMessages.value) {
    chatMessages.value.scrollTop = chatMessages.value.scrollHeight
  }
}

function formatMessage(text) {
  return text
    .replace(/\n/g, "<br>")
    .replace(/\\"/g, '"')
}
</script>

<style scoped>
.chat-wrapper {
  background-color: #f9f5f0;
  min-height: 100vh;
  padding: 2rem 1rem;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.chat-card {
  background-color: #fff;
  padding: 2rem;
  border-radius: 20px;
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.08);
  max-width: 700px;
  width: 100%;
  display: flex;
  flex-direction: column;
}

.chat-title {
  font-size: 1.8rem;
  font-weight: 600;
  color: #2a2a2a;
  text-align: center;
  margin-bottom: 1.5rem;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  max-height: 500px;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background-color: #fafafa;
  border-radius: 12px;
}

.chat-message {
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border-radius: 12px;
  max-width: 85%;
  word-break: break-word;
}

.chat-message.user {
  background-color: #d2e3fc;
  align-self: flex-end;
  text-align: right;
  margin-left: auto;
}

.chat-message.bot {
  background-color: #f2e8dc;
  align-self: flex-start;
  text-align: left;
  margin-right: auto;
}

.chat-input-group {
  display: flex;
  gap: 0.5rem;
}

.chat-input {
  flex: 1;
  padding: 0.75rem 1rem;
  border-radius: 12px;
  border: 1px solid #ccc;
  font-size: 1rem;
}

.chat-send-button {
  padding: 0.75rem 1.2rem;
  background-color: #f2e8dc;
  border: 1px solid #d4c2aa;
  border-radius: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s;
}

.chat-send-button:hover {
  background-color: #e8dcca;
}

.chat-loading {
  margin-top: 1rem;
  text-align: center;
  color: #777;
  font-size: 0.95rem;
}
</style>
