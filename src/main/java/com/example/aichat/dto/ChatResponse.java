package com.example.aichat.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatResponse {
    private String reply;
    private Long messageId;
    private LocalDateTime timestamp;
}
