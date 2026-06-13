package com.example.aichat.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private Long userId;
    private BigDecimal balance;
}
