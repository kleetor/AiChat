package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_summaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private Long conversationId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "message_count_at_generation", nullable = false)
    private Integer messageCountAtGeneration;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
