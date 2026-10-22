<script setup>
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { auth, api } from "../api";
const route=useRoute(), data=ref(null), loading=ref(true), error=ref("");
const stateLabels={NOT_ASSIGNED:"未分配",NO_SUBMISSION:"未申报",SUBMITTED:"待验收",APPROVED:"已通过",RETURNED:"待补充"};
const stateHints={NOT_ASSIGNED:"此任务分配给其他成员",NO_SUBMISSION:"负责人尚未提交贡献",SUBMITTED:"已提交，等待教师验收",APPROVED:"贡献已验收通过",RETURNED:"需要按意见补充"};
const stateClass={NOT_ASSIGNED:"neutral",NO_SUBMISSION:"muted",SUBMITTED:"pending",APPROVED:"approved",RETURNED:"returned"};
const isTeacher=computed(()=>auth.user?.role==="TEACHER");
const lookup=computed(()=>{const map=new Map();for(const cell of data.value?.cells||[])map.set(cell.memberId+":"+cell.taskId,cell);return map;});
function cell(member,task){return lookup.value.get(member.id+":"+task.id)}
async function load(){loading.value=true;error.value="";try{data.value=await api(`/projects/${route.params.id}/matrix`)}catch(e){error.value=e.message}finally{loading.value=false}}
onMounted(load);
</script>
<template>
  <div class="workspace-page matrix-page">
    <div class="page-heading">
      <div><h1>项目贡献矩阵</h1><p>按成员与任务查看申报和验收进度，直接进入对应的贡献记录。</p></div>
      <RouterLink class="button-secondary" to="/">返回项目总览</RouterLink>
    </div>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <p v-if="loading" class="loading">正在汇总项目进度…</p>
    <template v-if="data">
      <section class="matrix-project panel">
        <div><span class="small muted">项目</span><h2>{{ data.project.name }}</h2><p>{{ data.project.description }}</p></div>
        <div class="matrix-project-meta"><span>指导教师：{{ data.project.ownerName }}</span><span>{{ data.members.length }} 位成员</span><span>{{ data.tasks.length }} 项任务</span></div>
      </section>
      <section class="matrix-summary" aria-label="矩阵状态统计">
        <div><strong>{{ data.summary.notSubmitted }}</strong><span>待提交任务</span></div>
        <div><strong>{{ data.summary.submitted }}</strong><span>待验收申报</span></div>
        <div><strong>{{ data.summary.approved }}</strong><span>已通过</span></div>
        <div><strong>{{ data.summary.returned }}</strong><span>待补充</span></div>
      </section>
      <section class="panel matrix-panel">
        <div class="matrix-heading"><div><h2>成员 × 任务</h2><p>{{ isTeacher ? "查看本项目全部学生的当前进度。" : "此页仅展示你本人负责的任务。" }}</p></div>
          <div class="matrix-legend"><span><i class="approved"></i>已通过</span><span><i class="pending"></i>待验收</span><span><i class="returned"></i>待补充</span><span><i class="muted"></i>未申报</span></div>
        </div>
        <div v-if="!data.tasks.length || !data.members.length" class="empty-state">项目还没有可展示的学生任务。</div>
        <div v-else class="table-scroll matrix-scroll">
          <table class="data-table contribution-matrix">
            <thead><tr><th class="matrix-member-head">项目成员</th><th v-for="task in data.tasks" :key="task.id"><span>{{ task.title }}</span><small>负责人：{{ task.assigneeName }} · 截止 {{ task.dueDate }}</small></th></tr></thead>
            <tbody><tr v-for="member in data.members" :key="member.id"><th class="matrix-member"><strong>{{ member.name }}</strong><small>学生成员</small></th>
              <td v-for="task in data.tasks" :key="task.id"><template v-if="cell(member,task)"><RouterLink v-if="cell(member,task).contribution" class="matrix-cell" :to="`/contributions/${cell(member,task).contribution.id}`" :class="stateClass[cell(member,task).state]"><strong>{{ stateLabels[cell(member,task).state] }}</strong><small>{{ cell(member,task).contribution.title }}</small><small>第 {{ cell(member,task).contribution.currentVersion }} 版 · 查看详情 →</small></RouterLink><div v-else class="matrix-cell" :class="stateClass[cell(member,task).state]"><strong>{{ stateLabels[cell(member,task).state] }}</strong><small>{{ stateHints[cell(member,task).state] }}</small></div></template></td>
            </tr></tbody>
          </table>
        </div>
      </section>
      <p class="matrix-footnote">矩阵只展示任务分配和验收状态，不按提交次数或材料数量给成员排名。</p>
    </template>
  </div>
</template>
