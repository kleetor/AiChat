package com.example.aichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ChromaDBConfig {

    @Value("${chromadb.url}")
    private String chromaUrl;

    @Value("${embedding.api.url}")
    private String embeddingApiUrl;

    @Value("${embedding.api.key}")
    private String embeddingApiKey;

    @Value("${embedding.model}")
    private String embeddingModel;

    public String getChromaUrl() { return chromaUrl; }
    public String getEmbeddingApiUrl() { return embeddingApiUrl; }
    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public String getEmbeddingModel() { return embeddingModel; }
}
