<script setup>
import { ref, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth, api, clearSession } from "../api";
const route = useRoute(),
  router = useRouter(),
  open = ref(false),
  exiting = ref(false);
const navigation = computed(() => [
  { path: "/", label: "项目总览" },
  { path: "/tasks", label: "任务与标准" },
  { path: "/evidence", label: "证据资料库" },
  { path: "/activity", label: "项目动态" },
  { path: "/contributions", label: "贡献申报" },
  ...(auth.user?.role === "TEACHER"
    ? [{ path: "/reviews", label: "验收工作台" }]
    : []),
  { path: "/reports", label: "个人报告" },
]);
const today = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "long",
  day: "numeric",
}).format(new Date());
async function logout() {
  exiting.value = true;
  try {
    await api("/auth/logout", { method: "POST" });
  } catch {
    /* Local logout remains available offline. */
  }
  clearSession();
  await router.push("/login");
  exiting.value = false;
}
</script>
<template>
  <div class="workspace">
    <aside class="sidebar" :class="{ 'sidebar-open': open }">
      <RouterLink class="brand" to="/" @click="open = false">
        <span class="brand-mark" aria-hidden="true"><svg viewBox="0 0 40 40"><path d="M5 7.5 17 10v21L5 28zM35 7.5 23 10v21l12-3zM20 9.5V32M17 34l3 2 3-2"/></svg></span>
        <span class="brand-copy"><strong>证据台</strong><small>以证见成长</small></span>
      </RouterLink>
      <button
        class="mobile-nav-toggle button-quiet"
        @click="open = !open"
        :aria-expanded="open"
        aria-controls="main-navigation"
      >{{ open ? "收起导航" : "展开导航" }}</button>
      <nav id="main-navigation" aria-label="工作空间导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          active-class=""
          exact-active-class=""
          :class="{
            selected:
              item.path === '/'
                ? route.path === '/'
                : route.path.startsWith(item.path),
          }"
          @click="open = false"
        >
          <svg v-if="item.path === '/'" viewBox="0 0 24 24" aria-hidden="true"><path d="m3.5 10 8.5-7 8.5 7v10.5H14v-7h-4v7H3.5z" /></svg>
          <svg v-else-if="item.path === '/tasks'" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5h11M9 12h11M9 19h11"/><circle cx="4" cy="5" r="1"/><circle cx="4" cy="12" r="1"/><circle cx="4" cy="19" r="1"/></svg>
          <svg v-else-if="item.path === '/contributions'" viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3.5h7l5 5v12H7zM14 3.5v5h5M10 13h6M10 17h6"/></svg>
          <svg v-else-if="item.path === '/reviews'" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16.5 9"/></svg>
          <svg v-else viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20V12h4v8M10 20V7h4v13M16 20V3h4v17M3 20.5h18"/></svg>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-profile">
        <span class="profile-avatar" aria-hidden="true">{{ auth.user?.name?.slice(0, 1) || "同" }}</span>
        <span class="profile-copy"><strong>{{ auth.user?.name }}</strong><small>{{ auth.user?.role === "TEACHER" ? "指导教师" : "项目学生" }}</small></span>
        <span class="profile-chevron" aria-hidden="true">›</span>
      </div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <div class="topbar-breadcrumb">{{ route.meta.title || "项目总览" }} <span class="breadcrumb-slash">/</span><span>欢迎回来，{{ auth.user?.name }}</span></div>
        <div class="topbar-actions">
          <span class="today">{{ today }}</span><span class="topbar-divider"></span>
          <button class="text-button" @click="logout" :disabled="exiting">{{ exiting ? "正在退出" : "退出登录" }}</button>
        </div>
      </header>
      <main id="main-content"><slot /></main>
    </div>
  </div>
</template>


