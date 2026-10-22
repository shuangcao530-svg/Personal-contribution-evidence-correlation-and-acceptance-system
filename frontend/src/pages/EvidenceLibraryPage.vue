<script setup>
import {ref,computed,onMounted,onBeforeUnmount} from "vue";
import {api,downloadEvidence,date} from "../api";
const projects=ref([]),projectId=ref(""),items=ref([]),category=ref("ALL"),query=ref(""),loading=ref(true),error=ref(""),preview=ref(null),previewUrl=ref(""),previewError=ref("");
const categoryLabels={LINK:"外部链接",IMAGE:"图片",DOCUMENT:"文档",ARCHIVE:"压缩包",OTHER:"其他"};
const categories=computed(()=>[{id:"ALL",label:"全部"},...Object.entries(categoryLabels).map(([id,label])=>({id,label}))]);
const filtered=computed(()=>items.value.filter(e=>(category.value==="ALL"||e.category===category.value)&&(!query.value||[e.title,e.fileName,e.uploaderName,e.category].join(" ").toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase()))));
async function load(){loading.value=true;error.value="";items.value=[];if(!projectId.value){loading.value=false;return;}try{items.value=await api("/evidence?projectId="+projectId.value);}catch(err){error.value=err.message;}finally{loading.value=false;}}
async function loadProjects(){try{projects.value=await api("/projects");projectId.value=String(projects.value[0]?.id||"");await load();}catch(err){error.value=err.message;loading.value=false;}}
function canPreview(e){if(e.type==="LINK")return false;return ["pdf","png","jpg","jpeg","gif","webp","txt","md","csv","json","log"].includes((e.fileName||"").split(".").pop().toLowerCase());}
async function openPreview(e){
 closePreview();previewError.value="";
 try{const blob=await api("/evidence/"+e.id+"/preview",{download:true});previewUrl.value=URL.createObjectURL(blob);preview.value=e;}
 catch(err){previewError.value=err.message;}
}
function closePreview(){if(previewUrl.value)URL.revokeObjectURL(previewUrl.value);previewUrl.value="";preview.value=null;}
onBeforeUnmount(closePreview);
onMounted(loadProjects);
</script>
<template>
 <div class="workspace-page">
  <div class="page-heading"><div><h1>证据资料库</h1><p>按材料类型浏览项目证据，图片、PDF 与文本可直接预览。</p></div><label class="inline-field">所属项目<select v-model="projectId" @change="load"><option v-for="p in projects" :key="p.id" :value="p.id">{{p.name}}</option></select></label></div>
  <p v-if="error" class="notice error" role="alert">{{error}} <button class="text-button" @click="load">重试</button></p>
  <div v-if="projects.length" class="evidence-toolbar"><label class="evidence-search"><span class="sr-only">搜索证据</span><input v-model="query" type="search" placeholder="搜索证据名称、文件名或上传人"></label><div class="segmented-control"><button v-for="item in categories" :key="item.id" :class="{active:category===item.id}" @click="category=item.id">{{item.label}}</button></div></div>
  <p v-if="loading" class="loading">正在加载证据…</p>
  <div v-else-if="!projects.length" class="panel empty-state">你还没有加入项目。</div>
  <div v-else-if="!filtered.length" class="panel empty-state">没有符合条件的证据材料。</div>
  <div v-else class="evidence-library-grid"><article v-for="e in filtered" :key="e.id" class="panel evidence-library-card"><div class="evidence-card-icon" :class="e.category">{{e.category==="IMAGE"?"图":e.category==="LINK"?"链":e.category==="ARCHIVE"?"包":"文"}}</div><div class="evidence-card-copy"><span class="evidence-category">{{categoryLabels[e.category]||"其他"}}</span><h2>{{e.title}}</h2><p class="small muted">{{e.fileName||e.url}}</p><p class="small muted">{{e.uploaderName}} · {{date(e.createdAt)}}</p></div><div class="row-actions"><a v-if="e.type==='LINK'" class="button-secondary" :href="e.url" target="_blank" rel="noopener noreferrer">打开链接 ↗</a><template v-else><button v-if="canPreview(e)" class="button-secondary" @click="openPreview(e)">预览</button><button class="text-button" @click="downloadEvidence(e)">下载</button></template></div></article></div>
  <p v-if="previewError" class="notice error" role="alert">{{previewError}}</p>
  <div v-if="preview" class="evidence-preview-backdrop" role="presentation" @click.self="closePreview"><section class="evidence-preview-dialog" role="dialog" aria-modal="true" :aria-label="preview.title"><header><strong>{{preview.title}}</strong><button class="button-secondary" @click="closePreview">关闭</button></header><img v-if="preview.category==='IMAGE'" :src="previewUrl" :alt="preview.title"><iframe v-else :src="previewUrl" :title="preview.title"></iframe></section></div>
 </div>
</template>
