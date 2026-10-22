package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final ProjectService projects;
    private final EvidenceService evidence;
    private final ContributionService contributions;
    private final boolean enabled;
    public DemoDataInitializer(JdbcTemplate jdbc, AuthService auth, ProjectService projects, EvidenceService evidence, ContributionService contributions, @Value("${app.seed-demo:true}") boolean enabled) {
        this.jdbc=jdbc; this.auth=auth; this.projects=projects; this.evidence=evidence; this.contributions=contributions; this.enabled=enabled;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!enabled || jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class) != 0) return;
        long teacherId = addUser("teacher","陈老师","TEACHER");
        long studentId = addUser("student","张三","STUDENT");
        long student2Id = addUser("student2","李四","STUDENT");
        long student3Id = addUser("student3","王五","STUDENT");
        User teacher = auth.user(teacherId), student = auth.user(studentId), student2 = auth.user(student2Id), student3 = auth.user(student3Id);
        var project = projects.create(teacher,new ProjectInput("校园二手交易平台","完成需求分析、功能开发与测试验收。",List.of(studentId,student2Id,student3Id)));
        long projectId = (Long)project.get("id");
        String due = LocalDate.now().plusDays(21).toString();
        var task1 = projects.createTask(teacher,new TaskInput(projectId,"用户登录接口","实现登录校验，并用测试材料说明正确登录与错误密码提示。",studentId,due,List.of("正确账号密码可以登录","错误密码返回明确提示")));
        var task2 = projects.createTask(teacher,new TaskInput(projectId,"登录页面与交互","完成登录表单与提示交互，说明本人编写的页面和协作边界。",student2Id,due,List.of("登录表单可输入账号和密码","表单校验和错误提示交互清晰")));
        var task3 = projects.createTask(teacher,new TaskInput(projectId,"登录异常场景测试","提交测试用例和执行结果，覆盖正确登录和错误密码场景。",student3Id,due,List.of("正确登录测试记录完整","错误密码场景有执行结果")));
        var proof1 = evidence.store(student,projectId,"登录接口测试记录（演示）","登录接口测试记录-演示.txt",("【演示材料：仅用于体验系统，不代表真实项目成果】\n作者声明：张三负责登录接口与参数校验。\n第1项：使用有效账号密码登录，预期成功，演示结果：通过。\n第2项：使用错误密码登录，预期明确错误提示，演示结果：通过。\n协作说明：前端页面由李四完成。\n").getBytes(StandardCharsets.UTF_8));
        var proof2 = evidence.store(student2,projectId,"登录页面实现说明（演示）","登录页面实现说明-演示.txt",("【演示材料：仅用于体验系统，不代表真实项目成果】\n作者声明：李四负责登录页面。\n第1项：登录表单展示账号和密码输入框，演示检查通过。\n第2项：空值校验、提交状态与错误提示清晰，演示检查通过。\n协作说明：登录接口由张三负责，本申报只涉及页面和交互。\n").getBytes(StandardCharsets.UTF_8));
        var proof3 = evidence.store(student3,projectId,"登录异常测试初稿（演示）","登录异常测试初稿-演示.txt",("【演示材料：仅用于体验系统，不代表真实项目成果】\n作者声明：王五负责登录异常场景测试。\n第1项：正确登录用例，已填写步骤和预期结果。\n第2项：错误密码用例，尚未记录实际执行结果，需要补充。\n").getBytes(StandardCharsets.UTF_8));
        var pending = sample(student,task1,proof1,"用户登录接口实现","我负责登录接口逻辑和错误提示校验，关联的演示测试记录分别对应两条验收标准。");
        var approved = sample(student2,task2,proof2,"登录页面与交互","演示申报：我完成了登录表单和校验提示交互，接口由张三负责。");
        var returned = sample(student3,task3,proof3,"登录异常场景测试","演示申报：我编写了正确登录与错误密码测试用例，目前错误密码场景还需要补录实际执行结果。");
        contributions.accept(teacher,(Long)approved.get("id"),new ReviewInput(revisionId(approved),"APPROVED","演示验收：两项材料定位清晰，确认本次申报所述页面贡献。",criteriaOf(task2).stream().map(item -> new ReviewItemInput((Long)item.get("id"),true,"演示检查通过")).toList()));
        var thirdCriteria = criteriaOf(task3);
        contributions.accept(teacher,(Long)returned.get("id"),new ReviewInput(revisionId(returned),"RETURNED","演示退回：请补充错误密码场景的实际执行结果，并在新版本中注明材料位置。",List.of(new ReviewItemInput((Long)thirdCriteria.get(0).get("id"),true,"正确登录记录可确认"),new ReviewItemInput((Long)thirdCriteria.get(1).get("id"),false,"缺少实际执行结果"))));
        // Demonstration timestamps make the recent activity order clear; actual user records use server time.
        stamp(approved, 2); stamp(returned, 1); stamp(pending, 0);
    }
    private long addUser(String username,String name,String role) { return Db.insert(jdbc,"INSERT INTO users(username,name,role,password_hash) VALUES(?,?,?,?)",username,name,role,PasswordHasher.hash("demo1234")); }
    private Map<String,Object> sample(User user,Map<String,Object> task,Map<String,Object> proof,String title,String description) {
        var refs = criteriaOf(task).stream().map(item -> new ReferenceInput((Long)proof.get("id"),(Long)item.get("id"),"附件中“"+item.get("text")+"”对应条目","演示材料，教师需结合具体成果核验个人贡献。")).toList();
        return contributions.create(user,new ContributionInput((Long)task.get("id"),title,description,refs));
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> criteriaOf(Map<String,Object> task) { return (List<Map<String,Object>>)task.get("criteria"); }
    @SuppressWarnings("unchecked") private long revisionId(Map<String,Object> contribution) { return (Long)((List<Map<String,Object>>)contribution.get("revisions")).getFirst().get("id"); }
    private void stamp(Map<String,Object> contribution, int daysAgo) {
        String timestamp=Instant.now().minus(daysAgo,ChronoUnit.DAYS).toString();
        jdbc.update("UPDATE contributions SET created_at=?,updated_at=? WHERE id=?",timestamp,timestamp,contribution.get("id"));
        jdbc.update("UPDATE revisions SET created_at=? WHERE contribution_id=?",timestamp,contribution.get("id"));
        jdbc.update("UPDATE reviews SET created_at=? WHERE revision_id=?",timestamp,revisionId(contribution));
    }
}
