// dto/ChatRequest.java（增加 promptId 字段）
package com.example.aichat.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long promptId;  // 可选，指定使用的提示词ID
}
