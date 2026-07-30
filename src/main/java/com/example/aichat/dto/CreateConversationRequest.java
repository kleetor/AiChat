package com.example.aichat.dto;

import lombok.Data;

@Data
public class CreateConversationRequest {
    private String title;
    private Long promptId;
}
