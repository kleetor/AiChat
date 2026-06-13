package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recharge_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RechargeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(length = 20)
    private String payChannel;

    @Column(length = 64)
    private String thirdPartyOrderId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    @Column(name = "sponsor_image_path", length = 500)
    private String sponsorImagePath;

    @Column(name = "user_pid", length = 10)
    private String userPid;

    @Column(name = "user_name", length = 50)
    private String userName;

    @Column(name = "review_status", length = 20)
    @Builder.Default
    private String reviewStatus = "PENDING";

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}