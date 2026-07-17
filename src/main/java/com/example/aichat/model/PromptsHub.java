package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "prompts_hub")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptsHub {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_message", length = 500)
    private String userMessage;

    @Column(name = "user_name", length = 50)
    private String userName;

    @Column(name = "likes_count")
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "featured")
    @Builder.Default
    private Boolean featured = false;

    // ======== 新增字段 ========

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String category;

    /** 标签，存储 JSON 数组字符串，应用层反序列化 */
    @Column(columnDefinition = "JSON")
    private String tags;

    @Column(name = "model_support", length = 200)
    private String modelSupport;

    @Column(length = 20)
    @Builder.Default
    private String status = "published";

    @Column(length = 20)
    @Builder.Default
    private String version = "v1.0";

    @Column(name = "original_prompt_id")
    private Long originalPromptId;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "save_count")
    @Builder.Default
    private Integer saveCount = 0;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private java.math.BigDecimal avgRating = java.math.BigDecimal.ZERO;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
