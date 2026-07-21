package com.example.aichat.service.tool;

/**
 * 工具执行结果，将作为 role:tool 消息追加到 messages 数组。
 */
public class ToolResult {

    private final String toolCallId;
    private final String name;
    private final String content;

    public ToolResult(String toolCallId, String name, String content) {
        this.toolCallId = toolCallId;
        this.name = name;
        this.content = content;
    }

    public String getToolCallId() { return toolCallId; }
    public String getName() { return name; }
    public String getContent() { return content; }
}
