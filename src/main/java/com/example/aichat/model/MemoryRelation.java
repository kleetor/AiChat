package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "memory_relations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(nullable = false, length = 100)
    private String predicate;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private LocalDateTime validFrom = LocalDateTime.now();

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
