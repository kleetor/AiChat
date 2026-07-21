package com.example.aichat.model;

import com.example.aichat.config.ApiKeyEncryptor;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Convert(converter = ApiKeyEncryptor.class)
    private String apiKey;

    @NotBlank(message = "API URL 不能为空")
    @Column(nullable = false)
    private String apiUrl;

    @NotBlank(message = "模型名称不能为空")
    @Column(nullable = false)
    private String modelName;

    @Column(length = 100)
    private String displayName;

    @NotNull(message = "输入价格不能为空")
    @DecimalMin(value = "0", message = "输入价格不能为负数")
    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal inputTokenPrice = new BigDecimal("0.001000");

    @NotNull(message = "输出价格不能为负数")
    @DecimalMin(value = "0", message = "输出价格不能为负数")
    @Column(nullable = false, precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal outputTokenPrice = new BigDecimal("0.002000");

    @Column(name = "supports_tool_calling")
    @Builder.Default
    private Boolean supportsToolCalling = false;
}
