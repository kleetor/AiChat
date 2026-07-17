package com.example.aichat.config;

import com.example.aichat.config.props.ChromaDbProperties;
import com.example.aichat.config.props.EmbeddingProperties;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ChromaDBConfig {

    private final ChromaDbProperties chromaDbProperties;
    private final EmbeddingProperties embeddingProperties;

    public ChromaDBConfig(ChromaDbProperties chromaDbProperties,
                          EmbeddingProperties embeddingProperties) {
        this.chromaDbProperties = chromaDbProperties;
        this.embeddingProperties = embeddingProperties;
    }

    public String getChromaUrl() { return chromaDbProperties.getUrl(); }
    public String getEmbeddingApiUrl() { return embeddingProperties.getApiUrl(); }
    public String getEmbeddingApiKey() { return embeddingProperties.getApiKey(); }
    public String getEmbeddingModel() { return embeddingProperties.getModel(); }
}
