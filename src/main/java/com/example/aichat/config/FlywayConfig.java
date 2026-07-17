package com.example.aichat.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring Boot 4.0 移除了 Flyway 自动配置，改为手动配置。
 * Flyway 迁移在 Bean 初始化时执行，早于 JPA EntityManagerFactory，
 * 确保 Hibernate 校验 schema 前迁移已完成。
 */
@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    @Value("${spring.flyway.enabled:true}")
    private boolean flywayEnabled;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String locations;

    @Value("${spring.flyway.baseline-on-migrate:false}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.baseline-version:1}")
    private String baselineVersion;

    @Bean
    public Flyway flyway(DataSource dataSource) {
        if (!flywayEnabled) {
            logger.info("Flyway migration is disabled by configuration.");
            return null;
        }

        logger.info("=== Flyway: loading configuration ===");
        logger.info("  locations: {}", locations);
        logger.info("  baseline-on-migrate: {}", baselineOnMigrate);
        logger.info("  baseline-version: {}", baselineVersion);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load();

        MigrationInfoService info = flyway.info();
        logger.info("=== Flyway: current schema version: {} ===", info.current() != null ? info.current().getVersion() : "none");
        logger.info("=== Flyway: pending migrations: {} ===", info.pending().length);

        if (info.pending().length > 0) {
            logger.info("=== Flyway: starting migration ===");
            MigrateResult result = flyway.migrate();
            logger.info("=== Flyway: migration completed - {} migration(s) applied ===", result.migrationsExecuted);
            info = flyway.info();
            logger.info("=== Flyway: new schema version: {} ===", info.current().getVersion());
        } else {
            logger.info("=== Flyway: no pending migrations, schema is up to date ===");
        }

        return flyway;
    }
}
