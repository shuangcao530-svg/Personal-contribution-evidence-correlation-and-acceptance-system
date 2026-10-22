<script setup>
import { ref, onMounted } from "vue";
import { auth, api, post, date } from "../api";
import ModalDialog from "../components/ModalDialog.vue";
import StatusBadge from "../components/StatusBadge.vue";
const stats = ref(null),
  projects = ref([]),
  contributions = ref([]),
  tasks = ref([]),
  loading = ref(true),
  error = ref("");
const creating = ref(false),
  saving = ref(false),
  formError = ref(""),
  users = ref([]);
const name = ref(""),
  description = ref(""),
  memberIds = ref([]);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    [stats.value, projects.value, contributions.value, tasks.value] = await Promise.all([
      api("/dashboard"),
      api("/projects"),
      api("/contributions"),
      api("/tasks"),
    ]);
    contributions.value.sort(
      (a, b) => new Date(b.updatedAt) - new Date(a.updatedAt),
    );
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
function projectTasks(projectId) {
  return tasks.value.filter((task) => task.projectId === projectId).slice(0, 3);
}
function projectProgress(projectId) {
  const eligible=tasks.value.filter(task=>task.projectId===projectId&&(auth.user?.role!=="STUDENT"||task.assigneeId===auth.user.id));
  const approved=new Set(contributions.value.filter(item=>item.projectId===projectId&&item.status==="APPROVED").map(item=>item.taskId));
  const done=eligible.filter(task=>approved.has(task.id)).length;
  return { total:eligible.length, done, percent:eligible.length?Math.round(done*100/eligible.length):0 };
}
async function openCreate() {
  formError.value = "";
  name.value = "";
  description.value = "";
  memberIds.value = [];
  creating.value = true;
  try {
    users.value = (await api("/users")).filter((u) => u.role === "STUDENT");
  } catch (err) {
    formError.value = err.message;
  }
}
async function create() {
  if (saving.value) return;
  formError.value = "";
  if (!memberIds.value.length) {
    formError.value = "请至少选择一名学生成员。";
    return;
  }
  saving.value = true;
  try {
    await post("/projects", {
      name: name.value.trim(),
      description: description.value.trim(),
      memberIds: memberIds.value,
    });
    creating.value = false;
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
  <div class="overview-page">
    <section class="overview-hero">
      <div class="page-heading overview-heading">
        <div>
          <h1>你好，{{ auth.user?.name }}！</h1>
          <p class="hero-description">{{ auth.user?.role === 'TEACHER' ? '关注每一份工作，依据证据确认学生的贡献。' : '专注做好每一份贡献，用证据记录你的成长。' }}</p>
        </div>
        <button v-if="auth.user?.role === 'TEACHER'" class="button-primary" @click="openCreate">创建项目</button>
        <RouterLink v-else class="button-primary" to="/contributions/new">申报贡献</RouterLink>
      </div>
    </section>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <p v-if="loading" class="loading" role="status">正在加载项目…</p>
    <template v-if="stats">
      <section class="stats-strip" aria-label="项目统计">
        <div class="stat-card">
          <span class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M3 7h7l2 2h9v10H3zM3 7V5h7l2 2"/></svg></span>
          <span class="stat-copy"><span>进行中的项目</span><strong>{{ stats.projects }}</strong></span>
        </div>
        <div class="stat-card">
          <span class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M9 5h11M9 12h11M9 19h11"/><circle cx="4" cy="5" r="1"/><circle cx="4" cy="12" r="1"/><circle cx="4" cy="19" r="1"/></svg></span>
          <span class="stat-copy"><span>项目任务</span><strong>{{ stats.tasks }}</strong></span>
        </div>
        <div class="stat-card">
          <span class="stat-icon warm" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg></span>
          <span class="stat-copy"><span>待验收申报</span><strong>{{ stats.pending }}</strong></span>
        </div>
        <div class="stat-card">
          <span class="stat-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m8 12 2.5 2.5L16.5 9"/></svg></span>
          <span class="stat-copy"><span>已确认贡献</span><strong>{{ stats.approved }}</strong></span>
        </div>
      </section>
      <section v-if="stats.attentionTasks?.length" class="panel deadline-panel">
        <div class="section-heading"><div><h2>截止提醒</h2><p>{{ stats.overdue }} 项已逾期 · {{ stats.dueSoon }} 项将在 7 天内截止</p></div><RouterLink class="text-button" to="/tasks">查看任务 →</RouterLink></div>
        <ul class="deadline-list"><li v-for="task in stats.attentionTasks" :key="task.id"><span class="deadline-state" :class="task.urgency">{{ task.urgency==='OVERDUE'?'已逾期':'即将截止' }}</span><div><RouterLink :to="'/tasks?projectId='+task.projectId">{{task.title}}</RouterLink><small>{{task.projectName}} · {{task.assigneeName}}</small></div><time>{{task.dueDate}}</time></li></ul>
      </section>
      <div class="overview-work-row">
        <section class="panel project-section">
          <div class="section-heading"><h2>当前项目</h2><RouterLink class="text-button" to="/tasks">查看项目任务 <span aria-hidden="true">→</span></RouterLink></div>
          <div v-if="!projects.length" class="panel empty-state">
            还没有项目。{{ auth.user?.role === "TEACHER" ? "创建项目并添加学生，开始分配任务。" : "请联系老师将你加入项目。" }}
          </div>
          <article v-for="project in projects" :key="project.id" class="project-row">
            <div class="project-illustration" aria-hidden="true">
              <img src="/campus-project.png" alt="" loading="lazy" />
            </div>
            <div class="project-info">
              <div class="project-title-row"><h3>{{ project.name }}</h3><span class="status-badge approved">进行中</span></div>
              <p class="project-meta">{{ project.id === 1 && project.name === "校园二手交易平台" ? "软件工程课程项目" : "团队项目" }}</p>
              <p class="project-description">{{ project.description }}</p>
              <div v-if="projectProgress(project.id).total" class="project-progress">
                <div><span>{{ auth.user?.role==='TEACHER'?'项目验收进度':'我的任务进度' }}</span><strong>{{projectProgress(project.id).done}} / {{projectProgress(project.id).total}} 项已通过</strong></div>
                <div class="project-progress-track" role="progressbar" :aria-valuenow="projectProgress(project.id).percent" aria-valuemin="0" aria-valuemax="100" :aria-label="'已验收 '+projectProgress(project.id).percent+'%'"><span :style="{width:projectProgress(project.id).percent+'%'}"></span></div>
              </div>
            </div>
            <div class="project-facts">
                <span><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="5" width="17" height="16" rx="2"/><path d="M8 3v4M16 3v4M4 10h16"/></svg>{{ date(project.createdAt).slice(0, 7) }}</span>
                <span><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="9" cy="8" r="3.2"/><path d="M3.5 20v-1.5a5.5 5.5 0 0 1 11 0V20zM16 5.5a3.2 3.2 0 0 1 0 6.3M17 15a5 5 0 0 1 3.5 4.8V20"/></svg>{{ project.members.length }}位成员</span>
                <span><svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 20v-1.2a7 7 0 0 1 14 0V20M18 4l1.5 1.5L22 3"/></svg>指导教师 · {{ project.ownerName }}</span>
              <RouterLink class="text-button project-link" :to="`/projects/${project.id}/matrix`">贡献矩阵 <span aria-hidden="true">→</span></RouterLink>
            </div>

              <div v-if="projectTasks(project.id).length" class="project-task-list">
                <span>项目任务（{{ projectTasks(project.id).length }}）</span>
                <RouterLink v-for="task in projectTasks(project.id)" :key="task.id" :to="`/tasks?projectId=${project.id}`">{{ task.title }}</RouterLink>
              </div>
          </article>
        </section>
        <section class="panel workflow-panel">
          <div class="section-heading"><h2>接下来做什么</h2><RouterLink class="text-button" :to="auth.user?.role === 'TEACHER' ? '/reviews' : '/tasks'">{{ auth.user?.role === 'TEACHER' ? '查看详情' : '查看任务' }} <span aria-hidden="true">→</span></RouterLink></div>
          <ol class="workflow-steps">
            <li><span>1</span><div><strong>申报贡献</strong><small>提交工作成果、说明及相关证据</small></div></li>
            <li><span>2</span><div><strong>等待教师验收</strong><small>依据任务标准逐项审核</small></div></li>
            <li><span>3</span><div><strong>查看验收结果</strong><small>通过、退回或要求补充</small></div></li>
            <li><span>4</span><div><strong>生成个人报告</strong><small>汇总可追溯的过程证据</small></div></li>
          </ol>
          <p class="workflow-note"><strong>持续记录，积累你的项目成果</strong>让每一份贡献都有据可查。</p>
        </section>
      </div>
      <section class="panel recent-panel">
        <div class="section-heading"><h2>最近贡献</h2><RouterLink class="text-button" to="/contributions">查看全部 <span aria-hidden="true">→</span></RouterLink></div>
        <div v-if="!contributions.length" class="empty-state">还没有贡献申报。完成任务后，提交工作说明和证据。</div>
        <div v-else class="table-scroll">
          <table class="data-table overview-table">
            <thead><tr><th>#</th><th>提交时间</th><th>成员</th><th>任务名称</th><th>贡献描述</th><th>相关证据</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(item, index) in contributions.slice(0, 5)" :key="item.id">
                <td>{{ index + 1 }}</td>
                <td>{{ date(item.updatedAt || item.createdAt, true) }}</td>
                <td>{{ item.studentName }}</td>
                <td><RouterLink :to="`/contributions/${item.id}`">{{ item.taskTitle }}</RouterLink></td>
                <td class="contribution-summary">{{ item.title }}</td>
                <td><RouterLink :to="`/contributions/${item.id}`">{{ item.revisions?.[item.revisions.length - 1]?.evidence?.length || 0 }} 个文件</RouterLink></td>
                <td><StatusBadge :status="item.status" /></td>
                <td><RouterLink :to="`/contributions/${item.id}`">查看</RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <p v-if="projects.some((p) => p.id === 1 && p.name === '校园二手交易平台')" class="demo-footer">当前包含演示项目，可创建自己的项目开始使用。</p>
    </template>
    <ModalDialog v-if="creating" title="创建项目" :busy="saving" @close="creating = false">
      <form class="form-stack" @submit.prevent="create">
        <label>项目名称<input v-model="name" maxlength="100" required placeholder="例如：校园二手交易平台" /></label>
        <label>项目说明<textarea v-model="description" rows="3" maxlength="2000" required placeholder="介绍项目目标与预期成果"></textarea></label>
        <fieldset>
          <legend>学生成员 <span class="muted">（至少选择一人）</span></legend>
          <div class="member-options"><label v-for="user in users" :key="user.id" class="check-option"><input type="checkbox" v-model="memberIds" :value="user.id" /><span>{{ user.name }} <small class="muted">{{ user.username }}</small></span></label></div>
          <p v-if="!users.length" class="muted small">暂无可选学生。</p>
        </fieldset>
        <p class="small muted">你将作为指导教师加入该项目。</p>
        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="form-actions"><button type="button" class="button-secondary" :disabled="saving" @click="creating = false">取消</button><button class="button-primary" :disabled="saving">{{ saving ? "正在创建…" : "创建项目" }}</button></div>
      </form>
    </ModalDialog>
  </div>
</template>








