package cn.edu.contribution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class Db {
    private Db() {}
    static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
    static String now() { return Instant.now().toString(); }
    static Map<String, Object> map(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
    static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw ApiException.bad("请填写" + field);
        return optional(value, field, max);
    }
    static String optional(String value, String field, int max) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > max) throw ApiException.bad(field + "不能超过 " + max + " 字");
        return clean;
    }
    static long id(Long value, String field) {
        if (value == null || value <= 0) throw ApiException.bad("请选择有效的" + field);
        return value;
    }
}
