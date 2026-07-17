package com.example.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "消息不能为空")
    @Size(max = 10000, message = "消息长度不能超过 10000 个字符")
    private String message;

    private Long promptId;
    private Long modelConfigId;
    private Boolean webSearchEnabled;
    private String imageDescription; // 图片识别描述，由前端上传图片后填入
    private Long knowledgeBaseId;   // 选中的知识库 ID (null=不使用)
    private Boolean longMemoryEnabled; // 是否启用长期记忆 (默认 true)
}
