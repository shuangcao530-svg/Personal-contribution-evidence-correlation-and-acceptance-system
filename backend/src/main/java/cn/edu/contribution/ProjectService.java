package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {
    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final ActivityService activity;
    public record TaskRow(long id, long projectId, long assigneeId) {}
    public ProjectService(JdbcTemplate jdbc, AuthService auth, ActivityService activity) { this.jdbc = jdbc; this.auth = auth; this.activity=activity; }
    public void requireMember(User user, long projectId) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE id=?", Long.class, projectId) == 0) throw ApiException.missing("项目不存在");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?", Long.class, projectId, user.id()) == 0) throw ApiException.forbidden("你不是该项目的成员");
    }
    public void requireOwner(User user, long projectId) {
        requireMember(user, projectId);
        if (!user.teacher() || jdbc.queryForObject("SELECT owner_id FROM projects WHERE id=?", Long.class, projectId) != user.id()) throw ApiException.forbidden("仅项目负责教师可以执行此操作");
    }
    public List<Map<String,Object>> list(User user) {
        return jdbc.query("SELECT p.*,u.name owner_name FROM projects p JOIN users u ON p.owner_id=u.id JOIN project_members pm ON pm.project_id=p.id WHERE pm.user_id=? ORDER BY p.id DESC", (rs,n) -> Db.map("id", rs.getLong("id"), "name", rs.getString("name"), "description", rs.getString("description"), "ownerId", rs.getLong("owner_id"), "ownerName", rs.getString("owner_name"), "createdAt", rs.getString("created_at"), "members", members(rs.getLong("id"))), user.id());
    }
    public Map<String,Object> project(User user, long id) {
        requireMember(user, id);
        return jdbc.queryForObject("SELECT p.*,u.name owner_name FROM projects p JOIN users u ON p.owner_id=u.id WHERE p.id=?", (rs,n) -> Db.map("id", rs.getLong("id"), "name", rs.getString("name"), "description", rs.getString("description"), "ownerId", rs.getLong("owner_id"), "ownerName", rs.getString("owner_name"), "createdAt", rs.getString("created_at"), "members", members(id)), id);
    }
    public List<User> members(long projectId) {
        return jdbc.query("SELECT u.id,u.username,u.name,u.role FROM users u JOIN project_members pm ON u.id=pm.user_id WHERE pm.project_id=? ORDER BY u.id", (rs,n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role")), projectId);
    }
    @Transactional public Map<String,Object> create(User user, ProjectInput input) {
        if (!user.teacher()) throw ApiException.forbidden("仅教师可以创建项目");
        String name = Db.required(input.name(), "项目名称", 160);
        String description = Db.optional(input.description(), "项目说明", 10000);
        if (input.memberIds() == null || input.memberIds().isEmpty() || input.memberIds().size() > 100) throw ApiException.bad("请选择 1 至 100 位学生成员");
        var ids = new LinkedHashSet<Long>();
        for (Long id : input.memberIds()) {
            User member = auth.user(Db.id(id, "学生成员"));
            if (!"STUDENT".equals(member.role())) throw ApiException.bad("项目学生成员必须为学生账号");
            ids.add(id);
        }
        long id = Db.insert(jdbc, "INSERT INTO projects(name,description,owner_id,created_at) VALUES(?,?,?,?)", name, description, user.id(), Db.now());
        jdbc.update("INSERT INTO project_members(project_id,user_id) VALUES(?,?)", id, user.id());
        for (Long member : ids) jdbc.update("INSERT INTO project_members(project_id,user_id) VALUES(?,?)", id, member);
        activity.record(user,id,"PROJECT_CREATED","PROJECT",id,name);
        return project(user, id);
    }
    public TaskRow taskRow(long id) {
        var found = jdbc.query("SELECT id,project_id,assignee_id FROM tasks WHERE id=?", (rs,n) -> new TaskRow(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("assignee_id")), id);
        if (found.isEmpty()) throw ApiException.missing("任务不存在");
        return found.getFirst();
    }
    public List<Map<String,Object>> tasks(User user, Long projectId) {
        if (projectId != null) requireMember(user, projectId);
        var args = new ArrayList<Object>(); args.add(user.id());
        String filter = "";
        if (projectId != null) { filter = " AND t.project_id=?"; args.add(projectId); }
        var ids = jdbc.queryForList("SELECT t.id FROM tasks t JOIN project_members pm ON pm.project_id=t.project_id WHERE pm.user_id=?" + filter + " ORDER BY t.id DESC", Long.class, args.toArray());
        return ids.stream().map(this::task).toList();
    }
    public Map<String,Object> task(long id) {
        return jdbc.queryForObject("SELECT t.*,p.name project_name,u.name assignee_name FROM tasks t JOIN projects p ON t.project_id=p.id JOIN users u ON t.assignee_id=u.id WHERE t.id=?", (rs,n) -> Db.map("id", rs.getLong("id"), "projectId", rs.getLong("project_id"), "projectName", rs.getString("project_name"), "title", rs.getString("title"), "description", rs.getString("description"), "assigneeId", rs.getLong("assignee_id"), "assigneeName", rs.getString("assignee_name"), "dueDate", rs.getString("due_date"), "createdAt", rs.getString("created_at"), "criteria", criteria(id)), id);
    }
    public List<Map<String,Object>> criteria(long taskId) {
        return jdbc.query("SELECT id,text FROM criteria WHERE task_id=? ORDER BY id", (rs,n) -> Db.map("id", rs.getLong("id"), "text", rs.getString("text")), taskId);
    }
    @Transactional public Map<String,Object> createTask(User user, TaskInput input) {
        long projectId = Db.id(input.projectId(), "项目");
        requireOwner(user, projectId);
        String title = Db.required(input.title(), "任务名称", 160);
        String description = Db.optional(input.description(), "任务说明", 10000);
        User assignee = auth.user(Db.id(input.assigneeId(), "任务负责人"));
        if (assignee.teacher()) throw ApiException.bad("任务负责人必须为学生");
        requireMember(assignee, projectId);
        String dueDate = Db.required(input.dueDate(), "截止日期", 10);
        try { LocalDate.parse(dueDate); } catch (DateTimeParseException ex) { throw ApiException.bad("截止日期格式应为 YYYY-MM-DD"); }
        if (input.criteria() == null || input.criteria().isEmpty() || input.criteria().size() > 30) throw ApiException.bad("请填写 1 至 30 条验收标准");
        var standards = new LinkedHashSet<String>();
        for (String text : input.criteria()) {
            if (!standards.add(Db.required(text, "验收标准", 1000))) throw ApiException.bad("验收标准不能重复");
        }
        long id = Db.insert(jdbc, "INSERT INTO tasks(project_id,title,description,assignee_id,due_date,created_at) VALUES(?,?,?,?,?,?)", projectId, title, description, assignee.id(), dueDate, Db.now());
        for (String standard : standards) jdbc.update("INSERT INTO criteria(task_id,text) VALUES(?,?)", id, standard);
        activity.record(user,projectId,"TASK_CREATED","TASK",id,title);
        return task(id);
    }
    public Map<String,Object> contributionMatrix(User user, long projectId) {
        var project = project(user, projectId);
        var allMembers = members(projectId).stream().filter(member -> "STUDENT".equals(member.role())).toList();
        var visibleMembers = user.teacher() ? allMembers : allMembers.stream().filter(member -> member.id() == user.id()).toList();
        var projectTasks = tasks(user, projectId);
        var submissions = jdbc.query(
            "SELECT c.id,c.task_id,c.student_id,c.status,c.current_version,c.updated_at,r.title revision_title " +
            "FROM contributions c JOIN tasks t ON c.task_id=t.id " +
            "JOIN revisions r ON r.contribution_id=c.id AND r.version=c.current_version " +
            "WHERE t.project_id=? ORDER BY c.updated_at DESC,c.id DESC",
            (rs,n) -> Db.map("id",rs.getLong("id"),"taskId",rs.getLong("task_id"),"studentId",rs.getLong("student_id"),"status",rs.getString("status"),"currentVersion",rs.getInt("current_version"),"updatedAt",rs.getString("updated_at"),"title",rs.getString("revision_title")), projectId);
        var latest = new java.util.LinkedHashMap<String,Map<String,Object>>();
        for (var submission : submissions) latest.putIfAbsent(submission.get("studentId")+":"+submission.get("taskId"), submission);
        var cells = new ArrayList<Map<String,Object>>();
        long unassigned=0, notSubmitted=0, submitted=0, approved=0, returned=0;
        for (var member : visibleMembers) for (var task : projectTasks) {
            long memberId = (Long) member.id(); long taskId = (Long) task.get("id");
            if (((Long)task.get("assigneeId")) != memberId) {
                cells.add(Db.map("memberId",memberId,"taskId",taskId,"state","NOT_ASSIGNED","contribution",null));
                unassigned++;
                continue;
            }
            var submission = latest.get(memberId+":"+taskId);
            if (submission == null) {
                cells.add(Db.map("memberId",memberId,"taskId",taskId,"state","NO_SUBMISSION","contribution",null));
                notSubmitted++;
                continue;
            }
            String status = (String) submission.get("status");
            switch(status) {
                case "SUBMITTED" -> submitted++;
                case "APPROVED" -> approved++;
                case "RETURNED" -> returned++;
                default -> throw new IllegalStateException("Unknown contribution status: "+status);
            }
            cells.add(Db.map("memberId",memberId,"taskId",taskId,"state",status,"contribution",submission));
        }
        return Db.map("project",project,"members",visibleMembers,"tasks",projectTasks,"cells",cells,
            "summary",Db.map("notAssigned",unassigned,"notSubmitted",notSubmitted,"submitted",submitted,"approved",approved,"returned",returned),
            "viewerRole",user.teacher() ? "TEACHER" : "STUDENT");
    }

    public Map<String,Object> dashboard(User user) {
        String visible = user.teacher() ? "p.owner_id=?" : "c.student_id=?";
        var states = jdbc.query("SELECT c.status,COUNT(*) n FROM contributions c JOIN tasks t ON c.task_id=t.id JOIN projects p ON t.project_id=p.id WHERE " + visible + " GROUP BY c.status", (rs,n) -> Db.map("status",rs.getString("status"),"count",rs.getLong("n")), user.id());
        long pending = 0, approved = 0;
        for (var state : states) {
            if ("SUBMITTED".equals(state.get("status"))) pending = (Long) state.get("count");
            if ("APPROVED".equals(state.get("status"))) approved = (Long) state.get("count");
        }
        String taskScope=user.teacher()?"p.owner_id=?":"t.assignee_id=?";
        var attentionTasks=jdbc.query("SELECT t.id,t.project_id,p.name project_name,t.title,t.due_date,u.name assignee_name,CASE WHEN t.due_date<CURDATE() THEN 'OVERDUE' ELSE 'UPCOMING' END urgency FROM tasks t JOIN projects p ON p.id=t.project_id JOIN users u ON u.id=t.assignee_id WHERE "+taskScope+" AND t.due_date<=DATE_FORMAT(DATE_ADD(CURDATE(),INTERVAL 7 DAY),'%Y-%m-%d') AND NOT EXISTS (SELECT 1 FROM contributions c WHERE c.task_id=t.id AND c.student_id=t.assignee_id AND c.status='APPROVED') ORDER BY t.due_date,t.id LIMIT 12",(rs,n)->Db.map("id",rs.getLong("id"),"projectId",rs.getLong("project_id"),"projectName",rs.getString("project_name"),"title",rs.getString("title"),"dueDate",rs.getString("due_date"),"assigneeName",rs.getString("assignee_name"),"urgency",rs.getString("urgency")),user.id());
        long overdue=attentionTasks.stream().filter(task->"OVERDUE".equals(task.get("urgency"))).count();
        long dueSoon=attentionTasks.stream().filter(task->"UPCOMING".equals(task.get("urgency"))).count();
        return Db.map("projects",jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE user_id=?",Long.class,user.id()),"tasks",jdbc.queryForObject("SELECT COUNT(*) FROM tasks t JOIN project_members pm ON t.project_id=pm.project_id WHERE pm.user_id=?",Long.class,user.id()),"pending",pending,"approved",approved,"overdue",overdue,"dueSoon",dueSoon,"attentionTasks",attentionTasks);
    }
}

