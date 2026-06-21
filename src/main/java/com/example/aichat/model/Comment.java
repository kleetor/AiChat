package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", length = 50)
    private String userName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 父评论ID，null表示顶层评论 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 被回复的用户名，用于展示 "回复 @xxx" */
    @Column(name = "reply_to_name", length = 50)
    private String replyToName;

    @Column(name = "likes_count")
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
