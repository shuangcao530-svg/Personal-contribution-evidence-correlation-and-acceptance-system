package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AuthService auth;
    private final ProjectService projects;
    private final EvidenceService evidence;
    private final ContributionService contributions;
    private final ActivityService activity;
    public ApiController(AuthService auth, ProjectService projects, EvidenceService evidence, ContributionService contributions, ActivityService activity) { this.auth=auth; this.projects=projects; this.evidence=evidence; this.contributions=contributions; this.activity=activity; }
    @GetMapping("/health") public Map<String,Object> health() { return Db.map("app", "contribution-evidence", "status", "UP"); }
    @PostMapping("/auth/login") public Map<String,Object> login(@RequestBody LoginInput input) { return auth.login(input); }
    @GetMapping("/auth/me") public User me(@RequestAttribute User user) { return user; }
    @PostMapping("/auth/logout") public Map<String,Object> logout(HttpServletRequest request) { auth.logout(request.getHeader("Authorization")); return Db.map("message", "已退出登录"); }
    @GetMapping("/users") public List<User> users(@RequestAttribute User user) { return auth.users(user); }
    @GetMapping("/dashboard") public Map<String,Object> dashboard(@RequestAttribute User user) { return projects.dashboard(user); }
    @GetMapping("/activity") public List<Map<String,Object>> activity(@RequestAttribute User user,@RequestParam(required=false) Long projectId) { return activity.list(user,projectId); }
    @GetMapping("/projects") public List<Map<String,Object>> projects(@RequestAttribute User user) { return projects.list(user); }
    @PostMapping("/projects") public Map<String,Object> createProject(@RequestAttribute User user, @RequestBody ProjectInput input) { return projects.create(user,input); }
    @GetMapping("/projects/{id}/matrix") public Map<String,Object> contributionMatrix(@RequestAttribute User user,@PathVariable long id) { return projects.contributionMatrix(user,id); }
    @GetMapping("/tasks") public List<Map<String,Object>> tasks(@RequestAttribute User user, @RequestParam(required=false) Long projectId) { return projects.tasks(user,projectId); }
    @PostMapping("/tasks") public Map<String,Object> createTask(@RequestAttribute User user, @RequestBody TaskInput input) { return projects.createTask(user,input); }
    @GetMapping("/evidence") public List<Map<String,Object>> evidence(@RequestAttribute User user, @RequestParam long projectId) { return evidence.list(user,projectId); }
    @PostMapping("/evidence/link") public Map<String,Object> link(@RequestAttribute User user, @RequestBody LinkInput input) { return evidence.link(user,input); }
    @PostMapping(value="/evidence/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Map<String,Object> upload(@RequestAttribute User user, @RequestParam long projectId, @RequestParam String title, @RequestPart MultipartFile file) { return evidence.upload(user,projectId,title,file); }
    @GetMapping("/evidence/{id}/preview") public ResponseEntity<Resource> preview(@RequestAttribute User user,@PathVariable long id){
        var preview=evidence.preview(user,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(preview.contentType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(preview.resource());
    }
    @GetMapping("/evidence/{id}/download") public ResponseEntity<Resource> download(@RequestAttribute User user,@PathVariable long id) {
        var download = evidence.download(user,id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(download.fileName(),StandardCharsets.UTF_8).build().toString()).body(download.resource());
    }
    @GetMapping("/contributions") public List<Map<String,Object>> contributions(@RequestAttribute User user, @RequestParam(required=false) Long projectId) { return contributions.list(user,projectId); }
    @GetMapping("/contributions/{id}") public Map<String,Object> contribution(@RequestAttribute User user,@PathVariable long id) { return contributions.get(user,id); }
    @PostMapping("/contributions") public Map<String,Object> createContribution(@RequestAttribute User user,@RequestBody ContributionInput input) { return contributions.create(user,input); }
    @PostMapping("/contributions/{id}/revisions") public Map<String,Object> revise(@RequestAttribute User user,@PathVariable long id,@RequestBody RevisionInput input) { return contributions.revise(user,id,input); }
    @PostMapping("/contributions/{id}/reviews") public Map<String,Object> review(@RequestAttribute User user,@PathVariable long id,@RequestBody ReviewInput input) { return contributions.accept(user,id,input); }
    @GetMapping("/reports") public Map<String,Object> report(@RequestAttribute User user,@RequestParam(required=false) Long studentId) { return contributions.report(user,studentId); }
}
