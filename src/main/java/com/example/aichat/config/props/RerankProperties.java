package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rerank")
public class RerankProperties {
    private String apiUrl = "https://api.siliconflow.cn/v1/rerank";
    private String apiKey;
    private String model = "BAAI/bge-reranker-v2-m3";
}
