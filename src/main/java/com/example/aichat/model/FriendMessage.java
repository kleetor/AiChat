package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "friend_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FriendMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发送者用户ID */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** 接收者用户ID */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** 关联的好友关系ID */
    @Column(name = "friendship_id", nullable = false)
    private Long friendshipId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
