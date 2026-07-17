package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "prompt_ratings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "prompt_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptRating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "prompt_id", nullable = false)
    private Long promptId;

    @Column(nullable = false, columnDefinition = "TINYINT")
    private Integer rating;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
