package com.example.aichat.config;

import com.example.aichat.repository.UserRepository;
import com.example.aichat.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        logger.debug("JWT过滤器 - 请求路径: {}", request.getRequestURI());
        logger.debug("JWT过滤器 - Token: {}", token != null ? "存在" : "不存在");
        
        if (StringUtils.hasText(token)) {
            try {
                if (!jwtUtil.validateToken(token)) {
                    throw new BadCredentialsException("Token无效或已过期");
                }

                // 检查 Token 是否已被加入黑名单（登出后失效）
                if (tokenBlacklist.isBlacklisted(token)) {
                    throw new BadCredentialsException("Token已失效，请重新登录");
                }

                Long userId = jwtUtil.getUserIdFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);
                if (role == null) role = "USER";
                logger.debug("JWT过滤器 - 用户ID: {}, 角色: {}", userId, role);

                // 检查用户是否被禁用
                boolean isEnabled = userRepository.findById(userId)
                        .map(u -> u.getEnabled())
                        .orElse(false);
                if (!isEnabled) {
                    logger.warn("JWT过滤器 - 用户已被禁用: {}", userId);
                    throw new DisabledException("您的账号已被管理员禁用，请联系客服");
                }

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                request.setAttribute("userId", userId);
                logger.debug("JWT过滤器 - 认证成功");
            } catch (BadCredentialsException | DisabledException e) {
                throw e;
            } catch (Exception e) {
                logger.error("JWT验证异常: {}", e.getMessage());
                throw new BadCredentialsException("Token验证失败");
            }
        }
        // 没有Token时继续执行，让后续过滤器处理（如允许匿名访问则放行，需要认证则由AuthorizationFilter拒绝）
        filterChain.doFilter(request, response);
    }

    /**
     * 只对 API 请求执行 JWT 校验，其余路径（静态资源、HTML 页面、上传文件等）
     * 全部跳过，交给 SecurityConfig 的 permitAll() 处理。
     * 
     * 避免过期 Token 误伤公开 API（如 /api/auth/login）和页面路由（如 /login）。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
