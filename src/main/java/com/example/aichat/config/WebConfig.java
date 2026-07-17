package com.example.aichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Value("${upload.dir:./uploads/images}")
    private String uploadDir;

    @Value("${upload.url-prefix:/uploads/images}")
    private String uploadUrlPrefix;

    @Value("${cors.allowed-origins:http://localhost:5173,http://localhost:8080}")
    private String allowedOriginsConfig;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> allowedOrigins = parseAllowedOrigins();
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String resolvedDir = uploadDir;
        if (!new File(uploadDir).isAbsolute()) {
            resolvedDir = System.getProperty("user.dir") + File.separator + uploadDir;
        }
        File dirFile = new File(resolvedDir);
        try {
            resolvedDir = dirFile.getCanonicalPath();
        } catch (java.io.IOException ignored) {
            resolvedDir = dirFile.getAbsolutePath();
        }
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        String absolutePath = resolvedDir + File.separator;
        String pattern = uploadUrlPrefix.endsWith("/")
                ? uploadUrlPrefix + "**"
                : uploadUrlPrefix + "/**";
        registry.addResourceHandler(pattern)
                .addResourceLocations("file:" + absolutePath);

        // 随机封面图目录映射
        addUploadResourceMapping(registry, "uploads/random-covers", "/uploads/random-covers");
        // 赞助相关图片目录映射
        addUploadResourceMapping(registry, "uploads/Storepic", "/uploads/Storepic");
        addUploadResourceMapping(registry, "uploads/upStorepic", "/uploads/upStorepic");
        addUploadResourceMapping(registry, "uploads/userPic", "/uploads/userPic");
    }

    private void addUploadResourceMapping(ResourceHandlerRegistry registry, String dir, String urlPrefix) {
        String baseDir = System.getProperty("user.dir");
        String resolvedDir = baseDir + File.separator + dir;
        File dirFile = new File(resolvedDir);
        try {
            resolvedDir = dirFile.getCanonicalPath();
        } catch (java.io.IOException ignored) {
            resolvedDir = dirFile.getAbsolutePath();
        }
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations("file:" + resolvedDir + File.separator);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = parseAllowedOrigins();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOrigins() {
        return Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
