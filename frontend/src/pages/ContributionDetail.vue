<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { auth, api, timestamp } from "../api";
import StatusBadge from "../components/StatusBadge.vue";
import RevisionView from "../components/RevisionView.vue";
const route = useRoute(),
  item = ref(null),
  selectedVersion = ref(null),
  loading = ref(true),
  error = ref("");
const selected = computed(() =>
  item.value?.revisions.find(
    (r) => r.version === Number(selectedVersion.value),
  ),
);
const previousVersion = computed(() => {
  if (!selected.value || selected.value.version <= 1) return null;
  return item.value?.revisions.find((revision) => revision.version === selected.value.version - 1) || null;
});
const traceRows = computed(() => {
  if (!selected.value) return [];
  const groups = new Map();
  for (const proof of selected.value.evidence) {
    if (!groups.has(proof.criterionId)) groups.set(proof.criterionId, { criterionId: proof.criterionId, criterionText: proof.criterionText, evidence: [] });
    groups.get(proof.criterionId).evidence.push(proof);
  }
  return [...groups.values()].map((group) => ({ ...group, review: selected.value.review?.items?.find((entry) => entry.criterionId === group.criterionId) || null }));
});
const auditTrail = computed(() => (item.value?.revisions || []).flatMap((revision) => {
  const events = [{ id: `submission-${revision.id}`, type: "submission", version: revision.version, title: `提交第 ${revision.version} 版`, actor: item.value.studentName, time: revision.createdAt, comment: revision.title }];
  if (revision.review) events.push({ id: `review-${revision.review.id}`, type: "review", version: revision.version, title: revision.review.decision === "APPROVED" ? "验收通过" : "退回补充", actor: revision.review.reviewerName, time: revision.review.createdAt, comment: revision.review.comment });
  return events;
}));
const versionDiff = computed(() => {
  if (!selected.value || !previousVersion.value) return null;
  const before = previousVersion.value, after = selected.value;
  const signature = (proof) => `${proof.id}:${proof.criterionId}:${proof.location}`;
  const beforeProofs = new Map(before.evidence.map((proof) => [signature(proof), proof]));
  const afterProofs = new Map(after.evidence.map((proof) => [signature(proof), proof]));
  const added = [...afterProofs].filter(([key]) => !beforeProofs.has(key)).map(([, proof]) => proof);
  const removed = [...beforeProofs].filter(([key]) => !afterProofs.has(key)).map(([, proof]) => proof);
  const updated = [...afterProofs].filter(([key, proof]) => beforeProofs.has(key) && beforeProofs.get(key).note !== proof.note).map(([, proof]) => proof);
  return { before, after, added, removed, updated, titleChanged: before.title !== after.title, descriptionChanged: before.description !== after.description };
});
async function load() {
  loading.value = true;
  error.value = "";
  try {
    item.value = await api(`/contributions/${route.params.id}`);
    selectedVersion.value = item.value.currentVersion;
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="workspace-page narrow-page">
    <div class="page-heading">
      <div>
        <h1>贡献详情</h1>
        <p>每一版工作说明、关联证据与验收意见均保留。</p>
      </div>
      <RouterLink class="text-button" to="/contributions">返回列表</RouterLink>
    </div>
    <p v-if="loading" class="loading">正在加载贡献记录…</p>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <template v-if="item"
      ><section class="panel detail-summary">
        <div class="section-title-inline">
          <h2>{{ item.title }}</h2>
          <StatusBadge :status="item.status" />
        </div>
        <p class="muted">{{ item.projectName }} / {{ item.taskTitle }}</p>
        <div class="task-meta">
          <span>贡献人：{{ item.studentName }}</span
          ><span>最近更新：{{ timestamp(item.updatedAt) }}</span>
        </div>
        <div v-if="item.status !== 'APPROVED'" class="detail-actions">
          <RouterLink
            v-if="auth.user?.role === 'STUDENT' && item.status === 'RETURNED'"
            class="button-primary"
            :to="`/contributions/${item.id}/revise`"
            >补充并重新提交</RouterLink
          ><RouterLink
            v-if="auth.user?.role === 'TEACHER' && item.status === 'SUBMITTED'"
            class="button-primary"
            :to="`/reviews?contributionId=${item.id}`"
            >验收这一份贡献</RouterLink
          >
        </div>
      </section>
      <section v-if="selected" class="panel traceability-panel">
        <div class="traceability-heading"><div><h2>贡献追溯</h2><p>从项目任务、验收标准一路核对到证据与验收结论。</p></div><StatusBadge :status="item.status" /></div>
        <ol class="traceability-flow"><li><small>项目</small><strong>{{ item.projectName }}</strong></li><li><small>负责人</small><strong>{{ item.studentName }}</strong></li><li><small>任务</small><strong>{{ item.taskTitle }}</strong></li><li><small>当前版本</small><strong>第 {{ selected.version }} 版</strong></li><li><small>验收</small><strong>{{ selected.review ? (selected.review.decision === 'APPROVED' ? '已通过' : '待补充') : '待验收' }}</strong></li></ol>
        <div class="traceability-criteria"><h3>标准与证据对应</h3><div v-for="row in traceRows" :key="row.criterionId" class="traceability-row"><div class="traceability-standard"><span>验收标准</span><strong>{{ row.criterionText }}</strong><StatusBadge v-if="row.review" :status="row.review.passed ? 'APPROVED' : 'RETURNED'" /><span v-else class="status-badge submitted">待验收</span></div><span class="traceability-arrow" aria-hidden="true">→</span><div class="traceability-proofs"><span class="small muted">{{ row.evidence.length }} 条关联证据</span><div v-for="proof in row.evidence" :key="`${proof.id}-${proof.location}`"><strong>{{ proof.title }}</strong><small>{{ proof.fileName || proof.url }} · {{ proof.location }}</small></div><p v-if="row.review?.comment" class="small muted">验收意见：{{ row.review.comment }}</p></div></div><p v-if="!traceRows.length" class="empty-state compact">该版本还没有可展示的证据关联。</p></div>
      </section>
      <section v-if="auditTrail.length" class="panel audit-panel"><div><h2>操作时间线</h2><p>提交版本与教师验收均来自系统保存的记录。</p></div><ol class="audit-timeline"><li v-for="event in auditTrail" :key="event.id" :class="event.type"><span class="audit-marker"></span><div><strong>{{ event.title }}</strong><small>{{ event.actor }} · {{ timestamp(event.time) }}</small><p v-if="event.type === 'review' && event.comment">{{ event.comment }}</p><small v-else>{{ event.comment }}</small></div></li></ol></section>
      <section class="panel form-section">
        <div class="revision-toolbar">
          <h2>修订与验收记录</h2>
          <label class="inline-field"
            >查看版本<select v-model="selectedVersion">
              <option
                v-for="revision in [...item.revisions].reverse()"
                :key="revision.id"
                :value="revision.version"
              >
                第 {{ revision.version }} 版{{
                  revision.version === item.currentVersion ? " · 最新" : ""
                }}
              </option>
            </select></label
          >
        </div>
        <details v-if="versionDiff" class="version-diff">
          <summary>查看第 {{ versionDiff.before.version }} 版与第 {{ versionDiff.after.version }} 版的差异</summary>
          <ul><li v-if="versionDiff.titleChanged">标题：{{ versionDiff.before.title }} → {{ versionDiff.after.title }}</li><li v-if="versionDiff.descriptionChanged">工作说明已修改</li><li v-for="proof in versionDiff.added" :key="`add-${proof.id}-${proof.criterionId}-${proof.location}`">新增关联：{{ proof.title }} · {{ proof.criterionText }} · {{ proof.location }}</li><li v-for="proof in versionDiff.removed" :key="`remove-${proof.id}-${proof.criterionId}-${proof.location}`">移除关联：{{ proof.title }} · {{ proof.criterionText }} · {{ proof.location }}</li><li v-for="proof in versionDiff.updated" :key="`update-${proof.id}-${proof.criterionId}-${proof.location}`">修改关联说明：{{ proof.title }} · {{ proof.criterionText }}</li><li v-if="!versionDiff.titleChanged && !versionDiff.descriptionChanged && !versionDiff.added.length && !versionDiff.removed.length && !versionDiff.updated.length">标题、说明和证据关联均未变化。</li></ul>
        </details>
        <p
          v-if="selected?.version !== item.currentVersion"
          class="notice warning"
        >
          正在查看历史版本。当前申报状态以最新版本为准。
        </p>
        <RevisionView v-if="selected" :revision="selected" /></section
    ></template>
  </div>
</template>
