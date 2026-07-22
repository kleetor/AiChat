package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "memory_item_entities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "memory_item_id", nullable = false)
    private Long memoryItemId;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 20)
    private String role;
}
