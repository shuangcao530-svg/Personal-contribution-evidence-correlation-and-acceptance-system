package cn.edu.contribution;
import cn.edu.contribution.ApiModels.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
@Service
public class ActivityService {
 private final JdbcTemplate jdbc;
 public ActivityService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public void record(User actor,long projectId,String action,String entityType,long entityId,String summary){
  jdbc.update("INSERT INTO activity_logs(project_id,actor_id,action,entity_type,entity_id,summary,created_at) VALUES(?,?,?,?,?,?,?)",projectId,actor.id(),action,entityType,entityId,Db.optional(summary,"操作摘要",500),Db.now());
 }
 public List<Map<String,Object>> list(User user,Long projectId){
  String sql="SELECT a.id,a.project_id,p.name project_name,a.actor_id,u.name actor_name,a.action,a.entity_type,a.entity_id,a.summary,a.created_at FROM activity_logs a JOIN projects p ON p.id=a.project_id JOIN users u ON u.id=a.actor_id ";
  var mapper=(org.springframework.jdbc.core.RowMapper<Map<String,Object>>)(rs,n)->Db.map("id",rs.getLong("id"),"projectId",rs.getLong("project_id"),"projectName",rs.getString("project_name"),"actorId",rs.getLong("actor_id"),"actorName",rs.getString("actor_name"),"action",rs.getString("action"),"entityType",rs.getString("entity_type"),"entityId",rs.getLong("entity_id"),"summary",rs.getString("summary"),"createdAt",rs.getString("created_at"));
  if(projectId!=null){
   if(jdbc.queryForObject("SELECT COUNT(*) FROM projects WHERE id=?",Long.class,projectId)==0)throw ApiException.missing("项目不存在");
   if(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Long.class,projectId,user.id())==0)throw ApiException.forbidden("你不是该项目的成员");
   return jdbc.query(sql+"WHERE a.project_id=? ORDER BY a.id DESC LIMIT 200",mapper,projectId);
  }
  return jdbc.query(sql+"WHERE EXISTS (SELECT 1 FROM project_members pm WHERE pm.project_id=a.project_id AND pm.user_id=?) ORDER BY a.id DESC LIMIT 200",mapper,user.id());
 }
}
