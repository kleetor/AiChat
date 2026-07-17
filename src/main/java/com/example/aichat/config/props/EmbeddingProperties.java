package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {
    @NotBlank
    private String apiUrl;
    @NotBlank
    private String apiKey;
    private String model;
    @Positive
    private int batchSize = 32;
}
