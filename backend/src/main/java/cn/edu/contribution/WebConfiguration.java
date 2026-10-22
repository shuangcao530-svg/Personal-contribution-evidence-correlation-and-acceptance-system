package cn.edu.contribution;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final AuthService auth;
    public WebConfiguration(AuthService auth) { this.auth = auth; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Cache-Control", "no-store");
                request.setAttribute("user", auth.authenticate(request.getHeader("Authorization")));
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login", "/api/health");
    }
}
