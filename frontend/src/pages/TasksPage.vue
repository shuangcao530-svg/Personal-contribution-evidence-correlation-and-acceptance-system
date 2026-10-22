<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { auth, api, post, date } from "../api";
import ModalDialog from "../components/ModalDialog.vue";
const route = useRoute(),
  projects = ref([]),
  tasks = ref([]),
  filter = ref(route.query.projectId || ""),
  searchQuery = ref(""),
  loading = ref(true),
  error = ref("");
const modal = ref(false),
  saving = ref(false),
  formError = ref(""),
  form = ref({
    projectId: "",
    title: "",
    description: "",
    assigneeId: "",
    dueDate: "",
    criteria: "",
  });
const normalizedQuery = computed(() =>
  searchQuery.value.normalize("NFKC").trim().toLocaleLowerCase(),
);
const filteredTasks = computed(() => {
  const keywords = normalizedQuery.value.split(/\s+/).filter(Boolean);
  return tasks.value.filter((task) => {
    if (filter.value && String(task.projectId) !== String(filter.value)) return false;
    if (!keywords.length) return true;
    const searchableText = [
      task.projectName,
      task.title,
      task.description,
      task.assigneeName,
      ...(task.criteria || []).map((criterion) => criterion.text),
    ]
      .filter(Boolean)
      .join(" ")
      .normalize("NFKC")
      .toLocaleLowerCase();
    return keywords.every((keyword) => searchableText.includes(keyword));
  });
});
const selectedProject = computed(() =>
  projects.value.find((p) => String(p.id) === String(form.value.projectId)),
);
const students = computed(
  () =>
    selectedProject.value?.members.filter((m) => m.role === "STUDENT") || [],
);
const filterProject = computed(() =>
  projects.value.find((p) => String(p.id) === String(filter.value)),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    [projects.value, tasks.value] = await Promise.all([
      api("/projects"),
      api("/tasks"),
    ]);
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
function openCreate() {
  formError.value = "";
  form.value = {
    projectId: filter.value || projects.value[0]?.id || "",
    title: "",
    description: "",
    assigneeId: "",
    dueDate: "",
    criteria: "",
  };
  modal.value = true;
}
async function create() {
  if (saving.value) return;
  formError.value = "";
  const criteria = form.value.criteria
    .split("\n")
    .map((v) => v.trim())
    .filter(Boolean);
  if (!criteria.length) {
    formError.value = "请填写至少一条验收标准。";
    return;
  }
  saving.value = true;
  try {
    await post("/tasks", {
      ...form.value,
      projectId: Number(form.value.projectId),
      assigneeId: Number(form.value.assigneeId),
      criteria,
    });
    modal.value = false;
    await load();
  } catch (err) {
    formError.value = err.message;
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="workspace-page">
    <div class="page-heading">
      <div>
        <h1>任务与标准</h1>
        <p>先明确完成要求，再让工作与证据逐项对应。</p>
      </div>
      <button
        v-if="auth.user?.role === 'TEACHER'"
        class="button-primary"
        :disabled="!projects.length"
        @click="openCreate"
      >
        创建任务
      </button>
    </div>
    <div class="tasks-toolbar">
      <label class="inline-field tasks-project-filter"
        >所属项目<select v-model="filter">
          <option value="">全部项目</option>
          <option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.name }}
          </option>
        </select></label
      ><label class="tasks-search">
        <span class="sr-only">搜索任务与验收标准</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 5 5"/></svg>
        <input v-model="searchQuery" type="search" placeholder="搜索任务名称、工作要求或验收标准" autocomplete="off" />
        <button v-if="searchQuery" type="button" class="tasks-search-clear" aria-label="清空搜索" @click="searchQuery = ''">清空</button>
      </label>
      <span class="muted small tasks-result-count">{{ normalizedQuery ? `找到 ${filteredTasks.length} 项任务` : `共 ${filteredTasks.length} 项任务` }}</span>
      <p class="tasks-search-hint">支持项目、任务名称、工作要求、负责人和验收标准；多个关键词用空格分隔。</p>
    </div>
    <p v-if="filterProject" class="project-context">
      <strong>{{ filterProject.name }}</strong
      ><span>{{ filterProject.description }}</span
      ><span
        >项目成员：{{
          filterProject.members.map((m) => m.name).join("、")
        }}</span
      >
    </p>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <p v-if="loading" class="loading">正在加载任务…</p>
    <div v-else-if="!filteredTasks.length" class="panel empty-state">
      <template v-if="normalizedQuery">没有找到匹配的任务或验收标准。试试更短的关键词，或清空搜索。</template>
      <template v-else>还没有任务。{{
        auth.user?.role === "TEACHER"
          ? "创建任务，分配学生并明确验收标准。"
          : "请等待老师分配任务。"
      }}</template>
    </div>
    <div v-else class="task-list">
      <article
        class="panel task-card"
        v-for="task in filteredTasks"
        :key="task.id"
      >
        <div class="task-heading">
          <div>
            <p class="small muted">{{ task.projectName }}</p>
            <h2>{{ task.title }}</h2>
          </div>
          <RouterLink
            v-if="
              auth.user?.role === 'STUDENT' && task.assigneeId === auth.user.id
            "
            class="button-secondary"
            :to="`/contributions/new?taskId=${task.id}`"
            >申报贡献</RouterLink
          >
        </div>
        <p class="preserve-text">{{ task.description }}</p>
        <div class="task-meta">
          <span>负责人：{{ task.assigneeName }}</span
          ><span>截止日期：{{ date(task.dueDate) }}</span>
        </div>
        <div class="criteria-section">
          <h3>验收标准</h3>
          <ol class="criteria-list">
            <li v-for="criterion in task.criteria" :key="criterion.id">
              {{ criterion.text }}
            </li>
          </ol>
        </div>
      </article>
    </div>
    <ModalDialog
      v-if="modal"
      title="创建任务"
      :busy="saving"
      @close="modal = false"
      ><form class="form-stack" @submit.prevent="create">
        <label
          >所属项目<select
            v-model="form.projectId"
            required
            @change="form.assigneeId = ''"
          >
            <option disabled value="">请选择项目</option>
            <option v-for="p in projects" :key="p.id" :value="p.id">
              {{ p.name }}
            </option>
          </select></label
        ><label
          >任务名称<input
            v-model="form.title"
            required
            maxlength="150"
            placeholder="例如：实现用户登录接口" /></label
        ><label
          >工作要求<textarea
            v-model="form.description"
            rows="3"
            required
            maxlength="4000"
            placeholder="描述这项任务应完成的工作"
          ></textarea>
        </label>
        <div class="form-grid">
          <label
            >负责人<select v-model="form.assigneeId" required>
              <option disabled value="">请选择学生</option>
              <option
                v-for="student in students"
                :key="student.id"
                :value="student.id"
              >
                {{ student.name }}
              </option>
            </select></label
          ><label
            >截止日期<input v-model="form.dueDate" type="date" required
          /></label>
        </div>
        <label
          >验收标准<textarea
            v-model="form.criteria"
            rows="4"
            required
            maxlength="6000"
            placeholder="每行填写一条可检查的标准&#10;支持正确账号密码登录&#10;密码错误时显示明确提示"
          ></textarea>
        </label>
        <p class="small muted">
          标准发布后保留不变，供后续证据关联和历史验收追溯。
        </p>
        <p v-if="formError" class="notice error" role="alert">
          {{ formError }}
        </p>
        <div class="form-actions">
          <button
            type="button"
            class="button-secondary"
            :disabled="saving"
            @click="modal = false"
          >
            取消</button
          ><button class="button-primary" :disabled="saving">
            {{ saving ? "正在创建…" : "创建任务" }}
          </button>
        </div>
      </form></ModalDialog
    >
  </div>
</template>
