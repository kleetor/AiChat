package com.example.aichat;

import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class AichatApplication {

    private static final Logger logger = LoggerFactory.getLogger(AichatApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AichatApplication.class, args);
    }

    @Bean
    CommandLineRunner initAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.default.username:admin}") String adminUsername,
            @Value("${admin.default.password:admin123}") String adminPassword,
            @Value("${admin.default.email:admin@aichat.com}") String adminEmail) {
        return args -> {
            if (userRepository.existsByRole("ADMIN")) {
                logger.info("管理员账户已存在，跳过初始化");
                return;
            }
            User admin = User.builder()
                    .username(adminUsername)
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .pid("999999")
                    .role("ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            logger.info("默认管理员账户已创建: {}", adminUsername);
        };
    }
}
