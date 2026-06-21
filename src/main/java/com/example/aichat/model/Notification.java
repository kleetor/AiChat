package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户ID */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /** 通知类型：PROMPT_LIKE / PROMPT_COMMENT / COMMENT_REPLY / COMMENT_LIKE */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** 通知标题 */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 通知内容 */
    @Column(name = "content", length = 500)
    private String content;

    /** 关联的提示词ID（用于跳转） */
    @Column(name = "prompt_id")
    private Long promptId;

    /** 关联的评论ID（用于跳转） */
    @Column(name = "comment_id")
    private Long commentId;

    /** 触发者的用户名 */
    @Column(name = "from_user_name", length = 50)
    private String fromUserName;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
