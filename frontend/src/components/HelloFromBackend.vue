<template>
  <div>
    <h2>백엔드에서 받은 인사:</h2>
    <p v-if="loading">불러오는 중...</p>
    <p v-else>{{ message }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const message = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await axios.get('/api/hello') // 프록시 덕분에 localhost:8080으로 자동 연결됨
    message.value = res.data
  } catch (err) {
    message.value = '백엔드 요청 실패!'
    console.error(err)
  } finally {
    loading.value = false
  }
})
</script>
