<script setup>
import { ref, onMounted } from "vue";
import { api, timestamp } from "../api";
const projects=ref([]), items=ref([]), projectId=ref(""), loading=ref(true), error=ref("");
const labels={PROJECT_CREATED:"创建了项目",TASK_CREATED:"创建了任务",EVIDENCE_ADDED:"添加了证据",CONTRIBUTION_SUBMITTED:"提交了贡献",CONTRIBUTION_RESUBMITTED:"补交了贡献",CONTRIBUTION_REVIEWED:"完成了验收"};
async function load(){
 loading.value=true;error.value="";
 try{projects.value=await api("/projects");const suffix=projectId.value?"?projectId="+projectId.value:"";items.value=await api("/activity"+suffix);}
 catch(err){error.value=err.message;}finally{loading.value=false;}
}
function href(item){
 if(item.entityType==="CONTRIBUTION")return "/contributions/"+item.entityId;
 if(item.entityType==="EVIDENCE")return "/evidence";
 if(item.entityType==="TASK")return "/tasks?projectId="+item.projectId;
 if(item.entityType==="PROJECT")return "/projects/"+item.projectId+"/matrix";
 return "/";
}
onMounted(load);
</script>
<template>
 <div class="workspace-page">
  <div class="page-heading"><div><h1>项目动态</h1><p>查看项目内的任务、证据、贡献提交和验收记录。</p></div><label class="inline-field">筛选项目<select v-model="projectId" @change="load"><option value="">全部项目</option><option v-for="p in projects" :key="p.id" :value="p.id">{{p.name}}</option></select></label></div>
  <p v-if="error" class="notice error" role="alert">{{error}} <button class="text-button" @click="load">重试</button></p>
  <p v-if="loading" class="loading">正在加载项目动态…</p>
  <div v-else-if="!items.length" class="panel empty-state">还没有操作记录。后续的任务、证据和验收活动会显示在这里。</div>
  <section v-else class="panel activity-panel"><ol class="activity-list"><li v-for="item in items" :key="item.id"><span class="activity-dot"></span><div class="activity-content"><div class="activity-title"><strong>{{item.actorName}}</strong><span>{{labels[item.action]||item.action}}</span><RouterLink :to="href(item)">{{item.summary}}</RouterLink></div><p class="small muted">{{item.projectName}} · {{timestamp(item.createdAt)}}</p></div></li></ol></section>
 </div>
</template>
