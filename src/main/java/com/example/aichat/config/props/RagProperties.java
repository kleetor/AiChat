package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Chunk chunk = new Chunk();
    private Retrieve retrieve = new Retrieve();

    @Data
    public static class Chunk {
        private int size = 500;
        private int overlap = 50;
    }

    @Data
    public static class Retrieve {
        private int topK = 5;
    }
}
