package com.example.aichat.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatHistoryResponse {
    private List<MessageRecord> messages;

    @Data
    public static class MessageRecord {
        private Long id;
        private String userMessage;
        private String aiReply;
        private LocalDateTime timestamp;
        private String fileUrl;
    }
}
