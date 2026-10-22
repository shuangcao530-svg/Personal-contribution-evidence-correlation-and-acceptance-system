import { reactive } from "vue";

export const auth = reactive({
  token: sessionStorage.getItem("evidence-token") || "",
  user: null,
});
export function clearSession() {
  auth.token = "";
  auth.user = null;
  sessionStorage.removeItem("evidence-token");
}
export async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (auth.token) headers.set("Authorization", `Bearer ${auth.token}`);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  let response;
  try {
    response = await fetch(`/api${path}`, { ...options, headers });
  } catch {
    throw new Error("无法连接服务，请确认后端已经启动后重试。");
  }
  if (response.status === 401 && path !== "/auth/login") {
    clearSession();
    window.location.hash = "/login";
  }
  if (!response.ok) {
    const detail = await response.json().catch(() => ({}));
    throw new Error(
      detail.message || `请求失败（${response.status}），请稍后重试。`,
    );
  }
  if (options.download) return response.blob();
  if (response.status === 204) return null;
  return response.json();
}
export const post = (path, body) =>
  api(path, { method: "POST", body: JSON.stringify(body) });
export async function login(username, password) {
  const data = await post("/auth/login", { username, password });
  auth.token = data.token;
  auth.user = data.user;
  sessionStorage.setItem("evidence-token", data.token);
}
export function date(value, short = false) {
  if (!value) return "未设置";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  const mm = String(d.getMonth() + 1).padStart(2, "0"),
    dd = String(d.getDate()).padStart(2, "0");
  return short ? `${mm}-${dd}` : `${d.getFullYear()}-${mm}-${dd}`;
}
export function timestamp(value) {
  if (!value) return "—";
  const d = new Date(value);
  return `${date(value)} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}
export function saveFile(blob, name) {
  const url = URL.createObjectURL(blob),
    a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function downloadEvidence(evidence) {
  const blob = await api(`/evidence/${evidence.id}/download`, {
    download: true,
  });
  saveFile(blob, evidence.fileName || evidence.title);
}
export const statusText = {
  SUBMITTED: "待验收",
  RETURNED: "待补充",
  APPROVED: "已通过",
};
