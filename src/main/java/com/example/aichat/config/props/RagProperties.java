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
        /** 粗排候选数（向量和BM25各召回此数量，经RRF融合后送入Rerank精排） */
        private int candidateSize = 20;
        /** 是否启用查询重写 */
        private boolean queryRewriteEnabled = true;
    }
}
