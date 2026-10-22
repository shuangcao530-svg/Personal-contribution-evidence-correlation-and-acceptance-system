<script setup>
import { ref } from "vue";
import { downloadEvidence } from "../api";
defineProps({ evidence: Object });
const downloading = ref(false),
  error = ref("");
async function download(e) {
  downloading.value = true;
  error.value = "";
  try {
    await downloadEvidence(e);
  } catch (err) {
    error.value = err.message;
  } finally {
    downloading.value = false;
  }
}
</script>
<template>
  <div class="evidence-item">
    <div class="evidence-item-head">
      <strong>{{ evidence.title }}</strong
      ><a
        v-if="evidence.type === 'LINK'"
        :href="evidence.url"
        target="_blank"
        rel="noopener noreferrer"
        class="text-button"
        >打开链接 ↗</a
      ><button
        v-else
        class="text-button"
        :disabled="downloading"
        @click="download(evidence)"
      >
        {{ downloading ? "下载中…" : "下载附件" }}
      </button>
    </div>
    <p class="small muted">
      {{ evidence.type === "LINK" ? "链接材料" : evidence.fileName }} ·
      上传人：{{ evidence.uploaderName }}
    </p>
    <p><span class="muted">对应标准：</span>{{ evidence.criterionText }}</p>
    <p>
      <span class="muted">证据位置：</span>{{ evidence.location || "未填写" }}
    </p>
    <p class="preserve-text">
      <span class="muted">关联说明：</span>{{ evidence.note }}
    </p>
    <details v-if="evidence.sha256" class="small muted">
      <summary>文件完整性标识</summary>
      <p class="hash">SHA-256：{{ evidence.sha256 }}</p>
      <p>标识文件内容，不作为作者身份的证明。</p>
    </details>
    <p v-if="error" class="error-text" role="alert">{{ error }}</p>
  </div>
</template>
