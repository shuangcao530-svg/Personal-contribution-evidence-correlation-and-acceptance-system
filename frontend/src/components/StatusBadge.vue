<script setup>
import { statusText } from "../api";
defineProps({ status: String });
</script>
<template>
  <span class="status-badge" :class="status?.toLowerCase()">{{
    statusText[status] || status
  }}</span>
</template>
