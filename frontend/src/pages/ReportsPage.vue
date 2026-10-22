<script setup>
import { ref, onMounted } from "vue";
import { auth, api, date, statusText, saveFile } from "../api";
import StatusBadge from "../components/StatusBadge.vue";
const students = ref([]),
  studentId = ref(""),
  report = ref(null),
  loading = ref(true),
  error = ref("");
async function loadReport() {
  loading.value = true;
  error.value = "";
  report.value = null;
  try {
    if (auth.user.role === "TEACHER" && !studentId.value) return;
    report.value = await api(
      `/reports${studentId.value ? `?studentId=${studentId.value}` : ""}`,
    );
  } catch (err) {
    error.value = err.message;
  } finally {
    loading.value = false;
  }
}
async function load() {
  try {
    if (auth.user.role === "TEACHER") {
      const projects = await api("/projects");
      students.value = [
        ...new Map(
          projects
            .flatMap((p) => p.members)
            .filter((u) => u.role === "STUDENT")
            .map((u) => [u.id, u]),
        ).values(),
      ];
      studentId.value = students.value[0]?.id || "";
    }
    await loadReport();
  } catch (err) {
    error.value = err.message;
    loading.value = false;
  }
}
function csvCell(value) {
  let text = String(value ?? "");
  if (/^[\s\u0000-\u001f]*[=+@-]/u.test(text) || /^[\t\r\n]/u.test(text))
    text = `'${text}`;
  return `"${text.replaceAll('"', '""')}"`;
}
function exportCsv() {
  if (!report.value) return;
  const rows = [
    [
      "贡献人",
      "项目",
      "任务",
      "贡献标题",
      "状态",
      "最新版本",
      "工作说明",
      "标准",
      "证据",
      "证据位置",
      "关联说明",
      "验收人",
      "验收意见",
      "更新时间",
    ],
  ];
  for (const c of report.value.contributions) {
    const r = c.revisions.at(-1);
    for (const e of r?.evidence || [])
      rows.push([
        report.value.student.name,
        c.projectName,
        c.taskTitle,
        c.title,
        statusText[c.status],
        c.currentVersion,
        r.description,
        e.criterionText,
        e.title,
        e.location,
        e.note,
        r.review?.reviewerName || "",
        r.review?.comment || "",
        date(c.updatedAt),
      ]);
  }
  saveFile(
    new Blob(
      ["\ufeff", rows.map((row) => row.map(csvCell).join(",")).join("\r\n")],
      { type: "text/csv;charset=utf-8;" },
    ),
    `${report.value.student.name}-个人贡献报告-${date(new Date())}.csv`,
  );
}
const printReport = () => window.print();
onMounted(load);
</script>
<template>
  <div class="workspace-page">
    <div class="page-heading">
      <div>
        <h1>个人贡献报告</h1>
        <p>汇总已确认工作，保留每一项贡献的依据与验收结果。</p>
      </div>
      <div class="row-actions no-print">
        <button
          class="button-secondary"
          :disabled="!report"
          @click="printReport"
        >
          导出 PDF</button
        ><button class="button-primary" :disabled="!report" @click="exportCsv">
          导出 CSV
        </button>
      </div>
    </div>
    <div v-if="auth.user?.role === 'TEACHER'" class="toolbar no-print">
      <label class="inline-field"
        >查看学生<select
          v-model="studentId"
          :disabled="loading || !students.length"
          @change="loadReport"
        >
          <option v-if="!students.length" value="">暂无项目学生</option>
          <option v-for="s in students" :key="s.id" :value="s.id">
            {{ s.name }} · {{ s.username }}
          </option>
        </select></label
      >
    </div>
    <p v-if="error" class="notice error" role="alert">
      {{ error }} <button class="text-button" @click="load">重试</button>
    </p>
    <p v-if="loading" class="loading">正在生成贡献报告…</p>
    <template v-else-if="report"
      ><section class="report-owner">
        <h2>{{ report.student.name }}的贡献记录</h2>
        <p class="muted">
          账号：{{ report.student.username }} · 生成日期：{{ date(new Date()) }}
        </p>
        <p class="small muted no-print">点击“导出 PDF”，在浏览器打印窗口选择“保存为 PDF”。</p>
      </section>
      <section class="stats-strip report-stats">
        <div>
          <span>全部申报</span><strong>{{ report.summary.total }}</strong>
        </div>
        <div>
          <span>已确认贡献</span><strong>{{ report.summary.approved }}</strong>
        </div>
        <div>
          <span>待验收</span><strong>{{ report.summary.pending }}</strong>
        </div>
        <div>
          <span>待补充</span><strong>{{ report.summary.returned }}</strong>
        </div>
      </section>
      <p class="inline-note">
        仅“已通过”的最新版本计为已确认贡献。材料数量与提交次数不直接代表贡献大小。
      </p>
      <div v-if="!report.contributions.length" class="panel empty-state">
        还没有贡献记录。完成任务并提交证据后，报告会自动更新。
      </div>
      <section
        v-for="item in report.contributions"
        :key="item.id"
        class="panel report-entry"
      >
        <div class="section-title-inline">
          <h2>{{ item.title }}</h2>
          <StatusBadge :status="item.status" />
        </div>
        <p class="small muted">
          {{ item.projectName }} / {{ item.taskTitle }} · 第
          {{ item.currentVersion }} 版 · {{ date(item.updatedAt) }}
        </p>
        <p class="preserve-text">{{ item.revisions.at(-1)?.description }}</p>
        <ul class="report-evidence">
          <li
            v-for="(e, index) in item.revisions.at(-1)?.evidence || []"
            :key="index"
          >
            <strong>{{ e.criterionText }}</strong
            ><span>{{ e.title }} · {{ e.location }}</span
            ><span class="muted">{{ e.note }}</span>
          </li>
        </ul>
        <div v-if="item.revisions.at(-1)?.review" class="report-review">
          <strong
            >{{ item.revisions.at(-1).review.reviewerName }}的验收意见：</strong
          >{{
            item.revisions.at(-1).review.comment || "各项关联标准验收通过。"
          }}
        </div>
        <RouterLink
          class="text-button no-print"
          :to="`/contributions/${item.id}`"
          >查看证据与完整验收记录</RouterLink
        >
      </section></template
    >
    <div v-else-if="!error" class="panel empty-state">
      项目中暂时没有可查看的学生报告。
    </div>
  </div>
</template>
