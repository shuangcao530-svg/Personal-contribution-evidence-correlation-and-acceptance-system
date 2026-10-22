import { createApp } from "vue";
import { createRouter, createWebHashHistory } from "vue-router";
import App from "./App.vue";
import LoginPage from "./pages/LoginPage.vue";
import OverviewPage from "./pages/OverviewPage.vue";
import TasksPage from "./pages/TasksPage.vue";
import ContributionsPage from "./pages/ContributionsPage.vue";
import ContributionForm from "./pages/ContributionForm.vue";
import ContributionDetail from "./pages/ContributionDetail.vue";
import ReviewsPage from "./pages/ReviewsPage.vue";
import ReportsPage from "./pages/ReportsPage.vue";
import ProjectMatrixPage from "./pages/ProjectMatrixPage.vue";
import ActivityPage from "./pages/ActivityPage.vue";
import EvidenceLibraryPage from "./pages/EvidenceLibraryPage.vue";
import { auth, api, clearSession } from "./api";
import "./style.css";


const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/login", component: LoginPage, meta: { title: "登录" } },
    { path: "/", component: OverviewPage, meta: { title: "项目总览" } },
    { path: "/tasks", component: TasksPage, meta: { title: "任务与标准" } },
    {
      path: "/contributions",
      component: ContributionsPage,
      meta: { title: "贡献申报" },
    },
    {
      path: "/contributions/new",
      component: ContributionForm,
      meta: { title: "新增贡献申报", student: true },
    },
    {
      path: "/contributions/:id/revise",
      component: ContributionForm,
      meta: { title: "补充贡献证据", student: true },
    },
    {
      path: "/contributions/:id",
      component: ContributionDetail,
      meta: { title: "贡献详情" },
    },
    {
      path: "/reviews",
      component: ReviewsPage,
      meta: { title: "验收工作台", teacher: true },
    },
    { path: "/reports", component: ReportsPage, meta: { title: "个人报告" } },
    { path: "/projects/:id/matrix", component: ProjectMatrixPage, meta: { title: "项目贡献矩阵" } },
    { path: "/activity", component: ActivityPage, meta: { title: "项目动态" } },
    { path: "/evidence", component: EvidenceLibraryPage, meta: { title: "证据资料库" } },
    { path: "/:pathMatch(.*)*", redirect: "/" },
  ],
  scrollBehavior: () => ({ top: 0 }),
});
router.beforeEach(async (to) => {
  if (to.path === "/login") return true;
  if (!auth.token) return "/login";
  if (!auth.user) {
    try {
      auth.user = await api("/auth/me");
    } catch {
      clearSession();
      return "/login";
    }
  }
  if (to.meta.teacher && auth.user.role !== "TEACHER") return "/";
  if (to.meta.student && auth.user.role !== "STUDENT") return "/contributions";
});
createApp(App).use(router).mount("#app");
