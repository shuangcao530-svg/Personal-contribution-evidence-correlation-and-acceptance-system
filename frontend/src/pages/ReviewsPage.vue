<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute } from "vue-router";
import { api, post } from "../api";
import RevisionView from "../components/RevisionView.vue";
const route = useRoute(),
  list = ref([]),
  selectedId = ref(""),
  selected = ref(null),
  loading = ref(true),
  detailLoading = ref(false),
  error = ref(""),
  reviewError = ref(""),
  saving = ref(false),
  feedback = ref("");
const items = ref([]),
  comment = ref("");
const revision = computed(() => selected.value?.revisions.at(-1));
const allAnswered = computed(
  () => items.value.length > 0 && items.value.every((i) => i.result !== ""),
);
const allPassed = computed(
  () => allAnswered.value && items.value.every((i) => i.result === "true"),
);
const hasFailure = computed(
  () => allAnswered.value && items.value.some((i) => i.result === "false"),
);
async function selectContribution() {
  if (saving.value) return;
  selected.value = null;
  items.value = [];
  comment.value = "";
  reviewError.value = "";
  if (!selectedId.value) return;
  detailLoading.value = true;
  try {
    selected.value = await api(`/contributions/${selectedId.value}`);
    const unique = new Map(
      revision.value.evidence.map((e) => [e.criterionId, e.criterionText]),
    );
    items.value = [...unique].map(([criterionId, text]) => ({
      criterionId,
      text,
      result: "",
      comment: "",
    }));
  } catch (err) {
    reviewError.value = err.message;
  } finally {
    detailLoading.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    list.value = (await api("/contributions")).filter(
      (c) => c.status === "SUBMITTED",
    );
    selectedId.value =
      list.value.find(
        (c) => String(c.id) === String(route.query.contributionId),
      )?.id ||
      list.value[0]?.id ||
      "";
    await selectContribution();
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
async function submit(decision) {
  if (saving.value) return;
  reviewError.value = "";
  feedback.value = "";
  if (!allAnswered.value) {
    reviewError.value = "请逐项填写所有关联标准的验收结果。";
    return;
  }
  if (decision === "APPROVED" && !allPassed.value) {
    reviewError.value = "全部关联标准通过后，才能确认贡献。";
    return;
  }
  if (decision === "RETURNED" && (!hasFailure.value || !comment.value.trim())) {
    reviewError.value = "退回时，至少一项标准应为不通过，并填写补充意见。";
    return;
  }
  saving.value = true;
  try {
    await post(`/contributions/${selected.value.id}/reviews`, {
      revisionId: revision.value.id,
      decision,
      comment: comment.value.trim(),
      items: items.value.map((i) => ({
        criterionId: i.criterionId,
        passed: i.result === "true",
        comment: i.comment.trim(),
      })),
    });
    feedback.value =
      decision === "APPROVED"
        ? "验收已通过，这份贡献已计入个人报告。"
        : "已退回补充，学生可以根据意见提交新版本。";
  } catch (err) {
    reviewError.value = err.message;
    saving.value = false;
    return;
  }
  saving.value = false;
  await load();
}
onMounted(load);
</script>
<template>
  <div class="workspace-page">
    <div class="page-heading">
      <div>
        <h1>验收工作台</h1>
        <p>同时核实成果是否达标，以及材料能否支持学生的贡献声明。</p>
      </div>
      <span class="muted">{{ list.length }} 份待验收</span>
    </div>
    <p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <p v-if="loading" class="loading">正在加载待验收申报…</p>
    <div v-else-if="!list.length" class="panel empty-state">
      <h2>当前没有待验收的申报</h2>
      <p>新的申报或补充版本提交后，会出现在这里。</p>
      <RouterLink class="text-button" to="/contributions"
        >查看全部贡献</RouterLink
      >
    </div>
    <template v-else
      ><div class="toolbar">
        <label class="inline-field review-select"
          >选择申报<select
            v-model="selectedId"
            :disabled="saving || detailLoading"
            @change="selectContribution"
          >
            <option v-for="c in list" :key="c.id" :value="c.id">
              {{ c.studentName }} · {{ c.title }} · 第 {{ c.currentVersion }} 版
            </option>
          </select></label
        >
      </div>
      <p v-if="detailLoading" class="loading">正在加载关联材料…</p>
      <div v-else-if="selected && revision" class="review-layout">
        <section class="panel form-section review-evidence">
          <h2>{{ selected.title }}</h2>
          <p class="muted">
            {{ selected.studentName }} · {{ selected.projectName }} /
            {{ selected.taskTitle }}
          </p>
          <p class="small muted">正在验收第 {{ revision.version }} 版</p>
          <RevisionView :revision="revision" /><RouterLink
            class="text-button"
            :to="`/contributions/${selected.id}`"
            >查看所有历史版本</RouterLink
          >
        </section>
        <section class="panel form-section review-form">
          <h2>逐项验收</h2>
          <p class="small muted">
            以下是本次申报引用的标准。请结合证据位置与本人工作说明判断。
          </p>
          <fieldset
            v-for="(item, index) in items"
            :key="item.criterionId"
            class="review-criterion"
          >
            <legend>{{ index + 1 }}. {{ item.text }}</legend>
            <div class="radio-options">
              <label
                ><input
                  v-model="item.result"
                  type="radio"
                  :name="`result-${item.criterionId}`"
                  value="true"
                  :disabled="saving"
                />通过</label
              ><label
                ><input
                  v-model="item.result"
                  type="radio"
                  :name="`result-${item.criterionId}`"
                  value="false"
                  :disabled="saving"
                />不通过</label
              >
            </div>
            <label class="small"
              >该项意见<textarea
                v-model="item.comment"
                rows="2"
                maxlength="2000"
                :disabled="saving"
                placeholder="说明达标情况或缺少的证据"
              ></textarea>
            </label>
          </fieldset>
          <label class="review-comment"
            >总体验收意见<textarea
              v-model="comment"
              rows="4"
              maxlength="4000"
              :disabled="saving"
              placeholder="退回补充时必填，指出学生需要补充的工作或材料"
            ></textarea>
          </label>
          <p v-if="reviewError" class="notice error" role="alert">
            {{ reviewError }}
          </p>
          <div class="review-actions">
            <button
              class="button-primary"
              :disabled="saving || !allPassed"
              @click="submit('APPROVED')"
            >
              {{ saving ? "正在提交…" : "通过并确认贡献" }}</button
            ><button
              class="button-secondary"
              :disabled="saving || !hasFailure || !comment.trim()"
              @click="submit('RETURNED')"
            >
              退回补充
            </button>
          </div>
          <p class="small muted">
            通过需各项均通过；退回需至少一项不通过，并填写总体验收意见。
          </p>
        </section>
      </div>
      <p v-if="!selected && reviewError" class="notice error" role="alert">
        {{ reviewError }}
      </p></template
    >
  </div>
</template>
