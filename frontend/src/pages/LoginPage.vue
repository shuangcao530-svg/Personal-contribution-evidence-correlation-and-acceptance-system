<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { login } from "../api";
const router = useRouter(),
  username = ref(""),
  password = ref(""),
  busy = ref(false),
  error = ref("");
const accounts = [
  { username: "teacher", name: "陈老师", role: "教师" },
  { username: "student", name: "张三", role: "学生" },
  { username: "student2", name: "李四", role: "学生" },
  { username: "student3", name: "王五", role: "学生" },
];
function fill(account) {
  username.value = account.username;
  password.value = "demo1234";
  error.value = "";
}
async function submit() {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  try {
    await login(username.value.trim(), password.value);
    await router.push("/");
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="login-page">
    <div class="login-card">
    <div class="login-brand brand">
      <strong>证据台</strong><span>学生团队项目</span>
    </div>
    <div class="login-layout">
      <section class="login-intro">
        <h1>让每一份贡献<br />有据可查</h1>
        <p>连接任务、个人工作与验收依据。</p>
        <ol class="login-steps">
          <li><span>01</span>明确任务标准</li>
          <li><span>02</span>提交工作与证据</li>
          <li><span>03</span>教师逐项验收</li>
          <li><span>04</span>形成个人报告</li>
        </ol>
      </section>
      <section class="login-form-panel">
        <h2>登录工作空间</h2>
        <p class="muted">从一项有据可查的贡献开始。</p>
        <form @submit.prevent="submit" class="form-stack">
          <label
            >账号<input
              v-model="username"
              name="username"
              autocomplete="username"
              required
              placeholder="请输入账号" /></label
          ><label
            >密码<input
              v-model="password"
              name="password"
              type="password"
              autocomplete="current-password"
              required
              placeholder="请输入密码"
          /></label>
          <p v-if="error" class="notice error" role="alert">{{ error }}</p>
          <button class="button-primary login-submit" :disabled="busy">
            {{ busy ? "正在登录…" : "登录" }}
          </button>
        </form>
        <div class="demo-accounts">
          <h3>演示账号</h3>
          <p class="small muted">
            点击填入账号和密码，再点击登录。初始密码：demo1234
          </p>
          <div class="account-buttons">
            <button
              v-for="account in accounts"
              :key="account.username"
              class="button-secondary"
              @click="fill(account)"
            >
              {{ account.name }} <span class="muted">{{ account.role }}</span>
            </button>
          </div>
          <p class="small muted">演示内容仅用于了解流程，可创建自己的项目。</p>
        </div>
      </section>
    </div>
    </div>
  </main>
</template>

