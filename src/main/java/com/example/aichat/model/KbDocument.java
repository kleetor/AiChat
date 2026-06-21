package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kb_id", nullable = false)
    private Long kbId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 10)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "s3_key", length = 500)
    private String s3Key;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "PROCESSING";

    @Column(name = "chunk_count", nullable = false)
    @Builder.Default
    private Integer chunkCount = 0;

    @Column(name = "error_msg", columnDefinition = "TEXT")
    private String errorMsg;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
