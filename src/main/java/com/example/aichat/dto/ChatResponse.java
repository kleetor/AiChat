package com.example.aichat.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChatResponse {
    private String reply;
    private Long inputTokens;
    private Long outputTokens;
    private BigDecimal costAmount;
}
