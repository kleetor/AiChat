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

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.dir:./uploads/images}")
    private String uploadDir;

    @Value("${upload.url-prefix:/uploads/images}")
    private String uploadUrlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
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

        // 赞助相关图片目录映射
        addUploadResourceMapping(registry, "uploads/Storepic", "/uploads/Storepic");
        addUploadResourceMapping(registry, "uploads/upStorepic", "/uploads/upStorepic");
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
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
