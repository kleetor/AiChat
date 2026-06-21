package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "memory_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chroma_id", nullable = false, length = 100)
    private String chromaId;

    @Column(name = "`value`", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "original_value", columnDefinition = "TEXT")
    private String originalValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level", nullable = false, length = 20)
    @Builder.Default
    private DetailLevel detailLevel = DetailLevel.FULL;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "AUTO";

    @Column(name = "last_accessed_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum DetailLevel {
        FULL,   // 清晰期: 原文全文
        BRIEF,  // 模糊期: ~200字摘要
        TITLE   // 轮廓期: 一行~50字
    }
}
