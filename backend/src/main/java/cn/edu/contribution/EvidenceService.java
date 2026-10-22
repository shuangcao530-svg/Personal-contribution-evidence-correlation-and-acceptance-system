package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceService {
    private static final Set<String> ALLOWED = Set.of("txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx", "png", "jpg", "jpeg", "gif", "webp", "zip", "7z", "rar", "json", "log");
    private static final long MAX_SIZE = 10 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    private final ActivityService activity;
    private final Path uploadDir;
    public record Download(Resource resource, String fileName) {}
    public EvidenceService(JdbcTemplate jdbc, ProjectService projects, ActivityService activity, @Value("${app.upload-dir}") String uploadDir) {
        this.jdbc = jdbc; this.projects = projects; this.activity=activity; this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }
    public List<Map<String,Object>> list(User user, long projectId) {
        projects.requireMember(user, projectId);
        return jdbc.queryForList("SELECT id FROM evidence WHERE project_id=? ORDER BY id DESC", Long.class, projectId).stream().map(this::get).toList();
    }
    public Map<String,Object> get(long id) {
        var results = jdbc.query("SELECT e.*,u.name uploader_name FROM evidence e JOIN users u ON e.uploader_id=u.id WHERE e.id=?", (rs,n) -> Db.map("id", rs.getLong("id"), "projectId", rs.getLong("project_id"), "uploaderId", rs.getLong("uploader_id"), "uploaderName", rs.getString("uploader_name"), "title", rs.getString("title"), "type", rs.getString("type"), "url", rs.getString("url"), "fileName", rs.getString("file_name"), "sha256", rs.getString("sha256"), "category", category(rs.getString("type"),rs.getString("file_name")), "createdAt", rs.getString("created_at")), id);
        if (results.isEmpty()) throw ApiException.missing("证据不存在");
        return results.getFirst();
    }
    @Transactional public Map<String,Object> link(User user, LinkInput input) {
        long projectId = Db.id(input.projectId(), "项目"); projects.requireMember(user, projectId);
        String title = Db.required(input.title(), "证据标题", 160);
        String url = Db.required(input.url(), "证据链接", 2000);
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw ApiException.bad("证据链接必须是有效的 http 或 https 地址，且不能包含账号密码"); }
        long id = Db.insert(jdbc, "INSERT INTO evidence(project_id,uploader_id,title,type,url,created_at) VALUES(?,?,?,?,?,?)", projectId, user.id(), title, "LINK", url, Db.now());
        activity.record(user,projectId,"EVIDENCE_ADDED","EVIDENCE",id,title);
        return get(id);
    }
    @Transactional public Map<String,Object> upload(User user, long projectId, String title, MultipartFile file) {
        projects.requireMember(user, projectId);
        if (file == null || file.isEmpty()) throw ApiException.bad("请选择非空文件");
        if (file.getSize() > MAX_SIZE) throw new ApiException(413, "上传文件不能超过 10MB");
        try { return store(user, projectId, title, file.getOriginalFilename(), file.getBytes()); }
        catch (IOException ex) { throw new IllegalStateException("无法读取上传文件", ex); }
    }
    @Transactional public Map<String,Object> store(User user, long projectId, String title, String originalName, byte[] bytes) {
        projects.requireMember(user, projectId);
        title = Db.required(title, "证据标题", 160);
        String fileName = safeFileName(originalName);
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(extension)) throw ApiException.bad("不支持该文件格式，请上传常见文档、图片、文本或压缩包");
        if (bytes == null || bytes.length == 0) throw ApiException.bad("文件不能为空");
        if (bytes.length > MAX_SIZE) throw new ApiException(413, "上传文件不能超过 10MB");
        String storageName = UUID.randomUUID() + "." + extension;
        Path target = uploadDir.resolve(storageName).normalize();
        if (!target.startsWith(uploadDir)) throw ApiException.bad("文件路径无效");
        try {
            Files.createDirectories(uploadDir);
            Files.write(target, bytes);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) { try { Files.deleteIfExists(target); } catch (IOException ignored) {} }
                    }
                });
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            long id = Db.insert(jdbc, "INSERT INTO evidence(project_id,uploader_id,title,type,file_name,storage_name,sha256,created_at) VALUES(?,?,?,?,?,?,?,?)", projectId, user.id(), title, "FILE", fileName, storageName, hash, Db.now());
            activity.record(user,projectId,"EVIDENCE_ADDED","EVIDENCE",id,title);
            return get(id);
        } catch (Exception ex) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("证据文件保存失败", ex);
        }
    }
    private String category(String type,String fileName){
        if("LINK".equals(type))return "LINK";
        String ext=fileName==null||!fileName.contains(".")?"":fileName.substring(fileName.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(Set.of("png","jpg","jpeg","gif","webp").contains(ext))return "IMAGE";
        if(Set.of("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","md","csv","json","log").contains(ext))return "DOCUMENT";
        if(Set.of("zip","7z","rar").contains(ext))return "ARCHIVE";
        return "OTHER";
    }
    public Preview preview(User user,long id){
        var evidence=get(id);var file=download(user,id);String name=(String)evidence.get("fileName");String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String mime=switch(ext){case "pdf"->"application/pdf";case "png"->"image/png";case "jpg","jpeg"->"image/jpeg";case "gif"->"image/gif";case "webp"->"image/webp";case "txt","md","csv","json","log"->"text/plain;charset=UTF-8";default->throw ApiException.bad("此文件格式暂不支持在线预览");};
        return new Preview(file.resource(),mime);
    }
    public record Preview(Resource resource,String contentType){}
    private String safeFileName(String raw) {
        if (raw == null) throw ApiException.bad("缺少文件名");
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\\";]", "_");
        return Db.required(name, "文件名", 200);
    }
    public Download download(User user, long id) {
        var evidence = get(id);
        projects.requireMember(user, (Long) evidence.get("projectId"));
        if (!"FILE".equals(evidence.get("type"))) throw ApiException.bad("此证据是链接，没有可下载附件");
        String storageName = jdbc.queryForObject("SELECT storage_name FROM evidence WHERE id=?", String.class, id);
        Path file = uploadDir.resolve(storageName).normalize();
        if (!file.startsWith(uploadDir) || !Files.isRegularFile(file)) throw ApiException.missing("附件文件不存在，请联系项目教师");
        try { return new Download(new UrlResource(file.toUri()), (String) evidence.get("fileName")); }
        catch (IOException ex) { throw new IllegalStateException("附件读取失败", ex); }
    }
}
