package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_operation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作管理员ID */
    @Column(nullable = false)
    private Long adminId;

    /** 操作管理员用户名（冗余，方便查询时无需联表） */
    @Column(nullable = false, length = 50)
    private String adminUsername;

    /** 操作类型，如 BALANCE_UPDATE、ROLE_UPDATE */
    @Column(nullable = false, length = 50)
    private String action;

    /** 操作目标实体类型 */
    @Column(nullable = false, length = 50)
    private String targetType;

    /** 操作目标实体ID */
    @Column
    private Long targetId;

    /** 操作详情（JSON 格式，记录变更前后的关键字段） */
    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 操作者 IP */
    @Column(length = 45)
    private String ipAddress;

    /** 操作时间 */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
