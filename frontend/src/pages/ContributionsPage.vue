<script setup>
import { ref, computed, onMounted } from "vue";
import { auth, api, date } from "../api";
import StatusBadge from "../components/StatusBadge.vue";
const projects = ref([]),
  items = ref([]),
  projectId = ref(""),
  status = ref(""),
  loading = ref(true),
  error = ref("");
const filtered = computed(() =>
  items.value.filter(
    (c) =>
      (!projectId.value || String(c.projectId) === String(projectId.value)) &&
      (!status.value || c.status === status.value),
  ),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    [projects.value, items.value] = await Promise.all([
      api("/projects"),
      api("/contributions"),
    ]);
    items.value.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="workspace-page">
    <div class="page-heading">
      <div>
        <h1>贡献申报</h1>
        <p>
          {{
            auth.user?.role === "STUDENT"
              ? "说明你完成的工作，为每一项贡献关联证据。"
              : "查看项目成员的工作说明、证据与验收进度。"
          }}
        </p>
      </div>
      <RouterLink
        v-if="auth.user?.role === 'STUDENT'"
        class="button-primary"
        to="/contributions/new"
        >申报贡献</RouterLink
      >
    </div>
    <div class="toolbar">
      <label class="inline-field"
        >所属项目<select v-model="projectId">
          <option value="">全部项目</option>
          <option v-for="p in projects" :key="p.id" :value="p.id">
            {{ p.name }}
          </option>
        </select></label
      ><label class="inline-field"
        >验收状态<select v-model="status">
          <option value="">全部状态</option>
          <option value="SUBMITTED">待验收</option>
          <option value="RETURNED">待补充</option>
          <option value="APPROVED">已通过</option>
        </select></label
      >
    </div>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <p v-if="loading" class="loading">正在加载申报…</p>
    <div v-else-if="!filtered.length" class="panel empty-state">
      暂无符合条件的贡献申报。
    </div>
    <div v-else class="panel table-panel">
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>贡献内容 / 所属任务</th>
              <th>提交人</th>
              <th>状态</th>
              <th>版本</th>
              <th>更新时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.id">
              <td>
                <RouterLink
                  class="table-title"
                  :to="`/contributions/${item.id}`"
                  >{{ item.title }}</RouterLink
                ><span class="table-subtitle"
                  >{{ item.projectName }} · {{ item.taskTitle }}</span
                >
              </td>
              <td>{{ item.studentName }}</td>
              <td><StatusBadge :status="item.status" /></td>
              <td>第 {{ item.currentVersion }} 版</td>
              <td>{{ date(item.updatedAt) }}</td>
              <td>
                <div class="row-actions">
                  <RouterLink
                    class="text-button"
                    :to="`/contributions/${item.id}`"
                    >查看详情</RouterLink
                  ><RouterLink
                    v-if="
                      auth.user?.role === 'STUDENT' &&
                      item.status === 'RETURNED'
                    "
                    class="text-button"
                    :to="`/contributions/${item.id}/revise`"
                    >补充提交</RouterLink
                  ><RouterLink
                    v-if="
                      auth.user?.role === 'TEACHER' &&
                      item.status === 'SUBMITTED'
                    "
                    class="text-button"
                    :to="`/reviews?contributionId=${item.id}`"
                    >验收</RouterLink
                  >
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
