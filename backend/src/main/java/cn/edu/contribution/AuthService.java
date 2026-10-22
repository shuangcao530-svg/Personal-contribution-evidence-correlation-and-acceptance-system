package cn.edu.contribution;

import cn.edu.contribution.ApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private record Session(User user, Instant expiresAt) {}
    public AuthService(JdbcTemplate jdbc, @Value("${app.token-hours:12}") long hours) { this.jdbc = jdbc; lifetime = Duration.ofHours(hours); }

    public Map<String, Object> login(LoginInput input) {
        String username = Db.required(input.username(), "用户名", 80);
        String password = Db.required(input.password(), "密码", 200);
        var found = jdbc.query("SELECT id,username,name,role,password_hash FROM users WHERE username=?", (rs, n) -> Db.map("user", new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role")), "hash", rs.getString("password_hash")), username);
        if (found.isEmpty() || !PasswordHasher.verify(password, (String) found.getFirst().get("hash"))) throw new ApiException(401, "用户名或密码不正确");
        User user = (User) found.getFirst().get("user");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        sessions.put(token, new Session(user, now.plus(lifetime)));
        return Db.map("token", token, "user", user);
    }
    public User authenticate(String header) {
        String token = bearer(header);
        Session session = sessions.get(token);
        if (session == null || !session.expiresAt().isAfter(Instant.now())) {
            sessions.remove(token);
            throw new ApiException(401, "登录已失效，请重新登录");
        }
        return session.user();
    }
    public void logout(String header) { sessions.remove(bearer(header)); }
    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) throw new ApiException(401, "请先登录");
        return header.substring(7);
    }
    public User user(long id) {
        var found = jdbc.query("SELECT id,username,name,role FROM users WHERE id=?", (rs, n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role")), id);
        if (found.isEmpty()) throw ApiException.missing("用户不存在");
        return found.getFirst();
    }
    public List<User> users(User user) {
        if (!user.teacher()) throw ApiException.forbidden("仅教师可以查看用户列表");
        return jdbc.query("SELECT id,username,name,role FROM users ORDER BY id", (rs, n) -> new User(rs.getLong("id"), rs.getString("username"), rs.getString("name"), rs.getString("role")));
    }
}
