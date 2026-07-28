package com.example.aichat;

import com.example.aichat.config.props.AdminProperties;
import com.example.aichat.model.User;
import com.example.aichat.repository.UserRepository;
import com.example.aichat.util.AESUtil;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class AichatApplication {

    private static final Logger logger = LoggerFactory.getLogger(AichatApplication.class);

    private final AdminProperties adminProperties;

    public AichatApplication(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(AichatApplication.class, args);
    }

    @Bean
    CommandLineRunner initAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByRole("ADMIN")) {
                logger.info("管理员账户已存在，跳过初始化");
                return;
            }
            User admin = User.builder()
                    .username(adminProperties.getUsername())
                    .email(adminProperties.getEmail())
                    .password(passwordEncoder.encode(adminProperties.getPassword()))
                    .pid("999999")
                    .role("ADMIN")
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            logger.info("默认管理员账户已创建: {}", adminProperties.getUsername()); 
        };
    }

    /**
     * 启动时回收孤儿预扣余额（服务崩溃/重启时 reserved_balance > 0 的用户）。
     * 将 reserved_balance 归还到 balance，并打印 WARN 日志供人工核查。
     */
    @Bean
    CommandLineRunner recoverOrphanReservations(UserRepository userRepository,
                                                 TransactionTemplate transactionTemplate) {
        return args -> {
            transactionTemplate.executeWithoutResult(status -> {
                List<User> users = userRepository.findUsersWithReservedBalance();
                if (users.isEmpty()) {
                    logger.info("无孤儿预扣余额需要回收");
                    return;
                }
                for (User user : users) {
                    BigDecimal orphan = user.getReservedBalance();
                    user.setBalance(user.getBalance().add(orphan));
                    user.setReservedBalance(BigDecimal.ZERO);
                    userRepository.save(user);
                    logger.warn("已回收孤儿预扣余额: userId={}, username={}, amount={}",
                            user.getId(), user.getUsername(), orphan);
                }
                logger.warn("共回收 {} 个用户的孤儿预扣余额，请人工核实是否存在异常扣款", users.size());
            });
        };
    }

    /**
     * 将数据库中明文 API Key 迁移为 AES 加密存储（ENC: 前缀）。
     * 兼容历史 "AES:" 前缀数据（转换为新格式）。
     * 使用原生 SQL 直接读写，绕过 JPA 脏检查和 Converter。
     */
    @Bean
    CommandLineRunner migrateModelConfigApiKeys(EntityManager entityManager,
                                                 TransactionTemplate transactionTemplate) {
        return args -> {
            transactionTemplate.executeWithoutResult(status -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT id, api_key FROM model_configs").getResultList();
                int migrated = 0;
                int skipped = 0;
                int fixed = 0;
                for (Object[] row : rows) {
                    Long id = ((Number) row[0]).longValue();
                    String rawApiKey = (String) row[1];
                    if (rawApiKey == null) continue;

                    if (rawApiKey.startsWith("ENC:")) {
                        // 已使用 ENC: 前缀（新格式），跳过
                        skipped++;
                        continue;
                    }

                    if (rawApiKey.startsWith("AES:")) {
                        // 历史旧格式 AES: → 尝试解密后转为 ENC: 格式
                        String cipherPart = rawApiKey.substring("AES:".length());
                        String decrypted = AESUtil.decrypt(cipherPart);
                        if (decrypted != null) {
                            String newValue = "ENC:" + AESUtil.encrypt(decrypted);
                            entityManager.createNativeQuery(
                                    "UPDATE model_configs SET api_key = :val WHERE id = :id")
                                    .setParameter("val", newValue)
                                    .setParameter("id", id)
                                    .executeUpdate();
                            fixed++;
                            continue;
                        }
                        logger.warn("ModelConfig id={} 的 API Key 密钥不匹配，请通过管理后台重新设置", id);
                        skipped++;
                        continue;
                    }

                    // 明文数据 → 加密为 ENC: 格式
                    String newValue = "ENC:" + AESUtil.encrypt(rawApiKey);
                    entityManager.createNativeQuery(
                            "UPDATE model_configs SET api_key = :val WHERE id = :id")
                            .setParameter("val", newValue)
                            .setParameter("id", id)
                            .executeUpdate();
                    migrated++;
                }
                logger.info("API Key 加密迁移完成: 新加密 {} 条, 修复旧格式 {} 条, 跳过 {} 条",
                        migrated, fixed, skipped);
            });
        };
    }
}
