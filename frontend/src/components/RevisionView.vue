<script setup>
import { timestamp } from "../api";
import EvidenceItem from "./EvidenceItem.vue";
import StatusBadge from "./StatusBadge.vue";
defineProps({ revision: Object });
</script>
<template>
  <div class="revision-content">
    <p class="muted small">提交时间：{{ timestamp(revision.createdAt) }}</p>
    <h3>{{ revision.title }}</h3>
    <p class="preserve-text">{{ revision.description }}</p>
    <h4>贡献证据（{{ revision.evidence.length }} 条关联）</h4>
    <EvidenceItem
      v-for="(e, index) in revision.evidence"
      :key="`${e.id}-${e.criterionId}-${index}`"
      :evidence="e"
    />
    <div v-if="revision.review" class="review-record">
      <div class="section-title-inline">
        <h4>本次验收记录</h4>
        <StatusBadge :status="revision.review.decision" />
      </div>
      <p class="small muted">
        {{ revision.review.reviewerName }} ·
        {{ timestamp(revision.review.createdAt) }}
      </p>
      <p class="preserve-text">{{ revision.review.comment || "验收通过" }}</p>
      <ul class="review-items">
        <li v-for="item in revision.review.items" :key="item.criterionId">
          <strong :class="item.passed ? 'text-success' : 'text-danger'">{{
            item.passed ? "通过" : "不通过"
          }}</strong
          ><span>{{
            revision.evidence.find((e) => e.criterionId === item.criterionId)
              ?.criterionText || `标准 ${item.criterionId}`
          }}</span>
          <p v-if="item.comment" class="muted">{{ item.comment }}</p>
        </li>
      </ul>
    </div>
    <p v-else class="inline-note">这一版尚未验收，暂不计入已确认贡献。</p>
  </div>
</template>
