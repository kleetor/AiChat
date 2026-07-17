package com.example.aichat.dto;

import com.example.aichat.model.ModelConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模型配置 DTO — 不包含 apiKey，防止敏感数据泄露
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelConfigDTO {
    private Long id;
    private String apiUrl;
    private String modelName;
    private String displayName;
    private BigDecimal inputTokenPrice;
    private BigDecimal outputTokenPrice;

    public static ModelConfigDTO from(ModelConfig config) {
        return ModelConfigDTO.builder()
                .id(config.getId())
                .apiUrl(config.getApiUrl())
                .modelName(config.getModelName())
                .displayName(config.getDisplayName())
                .inputTokenPrice(config.getInputTokenPrice())
                .outputTokenPrice(config.getOutputTokenPrice())
                .build();
    }
}
