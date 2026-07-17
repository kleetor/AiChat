package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "tavily.api")
public class TavilyProperties {
    @NotBlank
    private String key;
    @NotBlank
    private String url;
}
