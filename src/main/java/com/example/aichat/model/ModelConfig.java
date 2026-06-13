package com.example.aichat.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "model_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String apiKey;

    @Column(nullable = false)
    private String apiUrl;

    @Column(nullable = false)
    private String modelName;

    @Column(length = 100)
    private String displayName;

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal inputTokenPrice = new BigDecimal("0.001000");

    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal outputTokenPrice = new BigDecimal("0.002000");
}
