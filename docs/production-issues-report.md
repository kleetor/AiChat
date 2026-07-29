# 生产环境问题排查与修复报告

**时间**：2026-07-28  
**环境**：Spring Boot 4.0.6 + Spring Security 7.x + Cloudflare CDN  
**域名**：https://www.man8out.xyz

---

## 问题1：JS/CSS 文件 403 Forbidden

### 现象
```
GET https://www.man8out.xyz/assets/index-JpUa_dJC.js  net::ERR_ABORTED 403 (Forbidden)
Refused to apply style from 'https://www.man8out.xyz/assets/index-CAbtnCaB.css'
  because its MIME type ('') is not a supported stylesheet MIME type
```

### 排查
- Spring Security `SecurityConfig` 已配置 `.permitAll()` 放行 `/assets/**`、`/**/*.css`、`/**/*.js`
- `JwtAuthenticationFilter.shouldNotFilter()` 排除所有非 `/api/` 路径
- 终端 curl 测试发现：带 `Accept` 头的请求返回 401，无 `Accept` 头返回 200
- 最终确认是 Cloudflare CDN 的 **Bot Fight Mode** 或安全规则误拦截

### 修复
- Cloudflare 控制台：关闭 Bot Fight Mode 或将 `/assets/*` 加入白名单
- Cloudflare WAF：检查 Security Events 确认拦截规则并添加跳过规则

---

## 问题2：Content-Security-Policy 阻止 Google Fonts

### 现象（Firefox）
```
Content-Security-Policy：由于违反下列指令："style-src 'self' 'unsafe-inline'"
页面设置已阻止应用一个位于 https://fonts.googleapis.com/css2?... 的样式
```

### 根因
`SecurityConfig.java` 中 CSP 策略缺少 Google Fonts 域名：

**修改前：**
```
style-src 'self' 'unsafe-inline';
font-src 'self';
```

### 修复
**文件**：`src/main/java/com/example/aichat/config/SecurityConfig.java`

**修改后：**
```
style-src 'self' 'unsafe-inline' https://fonts.googleapis.com;
font-src 'self' https://fonts.gstatic.com;
```

---

## 问题3：CSP 阻止 Thymeleaf 模板内联脚本

### 现象（Firefox）
```
Content-Security-Policy：由于违反下列指令："script-src 'self' https://unpkg.com ..."
页面设置已阻止执行一个内联脚本（script-src-elem）。
```

### 根因
`login.html` 等 Thymeleaf 模板中有内联 `<script>` 标签，但 `script-src` 缺少 `'unsafe-inline'`。

### 修复
**修改后：**
```
script-src 'self' 'unsafe-inline' https://unpkg.com https://cdn.jsdelivr.net https://static.cloudflareinsights.com;
```

---

## 问题4：CORS 跨域配置缺陷

### 根因
1. `WebConfig.java` 同时用了 `addCorsMappings()` 和 `CorsConfigurationSource` Bean，存在双写冲突
2. `docker-compose.yml` 未传入 `ALLOWED_ORIGINS` 环境变量，生产容器回退到 `localhost` 默认值
3. `List.of(allowedOrigins.split(","))` 将 `String[]` 作为单元素传入，类型错误

### 修复
- **WebConfig.java**：删除冗余的 `addCorsMappings()`，仅保留 `CorsConfigurationSource` Bean，修正为 `Arrays.asList()`
- **docker-compose.yml**：新增 `ALLOWED_ORIGINS` 环境变量，默认包含 `https://www.man8out.xyz`
- **application-prod.properties**：回退默认值加入生产域名
- **.env**：更新 `ALLOWED_ORIGINS` 包含生产域名

---

## 问题5：Spring Security 7 静态资源鉴权失效（核心问题）

### 现象
服务器对 `/assets/index-xxx.js` 返回 `401 Unauthorized`（JSON 响应），而不是返回文件内容。

### 根因
Spring Boot 4.0.6 搭载 Spring Security 7.x，默认使用 `MvcRequestMatcher` 进行模式匹配。`MvcRequestMatcher` 对 `/**/*.css`、`/**/*.js` 等 Ant 风格通配符模式匹配不可靠，导致 `.requestMatchers("/**/*.css").permitAll()` 实际未生效。请求最终落到 `.anyRequest().authenticated()` 触发 401。

### 修复
**文件**：`src/main/java/com/example/aichat/config/SecurityConfig.java`

用自定义 lambda `RequestMatcher` 直接检查 `request.getRequestURI()`，完全绕过 `MvcRequestMatcher`：

```java
private RequestMatcher staticResourceMatcher() {
    return request -> {
        String uri = request.getRequestURI();
        // 路径前缀匹配
        if (uri.startsWith("/assets/") || uri.startsWith("/uploads/")) {
            return true;
        }
        // 根路径具体文件
        if (uri.equals("/favicon.ico") || uri.equals("/favicon.svg")
                || uri.equals("/icons.svg") || uri.equals("/HanaChat.png")) {
            return true;
        }
        // 扩展名通配兜底
        for (String ext : new String[]{".css", ".js", ".png", ".svg",
                ".ico", ".woff", ".woff2", ".ttf", ".eot"}) {
            if (uri.endsWith(ext)) {
                return true;
            }
        }
        return false;
    };
}
```

并在 `filterChain` 中最优先匹配：

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(staticResources).permitAll()   // 静态资源最优先
    .requestMatchers("/", "/index.html", ...).permitAll()
    .requestMatchers("/api/auth/**", ...).permitAll()
    .anyRequest().authenticated()
)
```

---

## 附带修复：Vite 8 兼容性

### 问题
`vite.config.ts` 中 `build.esbuild` 配置在 Vite 8 中已被移除，且 `minify: 'esbuild'` 需要单独安装 esbuild。

### 修复
```typescript
// 修改前
build: {
    minify: 'esbuild',
    esbuild: { drop: ['console', 'debugger'] },
}

// 修改后
build: {
    minify: 'oxc',  // Vite 8 默认 minifier
}
```

---

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `src/main/java/com/example/aichat/config/SecurityConfig.java` | CSP 修复 + lambda RequestMatcher 替代 MvcRequestMatcher |
| `src/main/java/com/example/aichat/config/WebConfig.java` | 删除冗余 addCorsMappings，修复 List.of bug |
| `frontend/vite.config.ts` | esbuild → oxc，删除无效 esbuild 配置 |
| `docker-compose.yml` | 新增 ALLOWED_ORIGINS 环境变量 |
| `src/main/resources/application-prod.properties` | 更新 CORS 默认值 |
| `.env` | 更新 ALLOWED_ORIGINS |
