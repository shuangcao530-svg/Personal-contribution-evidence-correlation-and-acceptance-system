<script setup>
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth, api, post } from "../api";
const route = useRoute(),
  router = useRouter(),
  revising = computed(() => !!route.params.id);
const tasks = ref([]),
  taskId = ref(""),
  title = ref(""),
  description = ref(""),
  refs = ref([]),
  pool = ref([]);
const loading = ref(true),
  saving = ref(false),
  error = ref(""),
  original = ref(null),
  returnComment = ref("");
const mode = ref("LINK"),
  materialTitle = ref(""),
  materialUrl = ref(""),
  materialFile = ref(null),
  existingId = ref(""),
  materialBusy = ref(false),
  materialError = ref(""),
  poolBusy = ref(false),
  fileInput = ref(null);
const currentTask = computed(() =>
  tasks.value.find((t) => String(t.id) === String(taskId.value)),
);
const criteria = computed(() => currentTask.value?.criteria || []);
const returnFeedback = computed(() => {
  const revision=original.value?.revisions?.at(-1);
  return (revision?.review?.items||[]).filter(item=>!item.passed).map(item=>({
    ...item,
    criterionText:revision.evidence.find(proof=>proof.criterionId===item.criterionId)?.criterionText||"对应验收标准",
  }));
});
async function loadPool() {
  pool.value = [];
  existingId.value = "";
  if (!currentTask.value) return;
  poolBusy.value = true;
  try {
    pool.value = await api(
      `/evidence?projectId=${currentTask.value.projectId}`,
    );
  } catch (err) {
    materialError.value = err.message;
  } finally {
    poolBusy.value = false;
  }
}
async function taskChanged() {
  refs.value = [];
  materialError.value = "";
  await loadPool();
}
function addReference(evidence) {
  refs.value.push({
    key: `${evidence.id}-${Date.now()}-${Math.random()}`,
    evidence,
    evidenceId: evidence.id,
    criterionId: criteria.value[0]?.id || "",
    location: "",
    note: "",
  });
}
function addExisting() {
  materialError.value = "";
  const found = pool.value.find(
    (e) => String(e.id) === String(existingId.value),
  );
  if (!found) {
    materialError.value = "请选择一份已有材料。";
    return;
  }
  addReference(found);
  existingId.value = "";
}
async function addMaterial() {
  if (materialBusy.value || !currentTask.value) return;
  materialError.value = "";
  if (!materialTitle.value.trim()) {
    materialError.value = "请填写证据名称。";
    return;
  }
  if (mode.value === "LINK") {
    try {
      const parsed = new URL(materialUrl.value);
      if (!["http:", "https:"].includes(parsed.protocol)) throw new Error();
    } catch {
      materialError.value = "请输入完整的 http 或 https 链接。";
      return;
    }
  } else {
    if (!materialFile.value) {
      materialError.value = "请选择附件。";
      return;
    }
    if (materialFile.value.size > 10 * 1024 * 1024) {
      materialError.value = "单个附件不能超过 10 MB。";
      return;
    }
  }
  materialBusy.value = true;
  try {
    let evidence;
    if (mode.value === "LINK")
      evidence = await post("/evidence/link", {
        projectId: currentTask.value.projectId,
        title: materialTitle.value.trim(),
        url: materialUrl.value.trim(),
      });
    else {
      const body = new FormData();
      body.append("projectId", currentTask.value.projectId);
      body.append("title", materialTitle.value.trim());
      body.append("file", materialFile.value);
      evidence = await api("/evidence/upload", { method: "POST", body });
    }
    pool.value.unshift(evidence);
    addReference(evidence);
    materialTitle.value = "";
    materialUrl.value = "";
    materialFile.value = null;
    if (fileInput.value) fileInput.value.value = "";
  } catch (err) {
    materialError.value = err.message;
  } finally {
    materialBusy.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = "";
  try {
    tasks.value = (await api("/tasks")).filter(
      (t) => t.assigneeId === auth.user.id,
    );
    if (revising.value) {
      original.value = await api(`/contributions/${route.params.id}`);
      if (original.value.status !== "RETURNED")
        throw new Error(
          "只有待补充的申报可以重新提交。请返回贡献详情查看当前状态。",
        );
      const latest = original.value.revisions.at(-1);
      taskId.value = original.value.taskId;
      title.value = latest.title;
      description.value = latest.description;
      returnComment.value = latest.review?.comment || "";
      await loadPool();
      refs.value = latest.evidence.map((e, index) => ({
        key: `old-${index}`,
        evidence: e,
        evidenceId: e.id,
        criterionId: e.criterionId,
        location: e.location,
        note: e.note,
      }));
    } else {
      const selected = tasks.value.find(
        (t) => String(t.id) === String(route.query.taskId),
      );
      taskId.value = selected?.id || tasks.value[0]?.id || "";
      await loadPool();
    }
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
async function submit() {
  if (saving.value || materialBusy.value) return;
  error.value = "";
  if (!currentTask.value) {
    error.value = "请选择分配给你的任务。";
    return;
  }
  if (!refs.value.length) {
    error.value = "请至少添加一条证据关联。";
    return;
  }
  saving.value = true;
  try {
    const body = {
      taskId: Number(taskId.value),
      title: title.value.trim(),
      description: description.value.trim(),
      evidence: refs.value.map(
        ({ evidenceId, criterionId, location, note }) => ({
          evidenceId,
          criterionId: Number(criterionId),
          location: location.trim(),
          note: note.trim(),
        }),
      ),
    };
    const result = await post(
      revising.value
        ? `/contributions/${route.params.id}/revisions`
        : "/contributions",
      body,
    );
    await router.push(`/contributions/${result.id}`);
  } catch (err) {
    error.value = err.message;
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div class="workspace-page narrow-page">
    <div class="page-heading">
      <div>
        <h1>{{ revising ? "补充贡献证据" : "申报个人贡献" }}</h1>
        <p>
          {{
            revising
              ? "根据验收意见补充材料，提交后将生成新版本。"
              : "说明本人完成的工作，关联能够支持这份声明的材料。"
          }}
        </p>
      </div>
      <RouterLink
        class="text-button"
        :to="revising ? `/contributions/${route.params.id}` : '/contributions'"
        >返回列表</RouterLink
      >
    </div>
    <p v-if="loading" class="loading">正在加载任务与证据…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <div v-if="!loading && !tasks.length" class="panel empty-state">
      目前没有分配给你的任务，请联系项目老师。
    </div>
    <form
      v-if="
        !loading &&
        tasks.length &&
        (!revising || original?.status === 'RETURNED')
      "
      class="contribution-form"
      @submit.prevent="submit"
    >
      <section v-if="returnComment" class="notice warning resubmission-feedback">
        <strong>教师补充意见</strong><p>{{ returnComment }}</p>
        <ul v-if="returnFeedback.length"><li v-for="item in returnFeedback" :key="item.criterionId"><strong>{{item.criterionText}}</strong><span>{{item.comment||"请对照验收标准补充或完善关联证据。"}}</span></li></ul>
        <small>修改后提交会生成新版本，之前的申报与验收记录会保留。</small>
      </section>
      <section class="panel form-section">
        <h2>本人完成的工作</h2>
        <div class="form-stack">
          <label
            >所属任务<select
              v-model="taskId"
              required
              :disabled="revising || materialBusy || saving"
              @change="taskChanged"
            >
              <option v-for="task in tasks" :key="task.id" :value="task.id">
                {{ task.projectName }} / {{ task.title }}
              </option>
            </select></label
          ><label
            >贡献标题<input
              v-model="title"
              required
              maxlength="150"
              placeholder="例如：完成登录接口与错误密码处理" /></label
          ><label
            >工作说明<textarea
              v-model="description"
              required
              maxlength="6000"
              rows="5"
              placeholder="具体描述你完成的内容、与他人的分工，以及交付结果。"
            ></textarea>
          </label>
        </div>
      </section>
      <section class="panel form-section">
        <h2>添加贡献证据</h2>
        <p class="muted small">
          上传者与贡献者可以不同；引用团队材料时，请说明其中由你完成的部分。
        </p>
        <div class="segmented-control" aria-label="证据来源">
          <button
            type="button"
            :class="{ active: mode === 'LINK' }"
            :disabled="materialBusy"
            @click="
              mode = 'LINK';
              materialError = '';
            "
          >
            添加链接</button
          ><button
            type="button"
            :class="{ active: mode === 'FILE' }"
            :disabled="materialBusy"
            @click="
              mode = 'FILE';
              materialError = '';
            "
          >
            上传附件</button
          ><button
            type="button"
            :class="{ active: mode === 'EXISTING' }"
            :disabled="materialBusy"
            @click="
              mode = 'EXISTING';
              materialError = '';
            "
          >
            引用已有材料
          </button>
        </div>
        <div v-if="mode === 'EXISTING'" class="form-stack">
          <label
            >同项目材料<select v-model="existingId" :disabled="poolBusy">
              <option value="">
                {{ poolBusy ? "正在加载…" : "请选择材料" }}
              </option>
              <option
                v-for="evidence in pool"
                :key="evidence.id"
                :value="evidence.id"
              >
                {{ evidence.title }} · {{ evidence.uploaderName }}
              </option>
            </select></label
          >
          <p v-if="!poolBusy && !pool.length" class="small muted">
            这个项目还没有材料，可以先添加链接或上传附件。
          </p>
          <div>
            <button
              type="button"
              class="button-secondary"
              :disabled="!existingId || saving"
              @click="addExisting"
            >
              添加证据关联
            </button>
          </div>
        </div>
        <div v-else class="form-stack">
          <label
            >证据名称<input
              v-model="materialTitle"
              maxlength="150"
              placeholder="例如：登录接口测试报告" /></label
          ><label v-if="mode === 'LINK'"
            >材料链接<input
              v-model="materialUrl"
              type="url"
              placeholder="https://…" /></label
          ><label v-else
            >附件<input
              ref="fileInput"
              type="file"
              accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.json,.png,.jpg,.jpeg,.gif,.webp,.zip,.7z"
              @change="materialFile = $event.target.files[0] || null"
            /><span class="field-help"
              >文档、图片、文本或压缩包，单个文件不超过 10 MB。</span
            ></label
          >
          <div>
            <button
              type="button"
              class="button-secondary"
              :disabled="materialBusy || saving || !currentTask"
              @click="addMaterial"
            >
              {{
                materialBusy
                  ? "正在保存材料…"
                  : mode === "LINK"
                    ? "保存链接并关联"
                    : "上传并关联"
              }}
            </button>
          </div>
        </div>
        <p v-if="materialError" class="notice error" role="alert">
          {{ materialError }}
        </p>
      </section>
      <section class="panel form-section">
        <h2>
          证据与标准的关联 <span class="count">{{ refs.length }}</span>
        </h2>
        <p class="small muted">
          每条关联选择一项标准，并填写具体位置与证明内容。同一材料可以关联多项标准。
        </p>
        <p v-if="!refs.length" class="empty-state compact">
          先在上方添加一份材料。
        </p>
        <article
          v-for="(reference, index) in refs"
          :key="reference.key"
          class="reference-editor"
        >
          <div class="section-title-inline">
            <h3>{{ index + 1 }}. {{ reference.evidence.title }}</h3>
            <button
              type="button"
              class="text-button danger-button"
              :disabled="saving"
              @click="refs.splice(index, 1)"
            >
              移除关联
            </button>
          </div>
          <p class="small muted">
            {{ reference.evidence.type === "FILE" ? "附件" : "链接" }} ·
            上传人：{{ reference.evidence.uploaderName }}
          </p>
          <div class="form-stack">
            <label
              >对应验收标准<select v-model="reference.criterionId" required>
                <option
                  v-for="criterion in criteria"
                  :key="criterion.id"
                  :value="criterion.id"
                >
                  {{ criterion.text }}
                </option>
              </select></label
            ><label
              >证据位置<input
                v-model="reference.location"
                required
                maxlength="500"
                placeholder="例如：报告第 3 页，测试用例 05；或代码文件与行号" /></label
            ><label
              >关联说明<textarea
                v-model="reference.note"
                required
                maxlength="2000"
                rows="3"
                placeholder="这份材料的哪个部分由你完成？它如何证明这项标准已满足？"
              ></textarea>
            </label>
          </div>
          <button
            type="button"
            class="text-button duplicate-reference"
            :disabled="saving"
            @click="addReference(reference.evidence)"
          >
            用这份材料关联另一项标准
          </button>
        </article>
      </section>
      <p class="inline-note">
        提交后进入教师验收。保存的材料会保留在项目中；退回补充时可生成新版本，历史记录保持可查。
      </p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <div class="form-actions">
        <RouterLink class="button-secondary" to="/contributions"
          >返回列表</RouterLink
        ><button class="button-primary" :disabled="saving || materialBusy">
          {{ saving ? "正在提交…" : revising ? "提交新版本" : "提交验收" }}
        </button>
      </div>
    </form>
  </div>
</template>
