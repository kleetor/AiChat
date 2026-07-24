package com.example.aichat.dto;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(max = 200, message = "签名长度不能超过200个字符")
    private String signature;
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
}
