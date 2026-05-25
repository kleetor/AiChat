// model/Prompt.java
package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prompts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prompt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;           // 提示词标题

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;        // 提示词内容（system prompt）
}
