<template>
  <div class="test">
    <h1>测试页面</h1>
    <p>如果你看到这个页面，说明路由正常工作！</p>
    <el-button @click="goBack">返回</el-button>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}
</script>

<style scoped>
.test {
  text-align: center;
  padding: 50px;
}
</style>