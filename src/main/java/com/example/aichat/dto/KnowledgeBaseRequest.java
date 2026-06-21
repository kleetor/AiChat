package com.example.aichat.dto;

import lombok.Data;

@Data
public class KnowledgeBaseRequest {
    private String name;
    private String description;
    private String visibility;
}
