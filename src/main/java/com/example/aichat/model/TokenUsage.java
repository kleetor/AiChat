package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_usages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    private Long modelConfigId;

    @Column(nullable = false)
    private String modelName;

    @Column(nullable = false)
    private Long inputTokens;

    @Column(nullable = false)
    private Long outputTokens;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal costAmount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}