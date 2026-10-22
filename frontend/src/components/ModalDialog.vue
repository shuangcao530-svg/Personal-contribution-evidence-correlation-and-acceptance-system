<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue";
const props = defineProps({ title: String, busy: Boolean, wide: Boolean });
const emit = defineEmits(["close"]),
  dialog = ref(null);
let previousFocus;
function close() {
  if (!props.busy) emit("close");
}
function onKey(event) {
  if (event.key === "Escape") {
    event.preventDefault();
    close();
  }
  if (event.key !== "Tab") return;
  const nodes = [
    ...dialog.value.querySelectorAll(
      "button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]",
    ),
  ];
  if (!nodes.length) {
    event.preventDefault();
    return;
  }
  const first = nodes[0],
    last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}
onMounted(() => {
  previousFocus = document.activeElement;
  document.body.style.overflow = "hidden";
  dialog.value.querySelector("input, select, textarea, button")?.focus();
  document.addEventListener("keydown", onKey);
});
onBeforeUnmount(() => {
  document.body.style.overflow = "";
  document.removeEventListener("keydown", onKey);
  previousFocus?.focus();
});
</script>
<template>
  <Teleport to="body"
    ><div class="modal-overlay" @mousedown.self="close">
      <section
        ref="dialog"
        class="modal"
        :class="{ 'modal-wide': wide }"
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <div class="modal-heading">
          <h2 id="modal-title">{{ title }}</h2>
          <button
            class="button-quiet"
            aria-label="关闭弹窗"
            :disabled="busy"
            @click="close"
          >
            关闭
          </button>
        </div>
        <slot />
      </section></div
  ></Teleport>
</template>
