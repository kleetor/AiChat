package com.example.aichat.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SendCodeRequest {
    @NotBlank @Email @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
