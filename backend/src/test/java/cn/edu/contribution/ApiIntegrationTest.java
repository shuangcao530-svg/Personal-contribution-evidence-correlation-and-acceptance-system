package cn.edu.contribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String RUN_ID = UUID.randomUUID().toString();
    private static final Path STORE = Path.of("target", "test-files", RUN_ID).toAbsolutePath();
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + STORE.resolve("database").toString().replace('\\','/') + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("app.upload-dir", () -> STORE.resolve("uploads").toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    String teacher, student, student2, student3;
    @BeforeEach void loginUsers() throws Exception {
        teacher = login("teacher"); student = login("student"); student2 = login("student2"); student3 = login("student3");
    }
    private String login(String username) throws Exception { return request("POST", "/api/auth/login", null, Map.of("username",username,"password","demo1234"),200).path("token").asText(); }
    private JsonNode request(String method, String path, String token, Object body, int status) throws Exception {
        var builder = switch (method) { case "POST" -> post(path); default -> get(path); };
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType("application/json").content(json.writeValueAsBytes(body));
        MvcResult result = mvc.perform(builder).andExpect(status().is(status)).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private JsonNode project(String name, List<Long> members) throws Exception { return request("POST","/api/projects",teacher,Map.of("name",name,"description","集成测试项目","memberIds",members),200); }
    private JsonNode task(long projectId, long assigneeId) throws Exception { return request("POST","/api/tasks",teacher,Map.of("projectId",projectId,"title","接口任务","description","集成测试任务","assigneeId",assigneeId,"dueDate","2026-10-15","criteria",List.of("正常场景符合预期","异常场景有明确反馈")),200); }
    private JsonNode link(long projectId, String token) throws Exception { return request("POST","/api/evidence/link",token,Map.of("projectId",projectId,"title","测试证据","url","https://example.org/evidence"),200); }
    private Map<String,Object> reference(long evidenceId, long criterionId) { return Map.of("evidenceId",evidenceId,"criterionId",criterionId,"location","第 1 页","note","测试材料证明此标准"); }
    private Map<String,Object> submission(long taskId, List<Map<String,Object>> refs) { return Map.of("taskId",taskId,"title","个人接口实现","description","我编写了接口校验与异常反馈","evidence",refs); }
    private Map<String,Object> review(long revisionId,String decision,List<Map<String,Object>> items) { return Map.of("revisionId",revisionId,"decision",decision,"comment",decision.equals("RETURNED") ? "请补充异常场景执行记录" : "材料与贡献说明一致","items",items); }
    private Map<String,Object> item(long criterionId,boolean passed) { return Map.of("criterionId",criterionId,"passed",passed,"comment",passed ? "材料可确认" : "缺少执行结果"); }
    private long id(JsonNode node) { return node.path("id").asLong(); }
    private long criterion(JsonNode task,int index) { return task.path("criteria").get(index).path("id").asLong(); }
    private long revision(JsonNode contribution) { return contribution.path("revisions").get(contribution.path("currentVersion").asInt()-1).path("id").asLong(); }

    @Test void authenticationAndRoleBoundaries() throws Exception {
        request("GET","/api/health",null,null,200);
        request("GET","/api/projects",null,null,401);
        request("POST","/api/auth/login",null,Map.of("username","teacher","password","incorrect"),401);
        request("GET","/api/users",student,null,403);
        request("POST","/api/projects",student,Map.of("name","越权项目","memberIds",List.of(2)),403);
        assertThat(request("GET","/api/auth/me",teacher,null,200).path("role").asText()).isEqualTo("TEACHER");
        JsonNode users = request("GET","/api/users",teacher,null,200);
        assertThat(users.toString()).doesNotContain("password", "hash");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username='teacher'",String.class)).startsWith("210000:").doesNotContain("demo1234");
        request("POST","/api/auth/logout",student,null,200);
        request("GET","/api/auth/me",student,null,401);
    }

    @Test void crossProjectEvidenceAndWrongTaskCriteriaAreRejectedAtomically() throws Exception {
        long p1 = id(project("关联边界项目一",List.of(2L,3L))), p2 = id(project("关联边界项目二",List.of(2L)));
        JsonNode t1 = task(p1,2), t2 = task(p2,2), sameProjectTask = task(p1,2);
        long e1=id(link(p1,student)),e2=id(link(p2,student));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM contributions",Long.class);
        request("POST","/api/contributions",student,submission(id(t1),List.of(reference(e2,criterion(t1,0)))),400);
        request("POST","/api/contributions",student,submission(id(t1),List.of(reference(e1,criterion(t2,0)))),400);
        request("POST","/api/contributions",student,submission(id(t1),List.of(reference(e1,criterion(sameProjectTask,0)))),400);
        request("POST","/api/contributions",student2,submission(id(t1),List.of(reference(e1,criterion(t1,0)))),403);
        request("POST","/api/contributions",student,submission(id(t1),List.of()),400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contributions",Long.class)).isEqualTo(before);
        // Other project members may contribute evidence; the assignee still owns the declaration.
        long collaboratorEvidence = id(link(p1,student2));
        JsonNode accepted = request("POST","/api/contributions",student,submission(id(t1),List.of(reference(collaboratorEvidence,criterion(t1,0)))),200);
        assertThat(accepted.path("studentId").asLong()).isEqualTo(2);
        assertThat(accepted.path("revisions").get(0).path("evidence").get(0).path("uploaderId").asLong()).isEqualTo(3);
    }

    @Test void returnResubmitApprovePreservesImmutableHistoryAndReport() throws Exception {
        long projectId=id(project("版本验收项目",List.of(2L,3L)));
        JsonNode task=task(projectId,2); long proof=id(link(projectId,student));
        long c1=criterion(task,0),c2=criterion(task,1);
        JsonNode created=request("POST","/api/contributions",student,submission(id(task),List.of(reference(proof,c1),reference(proof,c2))),200);
        long contributionId=id(created),revision1=revision(created);
        request("GET","/api/contributions/"+contributionId,student2,null,403);
        request("GET","/api/reports?studentId=2",student2,null,403);
        request("POST","/api/contributions/"+contributionId+"/reviews",student,review(revision1,"APPROVED",List.of(item(c1,true),item(c2,true))),403);
        request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision1,"APPROVED",List.of(item(c1,true))),400);
        request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision1,"APPROVED",List.of(item(c1,true),item(c1,true))),400);
        request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision1,"RETURNED",List.of(item(c1,true),item(c2,true))),400);
        JsonNode returned=request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision1,"RETURNED",List.of(item(c1,true),item(c2,false))),200);
        assertThat(returned.path("status").asText()).isEqualTo("RETURNED");
        Map<String,Object> revisionInput=Map.of("title","补充异常测试结果","description","补充异常场景的执行记录，并保留原材料定位。","evidence",List.of(reference(proof,c1),reference(proof,c2)));
        request("POST","/api/contributions/"+contributionId+"/revisions",student2,revisionInput,403);
        JsonNode revised=request("POST","/api/contributions/"+contributionId+"/revisions",student,revisionInput,200);
        assertThat(revised.path("currentVersion").asInt()).isEqualTo(2);
        assertThat(revised.path("revisions").size()).isEqualTo(2);
        assertThat(revised.path("revisions").get(0)).isEqualTo(returned.path("revisions").get(0));
        request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision1,"APPROVED",List.of(item(c1,true),item(c2,true))),409);
        long revision2=revision(revised);
        JsonNode approved=request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision2,"APPROVED",List.of(item(c1,true),item(c2,true))),200);
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        request("POST","/api/contributions/"+contributionId+"/reviews",teacher,review(revision2,"APPROVED",List.of(item(c1,true),item(c2,true))),409);
        request("POST","/api/contributions/"+contributionId+"/revisions",student,revisionInput,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM revisions WHERE contribution_id=?",Long.class,contributionId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE revision_id IN (?,?)",Long.class,revision1,revision2)).isEqualTo(2);
        assertThat(request("GET","/api/contributions/"+contributionId,teacher,null,200)).isEqualTo(approved);
        JsonNode report=request("GET","/api/reports?studentId=2",teacher,null,200);
        assertThat(report.path("summary").path("approved").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(Files.exists(STORE.resolve("database.mv.db"))).isTrue();
    }

    @Test void evidenceDownloadsRequireProjectMembershipAndPreserveBytes() throws Exception {
        long projectId=id(project("附件权限项目",List.of(2L,3L)));
        byte[] content="个人测试报告：真实存储的附件内容".getBytes(StandardCharsets.UTF_8);
        var upload=new MockMultipartFile("file","..\\贡献证明.txt","text/plain",content);
        MvcResult result=mvc.perform(multipart("/api/evidence/upload").file(upload).param("projectId",String.valueOf(projectId)).param("title","附件测试").header("Authorization","Bearer "+student)).andExpect(status().isOk()).andReturn();
        JsonNode proof=json.readTree(result.getResponse().getContentAsByteArray());
        assertThat(proof.path("fileName").asText()).isEqualTo("贡献证明.txt");
        assertThat(proof.path("sha256").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        assertThat(proof.toString()).doesNotContain("storageName");
        String download="/api/evidence/"+id(proof)+"/download";
        mvc.perform(get(download).header("Authorization","Bearer "+student2)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().exists("Content-Disposition")).andExpect(content().bytes(content));
        request("GET",download,student3,null,403);
        request("GET",download,null,null,401);
        mvc.perform(multipart("/api/evidence/upload").file(new MockMultipartFile("file","index.html","text/html","<html>unsafe</html>".getBytes())).param("projectId",String.valueOf(projectId)).param("title","不允许的格式").header("Authorization","Bearer "+student)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/evidence/upload").file(new MockMultipartFile("file","large.txt","text/plain",new byte[10*1024*1024+1])).param("projectId",String.valueOf(projectId)).param("title","大文件").header("Authorization","Bearer "+student)).andExpect(status().isPayloadTooLarge());
        request("POST","/api/evidence/link",student,Map.of("projectId",projectId,"title","无效链接","url","javascript:alert(1)"),400);
    }

    @Test void anotherTeacherCannotInspectOrAcceptThisProjectsRecords() throws Exception {
        String username="teacher_"+UUID.randomUUID().toString().substring(0,8);
        long otherId=Db.insert(jdbc,"INSERT INTO users(username,name,role,password_hash) VALUES(?,?,?,?)",username,"其他教师","TEACHER",PasswordHasher.hash("demo1234"));
        String otherTeacher=login(username);
        long p=id(project("教师隔离项目",List.of(2L)));
        JsonNode task=task(p,2); long e=id(link(p,student));
        JsonNode created=request("POST","/api/contributions",student,submission(id(task),List.of(reference(e,criterion(task,0)))),200);
        request("GET","/api/tasks?projectId="+p,otherTeacher,null,403);
        request("GET","/api/evidence?projectId="+p,otherTeacher,null,403);
        request("GET","/api/contributions/"+id(created),otherTeacher,null,403);
        request("POST","/api/contributions/"+id(created)+"/reviews",otherTeacher,review(revision(created),"APPROVED",List.of(item(criterion(task,0),true))),403);
        request("GET","/api/reports?studentId=2",otherTeacher,null,403);
        assertThat(request("GET","/api/contributions",otherTeacher,null,200).size()).isZero();
        JsonNode otherProject=request("POST","/api/projects",otherTeacher,Map.of("name","另一教师项目","memberIds",List.of(2L)),200);
        JsonNode otherTask=request("POST","/api/tasks",otherTeacher,Map.of("projectId",id(otherProject),"title","另一教师任务","assigneeId",2L,"dueDate","2026-10-16","criteria",List.of("验收要求")),200);
        JsonNode otherEvidence=link(id(otherProject),student);
        JsonNode otherContribution=request("POST","/api/contributions",student,submission(id(otherTask),List.of(reference(id(otherEvidence),criterion(otherTask,0)))),200);
        JsonNode report=request("GET","/api/reports?studentId=2",otherTeacher,null,200);
        assertThat(report.path("contributions").size()).isEqualTo(1);
        assertThat(id(report.path("contributions").get(0))).isEqualTo(id(otherContribution));
        assertThat(report.path("summary").path("pending").asInt()).isEqualTo(1);
        assertThat(otherProject.path("ownerId").asLong()).isEqualTo(otherId);
    }

    @Test void simultaneousReviewsProduceOnlyOneImmutableDecision() throws Exception {
        long p=id(project("并发验收项目",List.of(2L))); JsonNode task=task(p,2); long e=id(link(p,student));
        JsonNode created=request("POST","/api/contributions",student,submission(id(task),List.of(reference(e,criterion(task,0)))),200);
        byte[] body=json.writeValueAsBytes(review(revision(created),"APPROVED",List.of(item(criterion(task,0),true))));
        Callable<Integer> action=() -> mvc.perform(post("/api/contributions/"+id(created)+"/reviews").header("Authorization","Bearer "+teacher).contentType("application/json").content(body)).andReturn().getResponse().getStatus();
        List<Integer> statuses=new ArrayList<>();
        try(var executor=Executors.newFixedThreadPool(2)) { for(var future:executor.invokeAll(List.of(action,action))) statuses.add(future.get()); }
        assertThat(statuses).containsExactlyInAnyOrder(200,409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE revision_id=?",Long.class,revision(created))).isEqualTo(1);
    }
}



