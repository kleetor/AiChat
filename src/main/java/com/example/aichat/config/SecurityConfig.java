package com.example.aichat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Spring Security 7 的 MvcRequestMatcher 对 /** 通配符模式匹配不可靠，
        // 使用自定义 RequestMatcher 直接检查 URI 确保静态资源 permitAll() 生效。
        RequestMatcher staticResources = staticResourceMatcher();

        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ===== 静态资源（最优先，使用显式 AntPathRequestMatcher）=====
                        .requestMatchers(staticResources).permitAll()
                        // ===== 页面路由 =====
                        .requestMatchers("/", "/index.html", "/login", "/chat", "/prompt-hub", "/workshop",
                                "/kb-manager", "/memory-manager", "/admin").permitAll()
                        // ===== 公开 API =====
                        .requestMatchers("/api/auth/send-code", "/api/auth/register", "/api/auth/login",
                                "/api/auth/send-reset-code", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/admin/login").permitAll()
                        // ===== 管理员 API =====
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        // ===== 上传文件 =====
                        .requestMatchers("/uploads/**").permitAll()
                        // ===== 其他所有请求需要认证 =====
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .securityContext(securityContext -> securityContext.requireExplicitSave(false))
                // ========== 安全响应头 ==========
                .headers(headers -> headers
                        // 点击劫持防护
                        .frameOptions(frame -> frame.deny())
                        // 内容类型嗅探防护
                        .contentTypeOptions(contentTypeOptions -> {})
                        // 强制 HTTPS（生产环境启用）
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true))
                        // XSS 防护（关闭浏览器过时 Auditor，由 CSP 接管）
                        .xssProtection(xss -> xss.disable())
                        // 内容安全策略
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net https://static.cloudflareinsights.com; " +
                                        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                        "img-src 'self' data: blob: https:; " +
                                        "font-src 'self' https://fonts.gstatic.com; " +
                                        "connect-src 'self' https: https://static.cloudflareinsights.com https://cloudflareinsights.com; " +
                                        "frame-ancestors 'none'"))
                )
        ;
        return http.build();
    }

    /**
     * 构建静态资源匹配器，通过直接检查请求 URI 绕过 Spring Security 7
     * 的 MvcRequestMatcher 模式匹配问题。
     */
    private RequestMatcher staticResourceMatcher() {
        return request -> {
            String uri = request.getRequestURI();
            // 路径前缀
            if (uri.startsWith("/assets/") || uri.startsWith("/uploads/")) {
                return true;
            }
            // 根路径具体文件
            if (uri.equals("/favicon.ico") || uri.equals("/favicon.svg")
                    || uri.equals("/icons.svg") || uri.equals("/HanaChat.png")) {
                return true;
            }
            // 扩展名通配（兜底）
            for (String ext : new String[]{".css", ".js", ".png", ".svg", ".ico", ".woff", ".woff2", ".ttf", ".eot"}) {
                if (uri.endsWith(ext)) {
                    return true;
                }
            }
            return false;
        };
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            if (authException instanceof DisabledException) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write(
                    "{\"error\":\"账号已被禁用\",\"message\":\"您的账号已被管理员禁用，请联系客服\"}");
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write(
                    "{\"error\":\"未授权\",\"message\":\"请先登录或提供有效的认证令牌\"}");
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}