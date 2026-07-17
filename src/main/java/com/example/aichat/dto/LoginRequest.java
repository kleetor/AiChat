package com.example.aichat.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String email;

    @NotBlank(message = "密码不能为空")
    private String password;

    private String code;

    @AssertTrue(message = "用户名或邮箱至少填写一项")
    public boolean isUsernameOrEmailPresent() {
        return (username != null && !username.isBlank())
                || (email != null && !email.isBlank());
    }
}
