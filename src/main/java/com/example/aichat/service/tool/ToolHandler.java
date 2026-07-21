package com.example.aichat.service.tool;

/**
 * 工具处理器接口。
 * 每个工具实现此接口并通过 {@code @Component} 注解自动注册到 {@link ToolRegistry}。
 */
public interface ToolHandler {

    /** 工具唯一名称，对应 OpenAI tool_calls 中的 function.name */
    String name();

    /** 返回工具定义（供 LLM 识别） */
    ToolDefinition getDefinition();

    /** 执行工具并返回结果 */
    ToolResult execute(ToolCall call);
}
