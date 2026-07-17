package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;

public class SendResetCodeRequest {
    @NotBlank(message = "请输入用户名或邮箱")
    private String username;
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
