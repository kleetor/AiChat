package com.example.aichat.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long promptId;
    private Long modelConfigId;
}
