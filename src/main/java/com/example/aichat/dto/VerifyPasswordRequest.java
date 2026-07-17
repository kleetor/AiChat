package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyPasswordRequest {
    @NotBlank
    private String password;
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
