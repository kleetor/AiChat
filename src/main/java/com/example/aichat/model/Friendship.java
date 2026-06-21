package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "friendships")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发起方用户ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 接收方用户ID */
    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    /** 状态：PENDING / ACCEPTED / REJECTED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
