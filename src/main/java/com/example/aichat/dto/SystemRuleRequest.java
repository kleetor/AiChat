package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统规则请求 DTO — 替换裸 Map，提供输入校验
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemRuleRequest {
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 200, message = "规则名称不能超过200个字符")
    private String name;

    @NotBlank(message = "规则内容不能为空")
    private String content;

    private Boolean isActive;

    private Integer sortOrder;
}
