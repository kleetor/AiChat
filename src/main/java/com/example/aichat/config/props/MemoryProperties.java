package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private Inject inject = new Inject();
    private int searchTopK = 10;
    private LlmConfig llm = new LlmConfig();
    private DecayConfig decay = new DecayConfig();

    @Data
    public static class Inject {
        private int recentCount = 20;
    }

    @Data
    public static class LlmConfig {
        @NotBlank
        private String apiKey;
        private String apiUrl;
        private String modelName;
    }

    @Data
    public static class DecayConfig {
        private int freshDays = 3;
        private int briefDays = 7;
        private int forgetDays = 14;
    }
}
