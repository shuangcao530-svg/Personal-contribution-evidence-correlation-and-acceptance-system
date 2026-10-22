package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class FilePersistenceTest {
    @Test void closingAndReopeningApplicationRetainsProjectRevisionReviewAndAttachment() throws Exception {
        Path directory=Path.of("target","restart-test",UUID.randomUUID().toString()).toAbsolutePath();
        String url="jdbc:h2:file:"+directory.resolve("database").toString().replace('\\','/')+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        String[] options={"--spring.profiles.active=test","--spring.datasource.url="+url,"--app.upload-dir="+directory.resolve("uploads"),"--app.seed-demo=false","--spring.main.banner-mode=off"};
        long teacherId,studentId,contributionId,evidenceId;
        byte[] content="重启后仍可下载的原始附件".getBytes(StandardCharsets.UTF_8);
        try(var context=new SpringApplicationBuilder(ContributionApplication.class).web(WebApplicationType.NONE).run(options)) {
            JdbcTemplate jdbc=context.getBean(JdbcTemplate.class);
            teacherId=Db.insert(jdbc,"INSERT INTO users(username,name,role,password_hash) VALUES(?,?,?,?)","restart_teacher","教师","TEACHER",PasswordHasher.hash("demo1234"));
            studentId=Db.insert(jdbc,"INSERT INTO users(username,name,role,password_hash) VALUES(?,?,?,?)","restart_student","学生","STUDENT",PasswordHasher.hash("demo1234"));
            AuthService auth=context.getBean(AuthService.class); User teacher=auth.user(teacherId),student=auth.user(studentId);
            ProjectService projects=context.getBean(ProjectService.class);
            long projectId=(Long)projects.create(teacher,new ProjectInput("持久化项目","重启校验",List.of(studentId))).get("id");
            var task=projects.createTask(teacher,new TaskInput(projectId,"持久化任务","测试",studentId,"2026-10-15",List.of("执行结果可复核")));
            long taskId=(Long)task.get("id");
            long criterionId=jdbc.queryForObject("SELECT id FROM criteria WHERE task_id=?",Long.class,taskId);
            EvidenceService evidence=context.getBean(EvidenceService.class);
            evidenceId=(Long)evidence.store(student,projectId,"持久化证据","证明.txt",content).get("id");
            ContributionService contributions=context.getBean(ContributionService.class);
            contributionId=(Long)contributions.create(student,new ContributionInput(taskId,"已持久化贡献","本人完成接口并提供执行记录",List.of(new ReferenceInput(evidenceId,criterionId,"全文","对应执行结果")))).get("id");
            long revisionId=jdbc.queryForObject("SELECT id FROM revisions WHERE contribution_id=?",Long.class,contributionId);
            contributions.accept(teacher,contributionId,new ReviewInput(revisionId,"APPROVED","已核验",List.of(new ReviewItemInput(criterionId,true,"满足标准"))));
        }
        assertThat(Files.exists(directory.resolve("database.mv.db"))).isTrue();
        try(var reopened=new SpringApplicationBuilder(ContributionApplication.class).web(WebApplicationType.NONE).run(options)) {
            AuthService auth=reopened.getBean(AuthService.class);
            Map<String,Object> login=auth.login(new LoginInput("restart_student","demo1234"));
            assertThat(((User)login.get("user")).id()).isEqualTo(studentId);
            var contribution=reopened.getBean(ContributionService.class).get(auth.user(teacherId),contributionId);
            assertThat(contribution.get("status")).isEqualTo("APPROVED");
            assertThat(contribution.get("currentVersion")).isEqualTo(1);
            assertThat(reopened.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM reviews",Long.class)).isEqualTo(1);
            assertThat(reopened.getBean(EvidenceService.class).download(auth.user(studentId),evidenceId).resource().getContentAsByteArray()).isEqualTo(content);
        }
    }
}

