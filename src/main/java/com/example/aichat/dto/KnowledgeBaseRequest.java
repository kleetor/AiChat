package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KnowledgeBaseRequest {
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称不能超过 100 个字符")
    private String name;

    private String description;
    private String visibility;
}
