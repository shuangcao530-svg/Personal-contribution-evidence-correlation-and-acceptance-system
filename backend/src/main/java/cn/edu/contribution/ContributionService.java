package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContributionService {
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    private final EvidenceService evidence;
    private final AuthService auth;
    private final ActivityService activity;
    private record ContributionRow(long id, long taskId, long studentId, String status, int currentVersion) {}
    public ContributionService(JdbcTemplate jdbc, ProjectService projects, EvidenceService evidence, AuthService auth, ActivityService activity) { this.jdbc = jdbc; this.projects = projects; this.evidence = evidence; this.auth = auth; this.activity=activity; }

    private ContributionRow row(long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM contributions WHERE id=?" + (lock ? " FOR UPDATE" : ""), (rs,n) -> new ContributionRow(rs.getLong("id"), rs.getLong("task_id"), rs.getLong("student_id"), rs.getString("status"), rs.getInt("current_version")), id);
        if (rows.isEmpty()) throw ApiException.missing("贡献申报不存在");
        return rows.getFirst();
    }
    private void requireVisible(User user, ContributionRow contribution) {
        var task = projects.taskRow(contribution.taskId());
        if (user.teacher()) projects.requireOwner(user, task.projectId());
        else {
            projects.requireMember(user, task.projectId());
            if (contribution.studentId() != user.id()) throw ApiException.forbidden("只能查看本人的贡献申报");
        }
    }
    public List<Map<String,Object>> list(User user, Long projectId) {
        if (projectId != null) projects.requireMember(user, projectId);
        return visibleIds(user, projectId, null).stream().map(this::detail).toList();
    }
    private List<Long> visibleIds(User user, Long projectId, Long studentId) {
        String sql = "SELECT c.id FROM contributions c JOIN tasks t ON c.task_id=t.id JOIN projects p ON t.project_id=p.id WHERE " + (user.teacher() ? "p.owner_id=?" : "c.student_id=?");
        var args = new ArrayList<Object>(); args.add(user.id());
        if (projectId != null) { sql += " AND t.project_id=?"; args.add(projectId); }
        if (studentId != null) { sql += " AND c.student_id=?"; args.add(studentId); }
        return jdbc.queryForList(sql + " ORDER BY c.updated_at DESC,c.id DESC", Long.class, args.toArray());
    }
    public Map<String,Object> get(User user, long id) { requireVisible(user, row(id, false)); return detail(id); }
    private Map<String,Object> detail(long id) {
        return jdbc.queryForObject("SELECT c.*,t.project_id,t.title task_title,p.name project_name,u.name student_name,r.title FROM contributions c JOIN tasks t ON c.task_id=t.id JOIN projects p ON t.project_id=p.id JOIN users u ON c.student_id=u.id JOIN revisions r ON r.contribution_id=c.id AND r.version=c.current_version WHERE c.id=?", (rs,n) -> Db.map("id", rs.getLong("id"), "projectId", rs.getLong("project_id"), "projectName", rs.getString("project_name"), "taskId", rs.getLong("task_id"), "taskTitle", rs.getString("task_title"), "studentId", rs.getLong("student_id"), "studentName", rs.getString("student_name"), "title", rs.getString("title"), "status", rs.getString("status"), "createdAt", rs.getString("created_at"), "updatedAt", rs.getString("updated_at"), "currentVersion", rs.getInt("current_version"), "revisions", revisions(id)), id);
    }
    private List<Map<String,Object>> revisions(long contributionId) {
        return jdbc.query("SELECT * FROM revisions WHERE contribution_id=? ORDER BY version", (rs,n) -> Db.map("id", rs.getLong("id"), "version", rs.getInt("version"), "title", rs.getString("title"), "description", rs.getString("description"), "createdAt", rs.getString("created_at"), "evidence", references(rs.getLong("id")), "review", review(rs.getLong("id"))), contributionId);
    }
    private List<Map<String,Object>> references(long revisionId) {
        return jdbc.query("SELECT er.*,cr.text criterion_text FROM evidence_references er JOIN criteria cr ON er.criterion_id=cr.id WHERE er.revision_id=? ORDER BY er.id", (rs,n) -> {
            var result = evidence.get(rs.getLong("evidence_id"));
            result.put("criterionId", rs.getLong("criterion_id")); result.put("criterionText", rs.getString("criterion_text"));
            result.put("location", rs.getString("location")); result.put("note", rs.getString("note"));
            return result;
        }, revisionId);
    }
    private Map<String,Object> review(long revisionId) {
        var reviews = jdbc.query("SELECT r.*,u.name reviewer_name FROM reviews r JOIN users u ON r.reviewer_id=u.id WHERE r.revision_id=?", (rs,n) -> Db.map("id", rs.getLong("id"), "revisionId", rs.getLong("revision_id"), "reviewerName", rs.getString("reviewer_name"), "decision", rs.getString("decision"), "comment", rs.getString("comment"), "createdAt", rs.getString("created_at"), "items", jdbc.query("SELECT criterion_id,passed,comment FROM review_items WHERE review_id=? ORDER BY criterion_id", (item,i) -> Db.map("criterionId", item.getLong("criterion_id"), "passed", item.getBoolean("passed"), "comment", item.getString("comment")), rs.getLong("id"))), revisionId);
        return reviews.isEmpty() ? null : reviews.getFirst();
    }
    @Transactional public Map<String,Object> create(User user, ContributionInput input) {
        if (user.teacher()) throw ApiException.forbidden("仅学生可以提交个人贡献");
        var task = projects.taskRow(Db.id(input.taskId(), "任务"));
        projects.requireMember(user, task.projectId());
        if (task.assigneeId() != user.id()) throw ApiException.forbidden("只能向分配给本人的任务提交贡献");
        validateRevision(task, input.title(), input.description(), input.evidence());
        String time = Db.now();
        long id = Db.insert(jdbc, "INSERT INTO contributions(task_id,student_id,status,current_version,created_at,updated_at) VALUES(?,?,?,?,?,?)", task.id(), user.id(), "SUBMITTED", 1, time, time);
        insertRevision(id, 1, input.title(), input.description(), input.evidence(), time);
        activity.record(user,task.projectId(),"CONTRIBUTION_SUBMITTED","CONTRIBUTION",id,input.title());
        return detail(id);
    }
    @Transactional public Map<String,Object> revise(User user, long id, RevisionInput input) {
        var contribution = row(id, true);
        if (user.teacher() || contribution.studentId() != user.id()) throw ApiException.forbidden("仅申报本人可以补交");
        var task = projects.taskRow(contribution.taskId()); projects.requireMember(user, task.projectId());
        if (!"RETURNED".equals(contribution.status())) throw ApiException.conflict("仅待补充的申报可以重新提交");
        validateRevision(task, input.title(), input.description(), input.evidence());
        String time = Db.now(); int version = contribution.currentVersion() + 1;
        insertRevision(id, version, input.title(), input.description(), input.evidence(), time);
        jdbc.update("UPDATE contributions SET status='SUBMITTED',current_version=?,updated_at=? WHERE id=?", version, time, id);
        activity.record(user,task.projectId(),"CONTRIBUTION_RESUBMITTED","CONTRIBUTION",id,"第 "+version+" 版");
        return detail(id);
    }
    private void validateRevision(ProjectService.TaskRow task, String title, String description, List<ReferenceInput> references) {
        Db.required(title, "贡献标题", 160); Db.required(description, "本人贡献说明", 10000);
        if (references == null || references.isEmpty() || references.size() > 100) throw ApiException.bad("请关联 1 至 100 条证据引用");
        Set<String> unique = new HashSet<>();
        for (ReferenceInput reference : references) {
            if (reference == null) throw ApiException.bad("证据引用不能为空");
            long evidenceId = Db.id(reference.evidenceId(), "证据"), criterionId = Db.id(reference.criterionId(), "验收标准");
            var proof = evidence.get(evidenceId);
            if ((Long) proof.get("projectId") != task.projectId()) throw ApiException.bad("证据必须属于当前任务所在项目");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM criteria WHERE id=? AND task_id=?", Long.class, criterionId, task.id()) != 1) throw ApiException.bad("验收标准必须属于当前任务");
            String location = Db.required(reference.location(), "证据位置", 500);
            Db.required(reference.note(), "关联说明", 2000);
            if (!unique.add(evidenceId + ":" + criterionId + ":" + location)) throw ApiException.bad("同一标准下相同位置的证据不能重复关联");
        }
    }
    private void insertRevision(long contributionId, int version, String title, String description, List<ReferenceInput> references, String time) {
        long revisionId = Db.insert(jdbc, "INSERT INTO revisions(contribution_id,version,title,description,created_at) VALUES(?,?,?,?,?)", contributionId, version, title.strip(), description.strip(), time);
        for (var reference : references) jdbc.update("INSERT INTO evidence_references(revision_id,evidence_id,criterion_id,location,note) VALUES(?,?,?,?,?)", revisionId, reference.evidenceId(), reference.criterionId(), Db.optional(reference.location(), "证据位置", 500), Db.optional(reference.note(), "关联说明", 2000));
    }
    @Transactional public Map<String,Object> accept(User user, long id, ReviewInput input) {
        var contribution = row(id, true);
        projects.requireOwner(user, projects.taskRow(contribution.taskId()).projectId());
        if (!"SUBMITTED".equals(contribution.status())) throw ApiException.conflict("该申报已验收，请刷新列表");
        long revisionId = Db.id(input.revisionId(), "申报版本");
        Long current = jdbc.queryForObject("SELECT id FROM revisions WHERE contribution_id=? AND version=?", Long.class, id, contribution.currentVersion());
        if (current != revisionId) throw ApiException.conflict("只能验收最新待验收版本，请刷新详情");
        if (!Set.of("APPROVED", "RETURNED").contains(input.decision() == null ? "" : input.decision())) throw ApiException.bad("验收结果必须为通过或退回补充");
        Set<Long> expected = new HashSet<>(jdbc.queryForList("SELECT DISTINCT criterion_id FROM evidence_references WHERE revision_id=?", Long.class, revisionId));
        if (input.items() == null || input.items().isEmpty() || input.items().size() > 30) throw ApiException.bad("请逐项填写本次关联标准的验收结果");
        Set<Long> actual = new HashSet<>(); boolean allPassed = true;
        for (var item : input.items()) {
            if (item == null || item.passed() == null) throw ApiException.bad("请填写每条标准的验收结果");
            long criterionId = Db.id(item.criterionId(), "验收标准");
            if (!actual.add(criterionId)) throw ApiException.bad("验收标准不能重复");
            Db.optional(item.comment(), "单项验收意见", 2000);
            allPassed &= item.passed();
        }
        if (!actual.equals(expected)) throw ApiException.bad("验收项必须完整对应当前版本引用的标准，不能遗漏或额外添加");
        String comment = Db.optional(input.comment(), "总体验收意见", 4000);
        if ("APPROVED".equals(input.decision()) && !allPassed) throw ApiException.bad("通过验收需要所有关联标准均通过");
        if ("RETURNED".equals(input.decision()) && (allPassed || comment.isBlank())) throw ApiException.bad("退回补充需要至少一项不通过，并填写总体验收意见");
        String time = Db.now();
        long reviewId = Db.insert(jdbc, "INSERT INTO reviews(revision_id,reviewer_id,decision,comment,created_at) VALUES(?,?,?,?,?)", revisionId, user.id(), input.decision(), comment, time);
        for (var item : input.items()) jdbc.update("INSERT INTO review_items(review_id,criterion_id,passed,comment) VALUES(?,?,?,?)", reviewId, item.criterionId(), item.passed(), Db.optional(item.comment(), "单项验收意见", 2000));
        jdbc.update("UPDATE contributions SET status=?,updated_at=? WHERE id=?", input.decision(), time, id);
        var reviewedTask=projects.taskRow(contribution.taskId());
        activity.record(user,reviewedTask.projectId(),"CONTRIBUTION_REVIEWED","CONTRIBUTION",id,input.decision().equals("APPROVED")?"验收通过":"退回补充："+comment);
        return detail(id);
    }
    public Map<String,Object> report(User user, Long requestedStudentId) {
        long studentId = requestedStudentId == null ? user.id() : Db.id(requestedStudentId, "学生");
        if (!user.teacher() && studentId != user.id()) throw ApiException.forbidden("只能查看本人的贡献报告");
        User student = auth.user(studentId);
        if (user.teacher() && studentId != user.id() && jdbc.queryForObject("SELECT COUNT(*) FROM projects p JOIN project_members pm ON pm.project_id=p.id WHERE p.owner_id=? AND pm.user_id=?", Long.class, user.id(), studentId) == 0) throw ApiException.forbidden("只能查看所负责项目成员的贡献报告");
        var contributions = visibleIds(user, null, studentId).stream().map(this::detail).toList();
        long approved = contributions.stream().filter(item -> "APPROVED".equals(item.get("status"))).count();
        long pending = contributions.stream().filter(item -> "SUBMITTED".equals(item.get("status"))).count();
        long returned = contributions.stream().filter(item -> "RETURNED".equals(item.get("status"))).count();
        return Db.map("student", student, "summary", Db.map("total", contributions.size(), "approved", approved, "pending", pending, "returned", returned), "contributions", contributions);
    }
}
