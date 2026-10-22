package cn.edu.contribution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);
    @ExceptionHandler(ApiException.class) ResponseEntity<Map<String,Object>> api(ApiException ex) { return ResponseEntity.status(ex.status()).body(Db.map("message", ex.getMessage())); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,Object>> invalid(Exception ex) { return ResponseEntity.badRequest().body(Db.map("message", "请求格式不正确，请检查必填字段和编号")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<Map<String,Object>> large(Exception ex) { return ResponseEntity.status(413).body(Db.map("message", "上传文件不能超过 10MB")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<Map<String,Object>> integrity(Exception ex) { LOG.warn("数据库约束拒绝请求", ex); return ResponseEntity.status(409).body(Db.map("message", "记录已变更或存在重复操作，请刷新后重试")); }
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> other(Exception ex) { LOG.error("处理请求失败", ex); return ResponseEntity.internalServerError().body(Db.map("message", "服务器处理失败，请稍后重试")); }
}
